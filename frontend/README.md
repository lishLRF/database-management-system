# 数据库管理软件 - 前端项目

## 项目信息

- **框架**: Vue 3.4.31 + TypeScript 5.3.3
- **构建工具**: Vite 5.3.3
- **UI组件库**: Element Plus 2.7.6
- **代码编辑器**: Monaco Editor 0.55.1
- **状态管理**: Pinia 2.1.7
- **HTTP客户端**: Axios 1.7.2
- **路由**: Vue Router 4.x

## 目录结构

```
frontend/
├── src/
│   ├── api/              # API接口封装
│   │   └── request.ts    # Axios封装，带JWT token拦截器
│   ├── components/       # 可复用组件
│   ├── stores/           # Pinia状态管理
│   │   └── connection.ts # 数据库连接状态
│   ├── types/            # TypeScript类型定义
│   │   └── index.ts      # 通用类型接口
│   ├── views/            # 页面组件
│   │   └── Home.vue      # 首页（欢迎页面）
│   ├── router/           # 路由配置
│   │   └── index.ts      # 路由定义
│   ├── styles/           # 全局样式
│   │   └── global.css    # 设计系统配色
│   ├── App.vue           # 根组件
│   └── main.ts           # 应用入口
├── vite.config.ts        # Vite配置（含代理到8080端口）
├── tsconfig.json         # TypeScript配置
└── package.json          # 项目依赖
```

## 已配置功能

### 1. Vite配置
- ✅ 代理 `/api` 到后端 `http://localhost:8080`
- ✅ 路径别名 `@` 指向 `./src`
- ✅ 开发服务器端口 5173

### 2. Axios封装
- ✅ 自动添加JWT token到请求头
- ✅ 请求/响应拦截器
- ✅ 超时设置 30秒

### 3. Pinia Store
- ✅ 数据库连接列表管理
- ✅ 当前连接状态
- ✅ 切换连接方法

### 4. 设计系统
- ✅ Clean Data × Surgical Precision 设计理念
- ✅ 完整配色变量（背景、文本、边框、强调色、阴影）
- ✅ 字体系统（IBM Plex Sans + JetBrains Mono）

## 启动项目

```bash
npm install
npm run dev    # http://localhost:5173
```

## 访问地址

- 前端开发服务器: http://localhost:5173
- API代理: /api -> http://localhost:8080/api
