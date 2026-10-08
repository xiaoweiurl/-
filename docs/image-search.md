# 以图搜图

上传一张图，在素材库（`images`）和商品库/打样（`goods_library`）里找相似图片，并带出货号、品名、打样员。

默认关闭。关闭时不连接新的向量集合，上传、删除、页面行为与现在一致（入口也不会出现）。

## 这台机器

- 显卡：NVIDIA GeForce RTX 5090，32GB 显存，驱动 610.88，Blackwell，计算能力 12.0（sm_120）。
- 内存：62GB。
- 同一张卡还要跑 Ollama 的 `qwen3.6:35b`。向量服务只占大约 1GB 显存，可以和它共存。显存被占满时把向量服务改到 CPU，不要再往这张卡上加载第二个大模型。

## 模型

这台机器用 **Chinese-CLIP ViT-L/14**（`OFA-Sys/chinese-clip-vit-large-patch14`，768 维，Apache-2.0）。

- 图片和中文文本在同一个向量空间里，余弦相似度可以直接比。除了以图搜图，也可以只输入中文描述。
- ViT-L/14 大约 4 亿参数。GPU 上用 fp16，权重大约 1GB，单张图是几十毫秒量级。32GB 显存里 qwen3.6:35b 已经常驻，这个向量模型仍然放得下。
- 代码和 Java 配置的默认值仍是较小的 ViT-B/16（`OFA-Sys/chinese-clip-vit-base-patch16`，512 维，集合 `image_vectors`）。那是 CPU 回退和没有确认 sm_120 的机器用的。这台 5090 在第一次回填之前改成下面四项，不要改已经建好的 512 维集合：

```text
IMAGE_EMBED_MODEL=OFA-Sys/chinese-clip-vit-large-patch14
IMAGE_EMBED_DIMENSION=768
IMAGE_SEARCH_DIMENSION=768
IMAGE_SEARCH_COLLECTION=image_vectors_vitl
```

- 没有用 jina-clip-v2：它的许可证是 CC-BY-NC，不适合公司正式使用。
- 没有用 SigLIP：英文和图图检索很好，中文对齐不如 Chinese-CLIP。
- 本机的 qwen3.6:35b 继续只做对话和识图描述。它不能产出可比对的图片向量，也不要把它挪来当检索模型。bge-m3 只处理文本。
- 新集合名是 `image_vectors_vitl`。不会读、改、删 `salesperson_docs`、`salesperson_docs_hybrid`、`salesperson_chunks`。集合已存在时不会改字段，维度不同必须换新集合名。Milvus 的字符串上限按字节计算，写入前会按字段上限截断，避免中文标题把整条插入打失败。

## 按顺序在 Windows 上执行

下面假设仓库在 `D:\yingyun`，数据库名以 `application-local.yml` 为准，默认 `image_management`。JDK 17 要在 `PATH` 里（`java -version`）。

### 1. 确认这张 5090

```powershell
nvidia-smi
```

应看到 `NVIDIA GeForce RTX 5090`、驱动 `610.88`、显存约 32GB。驱动比 CUDA 12.8 / 13.0 轮子要求的版本新，不需要再单独装一套 CUDA Toolkit，轮子自带运行库。

5090 是 sm_120。PyTorch 从 **2.7 的 cu128 轮子**起才带这个架构。`cu124`、`cu126` 的架构列表停在 sm_90，装上去会报 `no kernel image is available` 或 `sm_120 is not compatible`。不要装这两档。

2026-09-28 核对过官方索引：`cu128` 的 Windows 轮子到 `2.11.0+cu128`，`cu130` 仍在更新（Windows 轮子到 `2.14.0+cu130`），两者都含 sm_120。这台机器用 **cu130**。下不下来时再改用 cu128，不要改用更旧的索引。

### 2. 安装并启动图片向量服务

`requirements.txt` 里故意没有 torch。后装 requirements 不会把刚装好的 CUDA 轮子换成 PyPI 上没有 sm_120 的包。

```powershell
cd D:\yingyun\image-embed-service
py -3 -m venv .venv
.\.venv\Scripts\activate
pip uninstall -y torch torchvision torchaudio
pip install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cu130
pip install -r requirements.txt
```

cu130 失败时，改用仍包含 sm_120 的 cu128：

```powershell
pip uninstall -y torch torchvision torchaudio
pip install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cu128
```

装完先确认架构，再启动服务。必须看到 `sm_120` 和 `(12, 0)`，版本号里是 `+cu130` 或 `+cu128`：

