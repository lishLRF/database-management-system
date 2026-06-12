# API 概览

**项目**: 数据库管理软件  
**版本**: v1.0.0  
**基础路径**: `/api`

## 认证

所有API请求需携带JWT token（开发模式除外）：

```http
Authorization: Bearer <JWT_TOKEN>
```

开发模式下，使用 `/api/auth/debug-login` 获取临时token。

## 响应格式

### 成功响应
```json
{
  "success": true,
  "data": { ... },
  "message": "操作成功"
}
```

### 错误响应
```json
{
  "success": false,
  "error": "错误类型",
  "message": "详细错误信息",
  "timestamp": "2026-06-11T10:30:00Z"
}
```

## API 模块

| 模块 | 路径前缀 | 说明 |
|------|---------|------|
| 认证 | `/api/auth` | JWT认证、调试登录 |
| 数据库连接 | `/api/connections` | 连接管理、切换 |
| SQL执行 | `/api/sql` | SQL执行、EXPLAIN |
| AI辅助 | `/api/ai` | AI生成SQL、智能填表 |
| Schema | `/api/schema` | 数据库元数据查询 |
| 查询结果 | `/api/results` | 导出CSV/Excel |
| 历史记录 | `/api/history` | SQL执行历史 |
| SQL片段 | `/api/snippets` | 常用SQL模板 |

## 详细接口

详见 [endpoints.md](./endpoints.md)

## 错误码

| 错误码 | 说明 |
|-------|------|
| 401 | 未认证或token无效 |
| 403 | 权限不足 |
| 404 | 资源不存在 |
| 400 | 请求参数错误 |
| 500 | 服务器内部错误 |
| 1001 | 数据库连接失败 |
| 1002 | SQL执行错误 |
| 1003 | AI调用失败 |

## 限流规则

- 每用户每分钟最多60次请求
- AI接口每分钟最多10次请求
