import swaggerJsdoc from 'swagger-jsdoc';

const options: swaggerJsdoc.Options = {
  definition: {
    openapi: '3.0.0',
    info: {
      title: '数字知识库 API',
      version: '1.0.0',
      description: '数字知识库管理系统的RESTful API文档',
      contact: {
        name: 'Digital Knowledge Base Team',
      },
    },
    servers: [
      {
        url: '/api',
        description: '当前服务器',
      },
    ],
    tags: [
      { name: '认证', description: '用户认证相关接口' },
      { name: '图片管理', description: '图片（知识）管理接口' },
      { name: '相册管理', description: '相册（分类）管理接口' },
      { name: '用户管理', description: '用户信息管理接口' },
      { name: '管理员 - 用户管理', description: '管理员用户管理接口' },
      { name: 'AI 识别', description: 'AI智能识别接口' },
      { name: '通知', description: '通知相关接口' },
      { name: '商品管理', description: '商品管理接口' },
      { name: '系统设置', description: '系统设置接口' },
    ],
    components: {
      securitySchemes: {
        cookieAuth: {
          type: 'apiKey',
          in: 'cookie',
          name: 'session_id',
          description: 'Session认证',
        },
      },
      schemas: {
        // 通用响应
        ApiResponse: {
          type: 'object',
          properties: {
            success: { type: 'boolean', description: '请求是否成功' },
            error: { type: 'string', description: '错误信息' },
            data: { type: 'object', description: '响应数据' },
          },
        },
        // 用户信息
        User: {
          type: 'object',
          properties: {
            id: { type: 'string', description: '用户ID' },
            username: { type: 'string', description: '用户名' },
            email: { type: 'string', description: '邮箱' },
            nickname: { type: 'string', description: '昵称' },
            phone: { type: 'string', description: '电话' },
            bio: { type: 'string', description: '个人简介' },
            role: { type: 'string', enum: ['admin', 'user'], description: '用户角色' },
            membership: { type: 'string', enum: ['free', 'premium'], description: '会员等级' },
            avatar: { type: 'string', description: '头像URL' },
            createdAt: { type: 'string', format: 'date-time', description: '创建时间' },
            lastLoginAt: { type: 'string', format: 'date-time', description: '最后登录时间' },
          },
        },
        // 图片/知识
        Image: {
          type: 'object',
          properties: {
            id: { type: 'integer', description: '知识ID' },
            url: { type: 'string', description: '知识URL' },
            thumbnailUrl: { type: 'string', description: '缩略图URL' },
            title: { type: 'string', description: '标题' },
            description: { type: 'string', description: '描述' },
            size: { type: 'integer', description: '文件大小（字节）' },
            width: { type: 'integer', description: '图片宽度' },
            height: { type: 'integer', description: '图片高度' },
            format: { type: 'string', description: '文件格式' },
            date: { type: 'string', format: 'date-time', description: '上传日期' },
            favorite: { type: 'boolean', description: '是否收藏' },
            tags: { type: 'array', items: { type: 'string' }, description: '标签列表' },
            category: { type: 'string', description: '分类' },
            albumId: { type: 'integer', description: '所属相册ID' },
            albumName: { type: 'string', description: '所属相册名称' },
            fileType: { type: 'string', description: '文件类型' },
            productId: { type: 'integer', description: '关联商品ID' },
            isMainImage: { type: 'boolean', description: '是否为主图' },
            deleted: { type: 'boolean', description: '是否已删除' },
            deletedAt: { type: 'string', format: 'date-time', description: '删除时间' },
          },
        },
        // 商品图片
        ProductImage: {
          type: 'object',
          properties: {
            id: { type: 'integer', description: '图片ID' },
            url: { type: 'string', description: '图片URL' },
            thumbnailUrl: { type: 'string', description: '缩略图URL' },
            isMainImage: { type: 'boolean', description: '是否为主图' },
            productId: { type: 'integer', description: '商品ID' },
          },
        },
        // 商品
        Product: {
          type: 'object',
          properties: {
            id: { type: 'integer', description: '商品ID' },
            name: { type: 'string', description: '商品名称' },
            category: { type: 'string', description: '分类' },
            mainImageUrl: { type: 'string', description: '主图URL' },
            detailImageUrls: { type: 'array', items: { type: 'string' }, description: '详情图URL列表' },
            images: { type: 'array', items: { $ref: '#/components/schemas/ProductImage' }, description: '所有图片' },
            description: { type: 'string', description: '商品描述' },
            matchingKeywords: { type: 'array', items: { type: 'string' }, description: '匹配关键词' },
          },
        },
        // 相册/分类
        Album: {
          type: 'object',
          properties: {
            id: { type: 'integer', description: '分类ID' },
            name: { type: 'string', description: '分类名称' },
            description: { type: 'string', description: '分类描述' },
            imageCount: { type: 'integer', description: '知识数量' },
            coverUrl: { type: 'string', description: '封面URL' },
            matchingMode: { type: 'string', enum: ['contains', 'exact', 'startsWith', 'endsWith', 'regex', 'fuzzy'], description: '匹配模式' },
            matchingConfig: { type: 'object', description: '匹配配置JSON' },
            createdAt: { type: 'string', format: 'date-time', description: '创建时间' },
            updatedAt: { type: 'string', format: 'date-time', description: '更新时间' },
          },
        },
        // 通知
        Notification: {
          type: 'object',
          properties: {
            id: { type: 'string', description: '通知ID' },
            title: { type: 'string', description: '通知标题' },
            message: { type: 'string', description: '通知内容' },
            type: { type: 'string', enum: ['info', 'success', 'warning', 'error', 'upload', 'system'], description: '通知类型' },
            read: { type: 'boolean', description: '是否已读' },
            createdAt: { type: 'string', format: 'date-time', description: '创建时间' },
          },
        },
        // 用户设置
        UserSettings: {
          type: 'object',
          properties: {
            theme: { type: 'string', enum: ['light', 'dark', 'system'], description: '主题' },
            language: { type: 'string', description: '语言' },
            pageSize: { type: 'integer', description: '每页显示数量' },
            defaultSort: { type: 'string', description: '默认排序字段' },
            aiRecognitionEnabled: { type: 'boolean', description: 'AI识别开关' },
            emailNotifications: { type: 'boolean', description: '邮件通知' },
            systemNotifications: { type: 'boolean', description: '系统通知' },
            uploadNotifications: { type: 'boolean', description: '上传通知' },
            autoPlayVideos: { type: 'boolean', description: '自动播放视频' },
            highQualityPreviews: { type: 'boolean', description: '高质量预览' },
            compactMode: { type: 'boolean', description: '紧凑模式' },
            showFileInfo: { type: 'boolean', description: '显示文件信息' },
            defaultView: { type: 'string', enum: ['grid', 'list'], description: '默认视图' },
          },
        },
        // 密码修改请求
        PasswordChangeRequest: {
          type: 'object',
          required: ['currentPassword', 'newPassword', 'confirmPassword'],
          properties: {
            currentPassword: { type: 'string', description: '当前密码' },
            newPassword: { type: 'string', minLength: 6, description: '新密码（至少6位）' },
            confirmPassword: { type: 'string', description: '确认新密码' },
          },
        },
        // 错误响应
        ErrorResponse: {
          type: 'object',
          properties: {
            success: { type: 'boolean', example: false },
            error: { type: 'string', example: '错误信息' },
            message: { type: 'string', example: '错误消息' },
          },
        },
      },
    },
  },
  apis: [
    './src/app/api/**/*.ts',
  ],
};

export const swaggerSpec = swaggerJsdoc(options);
