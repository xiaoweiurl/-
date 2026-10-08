# 图片向量服务

这台机器是 NVIDIA GeForce RTX 5090（32GB，驱动 610.88，Blackwell sm_120），内存 62GB。同一张卡还跑 Ollama 的 `qwen3.6:35b`。

因此用 **Chinese-CLIP ViT-L/14**（768 维）。fp16 大约 1GB 显存，可以和 qwen3.6:35b 共存。代码默认仍是 ViT-B/16（512 维），留给 CPU 回退。完整步骤见仓库 `docs/image-search.md`。默认只监听 `127.0.0.1:8002`。

## 本机启动（Windows，RTX 5090）

5090 需要 **cu128 或更新** 的 PyTorch 轮子。`cu124` / `cu126` 没有 sm_120，会报 `no kernel image is available`。2026-09-28 核对：用 cu130（Windows 轮子到 `2.14.0+cu130`）。cu130 下不来时改用 cu128（到 `2.11.0+cu128`）。驱动 610.88 已满足这两档轮子，不用另装 CUDA Toolkit。

`requirements.txt` 不含 torch，避免后面的 pip 把 CUDA 轮子换成没有 sm_120 的包。

```powershell
cd image-embed-service
py -3 -m venv .venv
.\.venv\Scripts\activate
pip uninstall -y torch torchvision torchaudio
pip install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cu130
pip install -r requirements.txt
.\.venv\Scripts\python -c "import torch; print(torch.__version__, torch.version.cuda); print(torch.cuda.get_arch_list()); print(torch.cuda.get_device_capability(0)); print(torch.cuda.get_device_name(0))"
```

输出里要有 `sm_120`、`(12, 0)`、`NVIDIA GeForce RTX 5090`，版本号含 `+cu130` 或 `+cu128`。然后：

```powershell
$env:IMAGE_EMBED_MODEL = "OFA-Sys/chinese-clip-vit-large-patch14"
$env:IMAGE_EMBED_DIMENSION = "768"
$env:IMAGE_EMBED_DEVICE = "auto"
python main.py
```

`python main.py` 默认使用 h11（环境变量 `IMAGE_EMBED_HTTP`，一般不要改）。Java 的 `HttpClient` 会发送 `Upgrade: h2c`。如果改用 `uvicorn main:app` 启动，必须加上 `--http h11`，否则 uvicorn 默认的 httptools 解析器会返回 400、422 或空响应。

Java 侧同时设置 `IMAGE_SEARCH_DIMENSION=768` 和 `IMAGE_SEARCH_COLLECTION=image_vectors_vitl`，并且要在第一次回填之前设好。

健康检查：`curl http://127.0.0.1:8002/health`。`device` 应为 `cuda`，`dimension` 应为 `768`，`stub` 必须是 `false`。

## CPU 回退

显存被 qwen3.6:35b 占满时，模型和维度不变，只改设备：

```powershell
$env:IMAGE_EMBED_DEVICE = "cpu"
python main.py
```

机器没有可用 NVIDIA GPU 时，改用 CPU 轮子和默认小模型（集合也要回到 `image_vectors`，维度 512）：

```powershell
pip uninstall -y torch torchvision torchaudio
pip install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cpu
$env:IMAGE_EMBED_DEVICE = "cpu"
$env:IMAGE_EMBED_MODEL = "OFA-Sys/chinese-clip-vit-base-patch16"
$env:IMAGE_EMBED_DIMENSION = "512"
python main.py
```

`IMAGE_EMBED_STUB=1` 不下载模型，只给开发机冒烟，不能用于正式检索。

## 主体裁剪

可选。检测器是 OWLv2 `google/owlv2-base-patch16-ensemble`（Apache-2.0）。`transformers` 4.57 已包含检测类，不用另装检测库。它的图像后处理（`Owlv2ImageProcessor.resize`）会导入 `scipy.ndimage`，所以 `requirements.txt` 里要有 `scipy>=1.11,<2`。本机 Python 3.10 用 `scipy==1.15.3` 可以。提示词 `clothing` / `garment` / `person`。检不出或图太小就用整张图。查询和入库必须用同一种预处理，所以 Java 把裁剪向量写到另一个集合（默认 `image_vectors_vitl_crop`），不改 `image_vectors_vitl`。

`start.ps1` 被 gitignore，并且把环境变量写死在脚本里。请在那份脚本里加上：

```powershell
$env:IMAGE_EMBED_CROP = "1"
$env:HF_ENDPOINT = "https://hf-mirror.com"
# 本机 HTTP 代理（Clash 等）会把 hf-mirror 的下载打断。让镜像域名直连：
$env:NO_PROXY = "hf-mirror.com"
$env:no_proxy = "hf-mirror.com"
```

然后用原来的方式重启。第一次会从 hf-mirror 下载 OWLv2。`curl http://127.0.0.1:8002/health` 里 `crop_enabled` 为 true、`crop_mode` 为 `owlv2`、`crop_detector_ready` 为 true 之后，再跑 Java 的 `--variant crop` 回填。缺 scipy 或检测器加载失败时，启动只打一条错误日志，健康检查里 `crop_detector_ready` 为 false，`crop_detector_error` 是原因，裁剪请求退回整图。没加这个变量时，请求不带 `crop=1`，向量和以前一样是整图。

可选变量：`IMAGE_EMBED_CROP_MODEL`、`IMAGE_EMBED_CROP_THRESHOLD`（0.20）、`IMAGE_EMBED_CROP_PADDING`（0.12）、`IMAGE_EMBED_CROP_MIN_SIDE`（48）。完整的 V65、回填和评测步骤在 `docs/image-search.md`。
