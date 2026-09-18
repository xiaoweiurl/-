# 宝娜斯集团 · 图片 / 知识 / 商品库中台

宝娜斯集团（Bonasoma）内部业务系统，Maven 工程名为 **Image Manager Backend**（包名 `com.imagemanager`）。

本仓库是图库、知识文档、商品库与打样协同的中台，**不是**字节跳动 / 豆包 / 扣子（Coze）/ 火山引擎的智能体模板或对话 Demo。启动脚本里若出现 `COZE_PROJECT_ENV`，只是历史遗留的环境变量名，与产品定位无关。

生产环境常通过 FRP 暴露为 `http://ai.bonasoma.com`（以实际部署为准）。

## 系统做什么

| 模块 | 说明 |
|------|------|
| 图库 | 图片上传、相册分类、网格/预览、收藏、回收站、批量操作；独立编辑页 `/edit/[id]` |
| 知识库 | 文档上传与分类、向量化状态、语义检索（与记忆库表物理隔离） |
| 文档中心 | 主页侧栏入口：PDF / Word / Excel / PPT / 压缩包等 |
| 商品库 | 文件夹式商品（货号 + 品名）；主图 / 侧面 / 细节 / 产品图 |
| 打样表单 | 移动端页 `/sampler/{id}`：钉钉内免登填报，桌面编辑仍走 `/goods-library/{id}` |
| 组织与账号 | 钉钉通讯录同步、按姓名注册、用户管理 |
| 权限 | `user` / `admin` / `superadmin`；打样短会话仅能操作指定商品 |
| ERP 同步 | 管理员从外部 ERP HTTP 接口增量拉取订单 / 工艺 / BOM 等到本地库（见下文） |

侧栏还有数据驾驶舱、AI 对话、AI 生图、供应链单据概览、运维中心等页面，以后端已实现的 Controller 为准。后端不可用时接口返回 **503**，**没有** Mock 登录或假数据兜底。

## 技术栈

- **前端**：Next.js 16（App Router）+ React 19 + TypeScript + shadcn/ui + Tailwind CSS 4
- **前端进程**：自定义 `src/server.ts`，始终以 **production Next** 启动（无 Fast Refresh / HMR / `tsx watch`）
- **后端**：Spring Boot 3.2、Java 17，目录 `backend/`
- **数据库**：PostgreSQL（库名默认 `image_management`）；增量 SQL 在 `backend/src/main/resources/db/migration/`（Flyway 风格文件名，当前 **未** 接入 Flyway 插件，按环境执行）
- **会话**：Redis（`AuthServiceImpl` 读写 Session）
- **对象存储**：阿里云 OSS（S3 兼容）。图片存储优先 S3，凭据缺失或连接失败则降级本地目录；文档/知识库文件走本地存储
- **鉴权**：Java 侧 Session（`X-Session-Id` / Cookie / `Authorization: Bearer`）+ Spring Security；前端 `/api/*` 经 BFF 转发

可选本地模型（Ollama 等）用于对话、向量化、图片 OCR，属于部署能力，不是本产品的品牌。

## 仓库结构（高层）

```
.
├── src/                          # Next.js 前端
│   ├── app/                      # 页面与少量专属 API 路由
│   │   ├── api/[[...path]]/      # 统一 BFF：其余 /api/* → Java :8080/api/*
│   │   ├── sampler/[id]/         # 打样员表单
│   │   ├── goods-library/        # 商品库
│   │   ├── knowledge/            # 知识库
│   │   ├── org/                  # 钉钉组织（管理员）
│   │   └── ...
│   ├── components/               # Sidebar、图库、UI
│   ├── lib/                      # 权限辅助、BFF 代理
│   └── server.ts                 # 自定义 HTTP 入口（PORT 默认 5000）
├── backend/                      # Spring Boot
│   ├── src/main/java/com/imagemanager/
│   └── src/main/resources/application.yml
├── package.json                  # pnpm 脚本
└── README.md
```

新增 Java 接口一般不必再写 Next 代理：浏览器请求同源 `/api/<java路径>` 即可。

## 本地运行

依赖：**Node.js + pnpm ≥ 9**、**JDK 17**、**PostgreSQL**、**Redis**。未启动 Redis 时登录会话无法工作。

### 1. 后端

```bash
cd backend
# 配置 application-local.yml 或环境变量：DATABASE_*、REDIS_*、SEED_* 等
# application-local.yml 已被 .gitignore，不要提交
./mvnw spring-boot:run
```

默认 `http://localhost:8080/api`。Swagger：`http://localhost:8080/api/swagger-ui.html`。前端也可打开 `/api-docs`。

生产打包：`./mvnw package` 后 `java -jar target/*.jar`。

### 2. 前端

自定义服务**不会**热更新。改代码后必须重新构建并手动重启。

```bash
pnpm install
pnpm run build

# Linux / macOS（监听 0.0.0.0:5000，便于本机或 FRP）
NEXT_HOSTNAME=0.0.0.0 PORT=5000 pnpm start

# Windows：pnpm start 与 pnpm start:win 等价
pnpm start
```

`pnpm dev` 与 `pnpm start` 相同：都是 `NODE_ENV=production COZE_PROJECT_ENV=PROD PORT=5000 npx tsx src/server.ts`。没有 `.next` 构建产物会直接退出。

启动日志应出现 `as production` 以及 `HMR / Fast Refresh / tsx watch: OFF`。

