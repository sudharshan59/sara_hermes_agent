from fastapi import APIRouter, HTTPException
from pydantic import BaseModel
from typing import List, Optional
from app.services.hermes_adapter import hermes_adapter
from app.services.task_manager import task_manager

router = APIRouter(prefix="/api/v1", tags=["Chat"])

class ChatMessageItem(BaseModel):
    role: str
    content: str

class ChatRequest(BaseModel):
    message: str
    client_id: str = "com.sara.hermes"
    history: Optional[List[ChatMessageItem]] = None

class ChatResponse(BaseModel):
    response: str
    task_id: str

@router.post("/chat", response_model=ChatResponse)
async def chat_message(req: ChatRequest):
    if not req.message.strip():
        raise HTTPException(status_code=400, detail="Message cannot be empty")

    history_list = [{"role": h.role, "content": h.content} for h in req.history] if req.history else []
    task = await task_manager.create_task(prompt=req.message, client_id=req.client_id)
    await hermes_adapter.execute_task(task.task_id, req.message, history=history_list)
    updated_task = await task_manager.get_task(task.task_id)

    return ChatResponse(
        response=updated_task.summary or "Request processed.",
        task_id=task.task_id
    )
