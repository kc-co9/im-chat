# 即时通讯应用

基于Vue 3和Vite构建的即时通讯应用前端项目，提供登录、注册和实时聊天功能。

## 技术栈

- Vue 3
- Vite
- Vue Router
- WebSocket (STOMP协议)
- Font Awesome图标库

## 功能特性

- 用户登录和注册
- 实时消息发送和接收
- 聊天列表管理
- 联系人管理
- 动态消息

## 项目结构

```
static/
├── css/             # 样式文件
├── fonts/           # 字体文件
├── img/             # 图片资源
├── src/             # Vue源代码
│   ├── components/  # Vue组件
│   ├── App.vue      # 根组件
│   └── main.js      # 入口文件
├── index.html       # HTML入口
├── package.json     # 项目配置
└── vite.config.js   # Vite配置
```

## 开发和构建

### 安装依赖

```bash
npm install
```

### 开发模式

```bash
npm run dev
```

### 构建生产版本

```bash
npm run build
```

## 注意事项

- 确保后端服务已启动
- WebSocket连接需要正确配置
- 生产环境需要将构建产物部署到静态资源服务器
