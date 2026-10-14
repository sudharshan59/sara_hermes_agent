import asyncio
import json
from typing import Optional, Dict, Any
from fastapi import APIRouter, WebSocket, WebSocketDisconnect, HTTPException, BackgroundTasks
from pydantic import BaseModel, Field
from app.services.task_manager import task_manager
from app.services.hermes_adapter import hermes_adapter

router = APIRouter(tags=["Tasks"])

class TaskCreateRequest(BaseModel):
    # Android APK format
    prompt: Optional[str] = None
    client_id: Optional[str] = "com.sara.hermes"
    device_info: Optional[str] = "Android"

    # JARVIS Spec format
    input: Optional[Dict[str, Any]] = None
    mode: Optional[str] = "execute"
    response: Optional[Dict[str, Any]] = None

class TaskCreateResponse(BaseModel):
    task_id: str
    status: str
    summary: Optional[str] = None

@router.post("/api/v1/tasks", response_model=TaskCreateResponse)
async def create_task(req: TaskCreateRequest, background_tasks: BackgroundTasks):
    # Extract prompt from either Android APK or JARVIS format
    actual_prompt = ""
    if req.prompt:
        actual_prompt = req.prompt.strip()
    elif req.input and isinstance(req.input, dict):
        actual_prompt = req.input.get("text", "").strip()

    if not actual_prompt:
        raise HTTPException(status_code=400, detail="Prompt or input.text is required")

    task = await task_manager.create_task(
        prompt=actual_prompt,
        client_id=req.client_id or "com.sara.hermes",
        metadata={"device_info": req.device_info, "mode": req.mode}
    )

    # Dispatch background execution via Hermes
    background_tasks.add_task(hermes_adapter.execute_task, task.task_id, actual_prompt)

    return TaskCreateResponse(
        task_id=task.task_id,
        status=task.status,
        summary=task.summary
    )

@router.get("/api/v1/tasks/{task_id}")
async def get_task_status(task_id: str):
    task = await task_manager.get_task(task_id)
    if not task:
        raise HTTPException(status_code=404, detail="Task not found")
    return {
        "task_id": task.task_id,
        "status": task.status,
        "detail": task.detail,
        "summary": task.summary,
        "created_at": task.created_at,
        "updated_at": task.updated_at
    }

@router.post("/api/v1/tasks/{task_id}/cancel")
async def cancel_task(task_id: str):
    task = await task_manager.get_task(task_id)
    if not task:
        raise HTTPException(status_code=404, detail="Task not found")
    await task_manager.update_status(task_id, "cancelled", "Task cancelled by user")
    return {"task_id": task_id, "status": "cancelled"}

async def handle_task_websocket(websocket: WebSocket, task_id: str):
    await websocket.accept()
    queue = await task_manager.subscribe(task_id)
    if not queue:
        await websocket.send_json({"error": "Task not found", "task_id": task_id})
        await websocket.close()
        return

    try:
        while True:
            event = await queue.get()
            await websocket.send_json(event)
            if event.get("status") in ["completed", "failed", "cancelled"]:
                # Final event sent; graceful close
                await asyncio.sleep(0.5)
                break
    except (WebSocketDisconnect, asyncio.CancelledError):
        pass
    finally:
        await task_manager.unsubscribe(task_id, queue)

# Path 1: Used by Android APK
@router.websocket("/api/v1/tasks/{task_id}/stream")
async def websocket_task_stream_apk(websocket: WebSocket, task_id: str):
    await handle_task_websocket(websocket, task_id)

# Path 2: Used by JARVIS specification
@router.websocket("/ws/v1/tasks/{task_id}")
async def websocket_task_stream_spec(websocket: WebSocket, task_id: str):
    await handle_task_websocket(websocket, task_id)
