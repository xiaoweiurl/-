# 可选。默认 CPU 版 torch。有 NVIDIA GPU 时更建议在本机 venv 里装 CUDA 版 torch 后直接 python main.py。
FROM python:3.11-slim

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir torch --index-url https://download.pytorch.org/whl/cpu \
    && pip install --no-cache-dir -r requirements.txt

COPY main.py .

ENV IMAGE_EMBED_DEVICE=cpu
ENV HF_ENDPOINT=https://hf-mirror.com
EXPOSE 8002

CMD ["python", "main.py"]
