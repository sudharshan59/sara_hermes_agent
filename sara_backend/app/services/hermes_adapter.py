import asyncio
import html
import httpx
import json
import re
import shutil
import subprocess
from datetime import datetime
from typing import Dict, Any, Optional
from app.config import settings
from app.services.task_manager import task_manager

class HermesAdapter:
    def __init__(self):
        self.client = httpx.AsyncClient(timeout=60.0)

    async def execute_task(self, task_id: str, prompt: str, history: Optional[Any] = None):
        """
        Executes the user prompt via Hermes Agent or configured LLM/NIM provider.
        Streams state transitions to task_manager.
        """
        try:
            await task_manager.update_status(
                task_id=task_id,
                status="planning",
                detail="Hermes analyzing task and formulating response plan..."
            )
            await asyncio.sleep(0.3)  # Allow UI to display planning state

            # Check if Hermes Gateway or CLI is configured
            if settings.HERMES_MODE in ["hermes_gateway", "auto"] and await self._is_hermes_gateway_alive():
                result_text = await self._run_via_hermes_gateway(prompt, history)
            elif settings.HERMES_MODE in ["hermes_cli", "auto"] and shutil.which(settings.HERMES_BIN_PATH):
                await task_manager.update_status(
                    task_id=task_id,
                    status="executing",
                    detail="Executing task via Hermes Agent..."
                )
                result_text = await self._run_via_hermes_cli(prompt, history)
            elif settings.NVIDIA_API_KEY:
                await task_manager.update_status(
                    task_id=task_id,
                    status="executing",
                    detail="Generating response with NVIDIA NIM..."
                )
                result_text = await self._run_via_nvidia_nim(prompt, history)
            else:
                # Standalone fallback if neither is currently keyed
                await task_manager.update_status(
                    task_id=task_id,
                    status="executing",
                    detail="Processing request..."
                )
                await asyncio.sleep(0.5)
                result_text = f"I received your request: '{prompt}'. Hermes is ready on the backend."

            # Mark completed
            await task_manager.update_status(
                task_id=task_id,
                status="completed",
                detail="Task completed successfully",
                summary=result_text
            )

        except Exception as e:
            error_msg = f"Error processing task: {str(e)}"
            await task_manager.update_status(
                task_id=task_id,
                status="failed",
                detail=error_msg,
                summary="I encountered an issue completing that request. Please try again."
            )

    async def _is_hermes_gateway_alive(self) -> bool:
        try:
            resp = await self.client.get(f"{settings.HERMES_GATEWAY_URL}/health", timeout=1.5)
            return resp.status_code == 200
        except Exception:
            return False

    async def _run_via_hermes_gateway(self, prompt: str, history: Optional[Any] = None) -> str:
        payload = {"message": prompt}
        if history:
            payload["history"] = history[-5:]
        resp = await self.client.post(
            f"{settings.HERMES_GATEWAY_URL}/api/v1/chat",
            json=payload,
            timeout=settings.MAX_TASK_TIMEOUT_SECONDS
        )
        resp.raise_for_status()
        data = resp.json()
        return data.get("response", data.get("text", str(data)))

    async def _run_via_hermes_cli(self, prompt: str, history: Optional[Any] = None) -> str:
        """Runs hermes in non-interactive one-shot mode."""
        loop = asyncio.get_event_loop()
        full_prompt = prompt
        if history:
            hist_lines = [f"{h.get('role', 'user').capitalize()}: {h.get('content', '')}" for h in history[-5:]]
            hist_str = "\n".join(hist_lines)
            full_prompt = f"Previous conversation history:\n{hist_str}\n\nCurrent User Request: {prompt}"
        def _invoke():
            proc = subprocess.run(
                [settings.HERMES_BIN_PATH, "chat", "-q", full_prompt],
                capture_output=True,
                text=True,
                timeout=settings.MAX_TASK_TIMEOUT_SECONDS
            )
            return proc.stdout.strip() if proc.stdout else proc.stderr.strip()
        return await loop.run_in_executor(None, _invoke)

    async def _fetch_realtime_context(self, query: str) -> str:
        triggers = [
            'news', 'weather', 'latest', 'today', 'who', 'what', 'current',
            'score', 'price', 'time', 'date', 'how', 'when', 'why', 'search',
            'update', 'temperature', 'yesterday', 'tomorrow'
        ]
        if not any(t in query.lower() for t in triggers):
            return ""
        try:
            resp = await self.client.post(
                "https://html.duckduckgo.com/html/",
                data={"q": query},
                headers={"User-Agent": "Mozilla/5.0"},
                timeout=4.0
            )
            if resp.status_code == 200:
                snippets = re.findall(r'<a class=\"result__snippet[^>]*>(.*?)</a>', resp.text, re.DOTALL)
                clean = [html.unescape(re.sub(r'<[^>]+>', '', s).strip()) for s in snippets[:4]]
                valid = [s for s in clean if s]
                if valid:
                    return "\n".join(f"- {s}" for s in valid)
        except Exception:
            pass
        return ""

    async def _run_via_nvidia_nim(self, prompt: str, history: Optional[Any] = None) -> str:
        now_str = datetime.now().strftime("%A, %B %d, %Y, %I:%M %p")
        live_context = await self._fetch_realtime_context(prompt)

        system_content = (
            f"You are Siri, a voice-first assistant powered by Hermes and AWS. "
            f"Current date and time: {now_str}. "
            "Keep responses concise, direct, helpful, and suitable for Text-to-Speech audio."
        )
        if live_context:
            system_content += f"\n\nReal-time web search results for the user's question:\n{live_context}\nUse these real-time search results to answer accurately."

        messages = [{"role": "system", "content": system_content}]
        if history:
            for item in history[-5:]:
                role = "assistant" if item.get("role") in ["assistant", "bot", "sara", "hermes"] else "user"
                c = item.get("content", "").strip()
                if c:
                    messages.append({"role": role, "content": c})
        messages.append({"role": "user", "content": prompt})

        headers = {
            "Authorization": f"Bearer {settings.NVIDIA_API_KEY}",
            "Content-Type": "application/json"
        }
        payload = {
            "model": settings.NVIDIA_DEFAULT_MODEL,
            "messages": messages,
            "temperature": 0.6,
            "max_tokens": 1024
        }
        resp = await self.client.post(
            f"{settings.NVIDIA_BASE_URL}/chat/completions",
            headers=headers,
            json=payload,
            timeout=settings.MAX_TASK_TIMEOUT_SECONDS
        )
        resp.raise_for_status()
        data = resp.json()
        return data["choices"][0]["message"]["content"].strip()

hermes_adapter = HermesAdapter()
