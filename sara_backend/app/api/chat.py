from fastapi import APIRouter, HTTPException
from pydantic import BaseModel
from app.services.hermes_adapter import hermes_adapter
from app.services.task_manager import task_manager

router = APIRouter(prefix="/api/v1", tags=["Chat"])

class ChatRequest(BaseModel):
    message: str
    client_id: str = "com.sara.hermes"

class ChatResponse(BaseModel):
    response: str
    task_id: str

@router.post("/chat", response_model=ChatResponse)
async def chat_message(req: ChatRequest):
    if not req.message.strip():
        raise HTTPException(status_code=400, detail="Message cannot be empty")

    task = await task_manager.create_task(prompt=req.message, client_id=req.client_id)
    await hermes_adapter.execute_task(task.task_id, req.message)
    updated_task = await task_manager.get_task(task.task_id)

    return ChatResponse(
        response=updated_task.summary or "Request processed.",
        task_id=task.task_id
    )
