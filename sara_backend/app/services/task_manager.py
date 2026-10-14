import asyncio
import time
import uuid
from typing import Dict, List, Optional, Any
from dataclasses import dataclass, field

@dataclass
class Task:
    task_id: str
    prompt: str
    client_id: str
    status: str = "queued"
    detail: str = "Task received and queued"
    summary: Optional[str] = None
    created_at: float = field(default_factory=time.time)
    updated_at: float = field(default_factory=time.time)
    completed_at: Optional[float] = None
    metadata: Dict[str, Any] = field(default_factory=dict)

class TaskManager:
    def __init__(self):
        self.tasks: Dict[str, Task] = {}
        self._subscribers: Dict[str, List[asyncio.Queue]] = {}
        self._lock = asyncio.Lock()

    async def create_task(self, prompt: str, client_id: str = "unknown", metadata: Optional[Dict[str, Any]] = None) -> Task:
        task_id = str(uuid.uuid4())
        task = Task(
            task_id=task_id,
            prompt=prompt,
            client_id=client_id,
            metadata=metadata or {}
        )
        async with self._lock:
            self.tasks[task_id] = task
            self._subscribers[task_id] = []
        return task

    async def get_task(self, task_id: str) -> Optional[Task]:
        async with self._lock:
            return self.tasks.get(task_id)

    async def update_status(
        self,
        task_id: str,
        status: str,
        detail: Optional[str] = None,
        summary: Optional[str] = None
    ):
        async with self._lock:
            task = self.tasks.get(task_id)
            if not task:
                return
            task.status = status
            task.updated_at = time.time()
            if detail is not None:
                task.detail = detail
            if summary is not None:
                task.summary = summary
            if status in ["completed", "failed", "cancelled"]:
                task.completed_at = time.time()

            # Notify all subscribed WebSocket queues
            event = {
                "task_id": task_id,
                "status": task.status,
                "detail": task.detail,
                "summary": task.summary,
                "timestamp": task.updated_at
            }
            for queue in self._subscribers.get(task_id, []):
                await queue.put(event)

    async def subscribe(self, task_id: str) -> Optional[asyncio.Queue]:
        async with self._lock:
            if task_id not in self.tasks:
                return None
            queue = asyncio.Queue()
            self._subscribers[task_id].append(queue)
            # Send current state immediately
            task = self.tasks[task_id]
            await queue.put({
                "task_id": task_id,
                "status": task.status,
                "detail": task.detail,
                "summary": task.summary,
                "timestamp": task.updated_at
            })
            return queue

    async def unsubscribe(self, task_id: str, queue: asyncio.Queue):
        async with self._lock:
            if task_id in self._subscribers and queue in self._subscribers[task_id]:
                self._subscribers[task_id].remove(queue)

task_manager = TaskManager()
