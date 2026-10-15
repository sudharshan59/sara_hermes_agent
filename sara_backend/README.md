# JARVIS-Hermes Assistant Backend

FastAPI backend service connecting the native Android Siri Assistant (`com.sara.hermes`) with Hermes Agent and AWS EC2, adhering to `JARVIS_Hermes_AWS_Implementation_Plan_Final.pdf`.

---

## Architecture Overview

1. **Android Client (`com.sara.hermes`)**:
   - Dynamic fluid glowing Siri Orb UI (Jetpack Compose)
   - Real-time speech-to-text recognition via Android `SpeechRecognizer`
   - Sends task creation requests to `POST /api/v1/tasks`
   - Streams live status updates over WebSocket (`/api/v1/tasks/{task_id}/stream`)
   - Speaks final response using Android `TextToSpeech`

2. **FastAPI Backend (`sara_backend`)**:
   - Task lifecycle management (`queued` -> `planning` -> `executing` -> `completed`)
   - Async WebSocket broadcaster for instant progress streaming
   - Adapter interface for Hermes Agent Gateway / CLI / NVIDIA NIM
   - Fully safe without exposing secrets or arbitrary shell execution

3. **AWS EC2 Production Deployment**:
   - Systemd managed service (`jarvis-api.service`)
   - Nginx reverse proxy with TLS and WebSocket upgrade headers
   - Dedicated non-root `jarvis` service account

---

## Running Locally on Windows

1. Install dependencies (on drive E:):
   ```bash
   uv pip install -r requirements.txt
   ```

2. Run the server:
   ```bash
   python run_server.py
   ```
   The server will start listening at `http://0.0.0.0:8000`.

3. Connect Android APK:
   - In Android Emulator: Settings URL `http://10.0.2.2:8000`
   - On Physical Phone: Settings URL `http://<YOUR_PC_LAN_IP>:8000`

---

## Deploying to AWS EC2 (Ubuntu 24.04)

1. Copy the `sara_backend` folder to your EC2 instance:
   ```bash
   scp -i /path/to/key.pem -r /e/sara_backend ubuntu@<YOUR_EC2_IP>:/home/ubuntu/
   ```

2. SSH into your instance and run the setup script:
   ```bash
   ssh -i /path/to/key.pem ubuntu@<YOUR_EC2_IP>
   sudo bash /home/ubuntu/sara_backend/deploy/aws_setup.sh
   ```

3. Set your production domain or IP in `/etc/nginx/sites-available/jarvis.conf` and update the Siri Android App URL to `https://<YOUR_DOMAIN>`.