```powershell
.\.venv\Scripts\python -c "import torch; print(torch.__version__, torch.version.cuda); print(torch.cuda.get_arch_list()); print(torch.cuda.get_device_capability(0)); print(torch.cuda.get_device_name(0))"
```

确认通过后，在同一个窗口指定 ViT-L/14 再启动。这个窗口要一直开着。

```powershell
$env:IMAGE_EMBED_MODEL = "OFA-Sys/chinese-clip-vit-large-patch14"
$env:IMAGE_EMBED_DIMENSION = "768"
$env:IMAGE_EMBED_DEVICE = "auto"
python main.py
```

`python main.py` 默认使用 h11（环境变量 `IMAGE_EMBED_HTTP`，一般不要改）。Java 的 `HttpClient` 会发送 `Upgrade: h2c`。如果改用 `uvicorn main:app` 启动，必须加上 `--http h11`，否则 uvicorn 默认的 httptools 解析器会返回 400、422 或空响应。

第一次会从镜像下载 Chinese-CLIP ViT-L/14。健康检查：

```powershell
curl http://127.0.0.1:8002/health
```

`stub` 必须是 `false`，`device` 应为 `cuda`，`dimension` 应为 `768`，`gpu_name` 里应有 5090。`IMAGE_EMBED_STUB=1` 只给开发冒烟，不能用于正式检索。

显存被 qwen3.6:35b 占满、启动报 CUDA OOM 时，保持上面的模型和维度不变，只把设备改成 CPU。62GB 内存够跑 ViT-L/14，只是单张图会慢到一秒上下：

```powershell
$env:IMAGE_EMBED_DEVICE = "cpu"
python main.py
```

没有 NVIDIA GPU、或驱动起不来时，不要装 cu130。卸掉 CUDA 轮子，改用 CPU 轮子，并退回默认的 ViT-B/16（512 维，集合 `image_vectors`）：

```powershell
pip uninstall -y torch torchvision torchaudio
pip install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cpu
$env:IMAGE_EMBED_DEVICE = "cpu"
$env:IMAGE_EMBED_MODEL = "OFA-Sys/chinese-clip-vit-base-patch16"
$env:IMAGE_EMBED_DIMENSION = "512"
python main.py
```

CPU 回退和 5090 的 ViT-L/14 不能混在同一个 Milvus 集合里。换模型就要换集合名，并重新回填。

### 3. 手动执行 SQL

Flyway 是关的，下面这条不会自动跑。在 IDEA Database 控制台打开并执行：

`backend/src/main/resources/db/migration/V64__image_vector_index.sql`

或：

```powershell
psql -h localhost -U postgres -d image_management -f D:\yingyun\backend\src\main\resources\db\migration\V64__image_vector_index.sql
```

脚本可以重复执行。它只建 `image_vector_index`，用来记回填进度（完成 / 失败 / 跳过）。

### 4. 回填已有图片

另开一个 PowerShell。启动方式和已经跑通的 `scripts/hybrid-backfill.ps1` 一样：`cmd /c` 把 classpath 写到 `backend/cp.txt`（已在 `.gitignore`），再用 `Get-Content -Raw` 拼出以分号分隔的 classpath，然后 `java -cp`。不要用 `mvnw exec:java -Dexec.args`，PowerShell 5.1 会把它拆碎。也不要给 Maven 传 `-Dmdep.pathSeparator=;`，分号会被 cmd 截断。

回填窗口里的维度和集合名必须和向量服务一致。这台 5090 用 ViT-L/14 时先设：

```powershell
$env:IMAGE_SEARCH_DIMENSION = "768"
$env:IMAGE_SEARCH_COLLECTION = "image_vectors_vitl"
```

```powershell
cd D:\yingyun
powershell -ExecutionPolicy Bypass -File .\scripts\image-search-backfill.ps1
```

