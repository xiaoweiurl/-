# CPU 回退镜像，不是 RTX 5090 的 GPU 运行方式。
# 5090（sm_120，驱动 610.88）请在 Windows venv 里装 cu130 轮子，见 README。
# cu124 / cu126 没有 sm_120，不要用。
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
