# 图片管理系统 - Java 后端

## 项目简介

这是一个基于 Spring Boot 3.2 开发的图片管理系统后端服务，提供图片管理、相册管理、用户管理等 RESTful API 接口。

## 技术栈

- **框架**: Spring Boot 3.2.0
- **语言**: Java 17
- **构建工具**: Maven
- **API 文档**: SpringDoc OpenAPI 3
- **工具库**: Lombok

## 项目结构

```
backend/
├── src/main/java/com/imagemanager/
│   ├── ImageManagerApplication.java    # 主应用
│   ├── config/                         # 配置类
│   │   ├── CorsConfig.java            # CORS 跨域配置
│   │   └── SwaggerConfig.java         # API 文档配置
│   ├── controller/                     # 控制器层
│   │   ├── ImageController.java       # 图片接口
│   │   ├── AlbumController.java       # 相册接口
│   │   └── UserController.java        # 用户接口
│   ├── dto/                            # 数据传输对象
│   │   ├── ApiResponse.java           # 统一响应
│   │   ├── PageResponse.java          # 分页响应
│   │   ├── ImageQueryRequest.java     # 图片查询请求
│   │   └── BatchOperationRequest.java # 批量操作请求
│   ├── entity/                         # 实体类
│   │   ├── Image.java                 # 图片实体
│   │   ├── Album.java                 # 相册实体
│   │   ├── User.java                  # 用户实体
│   │   └── Notification.java          # 通知实体
│   ├── service/                        # 服务接口
│   │   ├── ImageService.java
│   │   ├── AlbumService.java
│   │   └── UserService.java
│   ├── service/impl/                   # 服务实现
│   │   ├── ImageServiceImpl.java
│   │   ├── AlbumServiceImpl.java
│   │   └── UserServiceImpl.java
│   └── exception/                      # 异常处理
│       └── GlobalExceptionHandler.java
├── src/main/resources/
│   └── application.yml                 # 应用配置
└── pom.xml                             # Maven 配置
```

## API 接口文档

### 基础路径
- **API 前缀**: `http://localhost:8080/api`
- **Swagger 文档**: `http://localhost:8080/api/swagger-ui.html`

### 图片接口 `/images`

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | `/images` | 查询图片列表（支持搜索、筛选、排序、分页） |
| GET | `/images/{id}` | 获取图片详情 |
| POST | `/images/upload` | 上传图片 |
| PUT | `/images/{id}` | 更新图片信息 |
| DELETE | `/images/{id}` | 删除图片（移至回收站） |
| DELETE | `/images/{id}/permanent` | 永久删除图片 |
| POST | `/images/{id}/restore` | 恢复图片 |
| POST | `/images/{id}/favorite` | 切换收藏状态 |
| POST | `/images/batch` | 批量操作 |
| GET | `/images/favorites` | 获取收藏图片 |
| GET | `/images/trash` | 获取回收站图片 |
| DELETE | `/images/trash` | 清空回收站 |

### 相册接口 `/albums`

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | `/albums` | 获取所有相册 |
| GET | `/albums/{id}` | 获取相册详情 |
| POST | `/albums` | 创建相册 |
| PUT | `/albums/{id}` | 更新相册 |
| DELETE | `/albums/{id}` | 删除相册 |

### 用户接口 `/user`

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | `/user` | 获取用户信息 |
| GET | `/user/stats` | 获取统计信息 |
| GET | `/user/notifications` | 获取通知列表 |
| GET | `/user/notifications/unread-count` | 获取未读数量 |
| POST | `/user/notifications/{id}/read` | 标记通知已读 |

## 构建与运行

### 前置要求
- JDK 17+
- Maven 3.6+

### 编译项目
```bash
cd backend
mvn clean package
```

### 运行项目
```bash
# 方式1: 使用 Maven
mvn spring-boot:run

# 方式2: 使用 JAR 包
java -jar target/image-manager-backend-1.0.0.jar
```

### 访问地址
- **应用地址**: http://localhost:8080/api
- **Swagger 文档**: http://localhost:8080/api/swagger-ui.html
- **OpenAPI JSON**: http://localhost:8080/api/v3/api-docs

## 配置说明

### application.yml

```yaml
server:
  port: 8080                    # 服务端口
  servlet:
    context-path: /api          # API 上下文路径

spring:
  servlet:
    multipart:
      max-file-size: 50MB       # 单文件最大大小
      max-request-size: 100MB   # 请求最大大小

app:
  upload:
    path: ./uploads             # 上传文件保存路径
    allowed-types: jpg,jpeg,png,gif,webp,bmp
    max-size: 52428800          # 文件大小限制（字节）
```

## 数据存储

当前版本使用**内存存储**模拟数据库，数据在应用重启后会丢失。

### 未来集成数据库
- 支持 MySQL、PostgreSQL 等关系型数据库
- 可集成 MongoDB 存储图片元数据
- 可对接对象存储服务（OSS、S3 等）

## 开发指南

### 添加新接口
1. 在 `entity` 包创建实体类
2. 在 `service` 包定义服务接口
3. 在 `service.impl` 包实现服务逻辑
4. 在 `controller` 包创建控制器

### 代码规范
- 使用 Lombok 简化代码
- 遵循 RESTful API 设计规范
- 统一使用 `ApiResponse` 返回结果
- 使用 `@Operation` 注解编写 API 文档

## 注意事项

1. **生产环境部署**：
   - 修改默认端口和上下文路径
   - 配置真实的数据库连接
   - 启用 HTTPS
   - 添加用户认证与授权

2. **文件上传**：
   - 配置合适的文件大小限制
   - 使用对象存储服务保存文件
   - 添加文件类型校验

3. **安全性**：
   - 添加 JWT 认证
   - 防止 SQL 注入
   - 防止 XSS 攻击
   - 限制 CORS 允许的域名

## 许可证

MIT License