| 变量 | 说明 |
|------|------|
| `BACKEND_API_URL` / `NEXT_PUBLIC_BACKEND_API_URL` | BFF 直连 Java，默认 `http://localhost:8080/api` |
| `PORT` | 前端端口，默认 `5000` |
| `NEXT_HOSTNAME` / `HOST` | 传给 Next 的 hostname；不要把 Linux 机器名 `HOSTNAME` 当公网域名 |
| `COOKIE_SECURE` | HTTP 内网不要设为 `true` |
| `COZE_PROJECT_ENV` | 启动脚本固定为 `PROD`，仅作运行环境开关，不是产品名 |

## 关键配置（不要把密钥写进 Git）

后端主配置：`backend/src/main/resources/application.yml`。生产用环境变量注入；本地默认值放 `application-local.yml`。

| 用途 | 变量（节选） |
|------|----------------|
| 数据库 | `DATABASE_URL_JDBC` / `DATABASE_USERNAME` / `DATABASE_PASSWORD` |
| Redis | `REDIS_HOST` `REDIS_PORT` `REDIS_PASSWORD` |
| 前端对外地址 | `FRONTEND_URL`（钉钉跳转基址，默认 `http://localhost:5000`） |
| CORS | `CORS_ALLOWED_ORIGINS`（生产把 `http://ai.bonasoma.com` 配上） |
| OSS | `STORAGE_TYPE`（默认 `s3`）、`S3_ENDPOINT` `S3_REGION` `S3_BUCKET_NAME` `S3_ACCESS_KEY` `S3_SECRET_KEY` |
| 种子账号 | 仅 `local`/`dev`：`SEED_SUPERADMIN_PASSWORD` `SEED_ADMIN_PASSWORD` `SEED_USER_PASSWORD`；未配置则跳过创建；首次登录强制改密 |
| 钉钉 | `DINGTALK_APP_KEY` `DINGTALK_APP_SECRET` `DINGTALK_AGENT_ID` `DINGTALK_CORP_ID` 等 |
| ERP | `ERP_BASE_URL` `ERP_UID` `ERP_PASSWORD` `ERP_CUSTOM_ID` |

OSS：图片走 S3 兼容接口（阿里云需关闭 chunked encoding）。未配齐凭据或探测失败则写入 `./uploads`（可用 `UPLOAD_PATH` 改）。`/uploads/**` **不**匿名公开，同站 `<img>` 带 Cookie 访问。本地文件缺失时会按 key 回源 OSS。

### ERP 同步（管理员）

页面 `/erp-sync`。应用做的是：**按模块增量拉取外部 ERP 接口，仅插入本地尚不存在的记录**（幂等，不覆盖已有行）。默认 `ERP_BASE_URL` 历史上指向义乌网纺 Unitive 风格路径，可用环境变量改掉。

本仓库**不是**工厂 MES，也不描述车间报工/考勤。同步模块以 `ErpSyncController` 与配置为准（订单、内衣/丝袜工艺单、部件、工序、工价、原料 BOM 等）。

## 钉钉与打样

未配置 AppKey/Secret 时应用仍可启动；调用「同步钉钉组织」会返回明确错误，不做 Mock。未配置 `DINGTALK_AGENT_ID` 时商品仍能保存，只是不发工作通知。

**组织同步 + 姓名注册（办公端）**

1. 管理员打开 `/org`，同步部门树与通讯录（`org_departments` / `org_users`）。同步本身不批量开户。
2. 员工在 `/register` 填**姓名**（公司默认宝娜斯集团），精确匹配已同步通讯录。
3. 命中一人则创建本地账号并绑定 `dingtalk_userid`；重名则 409 候选人。
4. 初始密码固定 **123456**，`must_change_password=true`，登录后走 `/settings?tab=security&forceChange=1`。
5. 指派打样员发工作通知前，会按通讯录姓名尝试确保本地账号存在（与第 1 步的「只缓存通讯录」互补）。

**打样工作通知**

商品库填写/变更**打样员**且事务成功后异步发企业内部应用 ActionCard。按姓名解析 userid（通讯录优先，其次已注册用户）。找不到人或同名多人则打日志跳过，不让保存失败。

钉钉**不能**改已发出 ActionCard 正文。表单补全了原卡缺失的货号/品名时，会再发一封「打样信息已更新」（每条指派最多一次）。

**钉钉内打开 `/sampler/{id}`**

- 钉钉 UA：JSAPI 免登（`GET /api/auth/dingtalk/config` + `POST /api/auth/dingtalk`）；可用 HMAC ticket。未注册通讯录成员只拿 **sampler 作用域**短会话（约 12 小时），不能进图库/知识库/组织/ERP/管理端。
- 普通浏览器：跳转 `/login?returnUrl=/sampler/{id}`。
- 开放平台需配置 **H5 可信域名**（如 `ai.bonasoma.com`，不要带路径），改完必须 **发布应用**。发布后请重新指定打样员以发出新通知。

`DINGTALK_WORK_NOTICE_PROTOCOL_LINKS=false` 可强制工作通知按钮用裸 HTTP(S) 链接。

## 角色

| 角色 | 能力（摘要） |
|------|----------------|
| `user` | 使用图库/知识库/商品库等业务页；不能管用户、不能 ERP 同步 |
| `admin` | 管理功能 + ERP 同步；不能重置其他 admin/superadmin 的密码（本人除外） |
| `superadmin` | 最高权限 |
| `sampler`（作用域） | 仅打样表单，不是完整中台账号 |

种子账号用户名：`superadmin` / `admin` / `user`。密码只来自环境变量，文档不写明文。钉钉姓名注册账号的初始密码见上一节。

## 开发约定

- 不要提交密钥、`.env.local`、`application-local.yml`。
- 前端：`pnpm ts-check`、`pnpm lint`。后端：`./mvnw test`。
- 基于 `main` 开短分支；PR 写清改动与验证方式。
- 鉴权、上传、会话相关改动不要引入 Mock 兜底或把 session 放到 URL 查询参数。
