#!/usr/bin/env bash
# AWS EC2 Ubuntu 24.04 Setup Script for JARVIS-Hermes Backend
set -e

echo "=== Step 1: Updating system packages ==="
sudo apt update && sudo apt upgrade -y
sudo apt install -y python3 python3-venv python3-pip git curl ca-certificates nginx

echo "=== Step 2: Creating service user and directory structure ===
id -u jarvis &>/dev/null || sudo adduser --system --group --home /opt/jarvis --shell /usr/sbin/nologin jarvis
sudo install -d -o jarvis -g jarvis /opt/jarvis/app /opt/jarvis/logs

echo "=== Step 3: Setting up Python virtual environment ===
sudo -u jarvis -H python3 -m venv /opt/jarvis/app/.venv
sudo -u jarvis -H /opt/jarvis/app/.venv/bin/pip install --upgrade pip

# Copy code to /opt/jarvis/app
echo "=== Step 4: Installing dependencies ===
if [ -f "/opt/jarvis/app/requirements.txt" ]; then
    sudo -u jarvis -H /opt/jarvis/app/.venv/bin/pip install -r /opt/jarvis/app/requirements.txt
fi

echo "=== Step 5: Configuring Systemd Service ===
sudo cp /opt/jarvis/app/deploy/systemd/jarvis-api.service /etc/systemd/system/jarvis-api.service
sudo systemctl daemon-reload
sudo systemctl enable jarvis-api
sudo systemctl restart jarvis-api

echo "=== Step 6: Configuring Nginx Reverse Proxy ===
sudo cp /opt/jarvis/app/deploy/nginx/jarvis.conf /etc/nginx/sites-available/jarvis.conf
sudo ln -sf /etc/nginx/sites-available/jarvis.conf /etc/nginx/sites-enabled/jarvis.conf
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t
sudo systemctl reload nginx

echo "=== Setup Complete! JARVIS-Hermes backend is running ==="
