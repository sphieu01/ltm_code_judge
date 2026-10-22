#!/bin/bash
# ============================================================
# build-image.sh — Build Docker Image ltm-judge-runner (Linux/macOS)
# ============================================================

echo "Building Docker Image: ltm-judge-runner:latest..."
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
cd "$DIR"

docker build -t ltm-judge-runner:latest .

if [ $? -eq 0 ]; then
    echo "[SUCCESS] Docker image ltm-judge-runner:latest built successfully!"
else
    echo "[ERROR] Docker build failed. Please make sure Docker daemon is running."
fi
