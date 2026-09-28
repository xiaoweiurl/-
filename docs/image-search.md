# 以图搜图

上传一张图，在素材库（`images`）和商品库/打样（`goods_library`）里找相似图片，并带出货号、品名、打样员。

默认关闭。关闭时不连接新的向量集合，上传、删除、页面行为与现在一致（入口也不会出现）。

## 模型

用 **Chinese-CLIP ViT-B/16**（`OFA-Sys/chinese-clip-vit-base-patch16`，512 维，Apache-2.0）。

- 图片和中文文本在同一个向量空间里，余弦相似度可以直接比。除了以图搜图，也可以只输入中文描述。
- ViT-B/16 大约 1.8 亿参数，没有 GPU 时 CPU 可以跑完一张图（大约几百毫秒到两秒）。有 NVIDIA GPU 时自动用 CUDA。
- 没有用 jina-clip-v2：它的许可证是 CC-BY-NC，不适合公司正式使用。
- 没有用 SigLIP：英文和图图检索很好，中文对齐不如 Chinese-CLIP。
- 本机的 qwen3.6:35b 只能写描述，不能产出可比对的图片向量。bge-m3 只处理文本。
- 新集合名是 `image_vectors`。不会读、改、删 `salesperson_docs`、`salesperson_docs_hybrid`、`salesperson_chunks`。集合已存在时不会改字段。Milvus 的字符串上限按字节计算，写入前会按字段上限截断，避免中文标题把整条插入打失败。

有 GPU、想换更大的模型时，同时改这三项，并换一个新集合名（旧集合维度不能混用）：

- `IMAGE_EMBED_MODEL=OFA-Sys/chinese-clip-vit-large-patch14`
- `IMAGE_EMBED_DIMENSION=768`
- `IMAGE_SEARCH_DIMENSION=768`
- `IMAGE_SEARCH_COLLECTION=image_vectors_vitl`

## 按顺序在 Windows 上执行

下面假设仓库在 `D:\yingyun`，数据库名以 `application-local.yml` 为准，默认 `image_management`。JDK 17 要在 `PATH` 里（`java -version`）。

### 1. 看有没有 GPU

```powershell
nvidia-smi
```

有正常输出就是有 NVIDIA GPU。没有这条命令，或报驱动错误，就走 CPU。

装好向量服务后再确认一次：

```powershell
.\.venv\Scripts\python -c "import torch; print('cuda', torch.cuda.is_available()); print(torch.cuda.get_device_name(0) if torch.cuda.is_available() else 'cpu-only')"
```

服务起来后看 `http://127.0.0.1:8002/health` 里的 `device`：`cuda` 或 `cpu`。`IMAGE_EMBED_DEVICE` 默认 `auto`，有 CUDA 用 GPU，否则 CPU。强制 CPU：

```powershell
$env:IMAGE_EMBED_DEVICE = "cpu"
```

### 2. 安装并启动图片向量服务

```powershell
cd D:\yingyun\image-embed-service
py -3 -m venv .venv
.\.venv\Scripts\activate
pip install torch --index-url https://download.pytorch.org/whl/cpu
# 上一步确认有 GPU 时，改成对应 CUDA 轮子，例如：
# pip install torch --index-url https://download.pytorch.org/whl/cu124
pip install -r requirements.txt
python main.py
```

第一次会从镜像下载 Chinese-CLIP（约几百 MB）。这个窗口要一直开着。健康检查：

```powershell
curl http://127.0.0.1:8002/health
```

`stub` 必须是 `false`。`IMAGE_EMBED_STUB=1` 只给开发冒烟，不能用于正式检索。

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
```

Milvus 继续用现有的 `MILVUS_HOST` / `MILVUS_PORT`（默认 `localhost:19530`）。

不要改 `milvus.collection-name`，那是文本库。图片库由 `IMAGE_SEARCH_COLLECTION` 指定，默认 `image_vectors`。

重启后：

- `GET /api/image-search/status` 的 `enabled` 为 true。
- 新上传的素材、商品库四张图、二创保存会异步生成向量。向量失败只打日志，上传仍然成功。
- 设计师首页、商品库、供应链页出现「以图搜图」。打样表单会话打不开这个接口。

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
4. 需要去掉向量时，只删除 Milvus 集合 `image_vectors`（若改过名，删你配置的那个 `image_` 集合）。不要删除 `salesperson_docs`、`salesperson_docs_hybrid`、`salesperson_chunks`。
5. 前端不需要单独回滚。开关关闭后按钮不会出现。

## 权限

| 谁 | 能做什么 |
| --- | --- |
| 未登录 | 不能搜 |
| 打样会话 | 不能搜，也不能看别人的素材或商品 |
| 普通登录用户 | 可搜本公司素材 + 全部商品库/打样图 |
| 管理员 | 另外可以调用 `POST /api/image-search/eval` |