常用参数：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\image-search-backfill.ps1 --source library
powershell -ExecutionPolicy Bypass -File .\scripts\image-search-backfill.ps1 --source goods
powershell -ExecutionPolicy Bypass -File .\scripts\image-search-backfill.ps1 --only-failed
powershell -ExecutionPolicy Bypass -File .\scripts\image-search-backfill.ps1 --limit 50
powershell -ExecutionPolicy Bypass -File .\scripts\image-search-backfill.ps1 --force
```

- 已成功且 OSS 键没变的会跳过，中断后直接再跑即可。
- 单张失败会写入 `image_vector_index.status=FAILED`，然后继续下一张。
- `--only-failed` 只重试失败项。
- `--force` 全部重算。
- 这一步只在回填进程里打开开关，IDEA 里的服务仍然是关的。

回填要能连上和 IDEA 一样的数据库、OSS（`S3_*` / `application-local.yml`）以及本机 Milvus `localhost:19530`。

### 5. 只读评测

评测窗口同样要带上和第 4 步一样的维度与集合名（`$env:IMAGE_SEARCH_DIMENSION = "768"`、`$env:IMAGE_SEARCH_COLLECTION = "image_vectors_vitl"`）。

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\image-search-eval.ps1 --groups 20 --synthetic 8 --k 10
```

输出 Recall@1、Recall@5、Recall@K，以及检索延迟的平均 / P50 / P95。不写数据库，不写 Milvus。

- 风格探针：同一 `product_id`、同一商品多张图、同一货号的其他图片。
- 合成探针：把原图缩小一半、做中心裁剪，再看原图是否回到 TopK。
- 读图或向量失败的探针不进分母，列在 `skipped` 里。

### 6. 打开开关并重启

IDEA 运行配置里增加环境变量，然后重启后端：

```text
IMAGE_SEARCH_ENABLED=true
IMAGE_EMBED_URL=http://127.0.0.1:8002
IMAGE_SEARCH_DIMENSION=768
IMAGE_SEARCH_COLLECTION=image_vectors_vitl
```

这四项要和已经启动的向量服务一致（模型 `chinese-clip-vit-large-patch14`，维度 768）。只在 IDEA 里改维度、向量服务仍是 512，写入会失败。

Milvus 继续用现有的 `MILVUS_HOST` / `MILVUS_PORT`（默认 `localhost:19530`）。

不要改 `milvus.collection-name`，那是文本库。图片库由 `IMAGE_SEARCH_COLLECTION` 指定。这台机器是 `image_vectors_vitl`。代码默认仍是 512 维的 `image_vectors`，只在没有改上面两项时使用。

重启后：

- `GET /api/image-search/status` 的 `enabled` 为 true。
- 新上传的素材、商品库四张图、二创保存会异步生成向量。向量失败只打日志，上传仍然成功。
- 「以图搜图」出现在两处，用的是同一个组件和同一套接口：设计师素材页顶栏（`/` 的 Header），以及工厂页 `/supply-chain` 顶栏。工厂页里的单据概览和 `mode=factory` 对话都能看到这个按钮。
- 商品库和打样表单 `/sampler` 没有这个按钮。
- 搜到的结果仍会带出关联的历史打样或商品记录，作为参考。接口、回填和评测不变。
- 权限沿用各页面自己的登录规则。能进设计师素材页或工厂页的完整会话可以搜；打样作用域会话进不了这两个页面，也打不开这个接口。

前端：

```powershell
cd D:\yingyun
pnpm build
pnpm start
```

素材库按 `images.company` 隔离，和图片列表一样。商品库/打样不按公司裁剪，和现在的商品库列表一样：登录用户都能看到。打样会话仍然只能进自己的那张表单。

## 回滚

1. IDEA 里去掉 `IMAGE_SEARCH_ENABLED`，或设成 `false`，重启后端。搜索接口返回未开启，新上传不再写向量，页面入口消失。
2. 停掉 `python main.py`。
3. 需要连进度表一起去掉时，手动执行：`DROP TABLE IF EXISTS image_vector_index;`
4. 需要去掉向量时，只删除 Milvus 集合 `image_vectors_vitl`。如果还建过默认的 `image_vectors`，也只删这个 `image_` 集合。不要删除 `salesperson_docs`、`salesperson_docs_hybrid`、`salesperson_chunks`。
5. 前端不需要单独回滚。开关关闭后按钮不会出现。

## 权限

设计师页和工厂页共用同一个接口。能不能搜，跟能不能进当前这个页面一样：完整登录会话可以，打样作用域会话不行。工厂模块没有单独的角色，沿用它现有的登录会话。接口、回填和评测没有改。

| 谁 | 能做什么 |
| --- | --- |
| 未登录 | 不能搜 |
| 打样会话 | 不能进设计师页、工厂页和 `/sampler` 以外的接口，也不能搜 |
| 能进设计师素材页或工厂页的登录用户 | 可搜本公司素材；结果里带出商品库/打样图作参考 |
| 管理员 | 另外可以调用 `POST /api/image-search/eval` |
