# 图片向量服务

Chinese-CLIP ViT-B/16。图和中文文本共用 512 维向量，供 Java 以图搜图调用。默认只监听 `127.0.0.1:8002`。

完整的安装、GPU 检测、回填和回滚命令见仓库 `docs/image-search.md`。

## 本机快速启动（Windows）

```powershell
cd image-embed-service
py -3 -m venv .venv
.\.venv\Scripts\activate
# 先看有没有 NVIDIA GPU：nvidia-smi
# 没有 GPU，或 nvidia-smi 失败：
pip install torch --index-url https://download.pytorch.org/whl/cpu
# 有 GPU（驱动正常）再改用对应 CUDA 轮子，例如：
# pip install torch --index-url https://download.pytorch.org/whl/cu124
pip install -r requirements.txt
python main.py
```

健康检查：`curl http://127.0.0.1:8002/health`

`device` 为 `cpu` 或 `cuda`。`IMAGE_EMBED_DEVICE=auto`（默认）会在有 CUDA 时用 GPU，否则用 CPU。强制 CPU：`$env:IMAGE_EMBED_DEVICE="cpu"`。

`IMAGE_EMBED_STUB=1` 不下载模型，只给开发机冒烟，不能用于正式检索。
