# API 端点清单

## 认证接口

### POST /api/auth/debug-login
**仅开发模式可用**

**请求体:**
```json
{
  "userId": "user123"
}
```

**响应:**
```json
{
  "success": true,
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "userId": "user123"
  }
}
```

---

## 数据库连接接口

### GET /api/connections
获取当前用户的所有数据库连接

**响应:**
```json
{
  "success": true,
  "data": {
    "connections": [
      {
        "id": 1,
        "name": "生产数据库",
        "dbType": "postgresql",
        "host": "localhost",
        "port": 5432,
        "database": "mydb",
        "isActive": true,
        "createdAt": "2026-06-10T10:00:00Z"
      }
    ]
  }
}
```

### POST /api/connections
添加新的数据库连接

**请求体:**
```json
{
  "name": "测试数据库",
  "dbType": "postgresql",
  "host": "localhost",
  "port": 5432,
  "database": "testdb",
  "username": "postgres",
  "password": "password123"
}
```

**响应:**
```json
{
  "success": true,
  "data": {
    "connectionId": 2
  },
  "message": "连接添加成功"
}
```

### PUT /api/connections/:id
更新数据库连接配置

**请求体:** 同POST /api/connections

### DELETE /api/connections/:id
删除数据库连接

### POST /api/connections/:id/test
测试数据库连接

**响应:**
```json
{
  "success": true,
  "data": {
    "connected": true
  },
  "message": "连接测试成功"
}
```

### POST /api/connections/:id/switch
切换到指定数据库连接

**响应:**
```json
{
  "success": true,
  "data": {
    "schema": {
      "tables": [
        {
          "tableName": "users",
          "rowCount": 150
        }
      ]
    }
  },
  "message": "切换成功"
}
```

---

## SQL执行接口

### POST /api/sql/execute
执行SQL语句

**请求体:**
```json
{
  "connectionId": 1,
  "sqlText": "SELECT * FROM users LIMIT 10",
  "confirmed": false
}
```

**响应（查询语句）:**
```json
{
  "success": true,
  "data": {
    "result": {
      "columns": [
        {"name": "id", "type": "INTEGER"},
        {"name": "name", "type": "VARCHAR"}
      ],
      "rows": [
        [1, "Alice"],
        [2, "Bob"]
      ],
      "total": 150,
      "executionTime": 45,
      "complexTypes": {}
    }
  }
}
```

**响应（危险操作）:**
```json
{
  "success": false,
  "data": {
    "isDangerous": true,
    "warningMessage": "此操作将删除数据，是否确认执行？"
  }
}
```

### POST /api/sql/explain
获取SQL执行计划

**请求体:**
```json
{
  "connectionId": 1,
  "sqlText": "SELECT * FROM users WHERE age > 18"
}
```

**响应:**
```json
{
  "success": true,
  "data": {
    "plan": "Seq Scan on users  (cost=0.00..15.50 rows=100 width=200)"
  }
}
```

---

## AI接口

### GET /api/ai/config
获取AI配置

**响应:**
```json
{
  "success": true,
  "data": {
    "apiProvider": "deepseek",
    "apiBaseUrl": "https://api.deepseek.com/v1",
    "modelName": "deepseek-chat",
    "temperature": 0.7,
    "maxTokens": 2000
  }
}
```

### PUT /api/ai/config
更新AI配置

**请求体:**
```json
{
  "apiProvider": "deepseek",
  "apiKey": "sk-xxxxx",
  "apiBaseUrl": "https://api.deepseek.com/v1",
  "modelName": "deepseek-chat",
  "temperature": 0.7,
  "maxTokens": 2000
}
```

### POST /api/ai/generate-sql
自然语言转SQL

**请求体:**
```json
{
  "connectionId": 1,
  "prompt": "查询所有年龄大于18岁的用户",
  "conversationId": "uuid-1234"
}
```

**响应:**
```json
{
  "success": true,
  "data": {
    "sql": "SELECT * FROM users WHERE age > 18;",
    "conversationId": "uuid-1234"
  }
}
```

### POST /api/ai/parse-text
智能填表（INSERT）

**请求体:**
```json
{
  "connectionId": 1,
  "tableName": "users",
  "textDescription": "姓名：张三，年龄：25岁，邮箱：zhangsan@example.com"
}
```

**响应:**
```json
{
  "success": true,
  "data": {
    "fields": {
      "name": "张三",
      "age": 25,
      "email": "zhangsan@example.com"
    }
  }
}
```

### POST /api/ai/fill-nulls
智能填充NULL值（UPDATE）

**请求体:**
```json
{
  "connectionId": 1,
  "tableName": "users",
  "primaryKey": {"id": 10},
  "existingData": {
    "name": "李四",
    "age": 30
  },
  "nullFields": ["email", "phone"],
  "description": "根据姓名推测，可能是lisi@example.com"
}
```

**响应:**
```json
{
  "success": true,
  "data": {
    "filledFields": {
      "email": "lisi@example.com",
      "phone": null
    }
  }
}
```

### POST /api/ai/parse-markdown
Markdown文档解析

**请求体:** `multipart/form-data`
- `file`: Markdown文件
- `connectionId`: 连接ID
- `tableName`: 目标表名

**响应:**
```json
{
  "success": true,
  "data": {
    "extractedData": [
      {"name": "Alice", "age": 25},
      {"name": "Bob", "age": 30}
    ],
    "rowCount": 2
  }
}
```

---

## Schema接口

### GET /api/schema/tables
获取数据库表列表

**查询参数:**
- `connectionId`: 连接ID

**响应:**
```json
{
  "success": true,
  "data": {
    "tables": [
      {
        "tableName": "users",
        "rowCount": 150
      }
    ]
  }
}
```

### GET /api/schema/table/:tableName
获取表结构

**查询参数:**
- `connectionId`: 连接ID

**响应:**
```json
{
  "success": true,
  "data": {
    "columns": [
      {
        "name": "id",
        "type": "INTEGER",
        "nullable": false,
        "defaultValue": null,
        "isPrimaryKey": true
      }
    ]
  }
}
```

---

## 查询结果接口

### POST /api/results/export
导出查询结果

**请求体:**
```json
{
  "format": "csv",
  "data": {
    "columns": [{"name": "id"}, {"name": "name"}],
    "rows": [[1, "Alice"], [2, "Bob"]]
  }
}
```

**响应:** 文件流（CSV或XLSX）

---

## 历史记录接口

### GET /api/history
获取SQL执行历史

**查询参数:**
- `connectionId`: (可选) 连接ID
- `startDate`: (可选) 开始日期
- `endDate`: (可选) 结束日期
- `status`: (可选) success/error

**响应:**
```json
{
  "success": true,
  "data": {
    "history": [
      {
        "id": 1,
        "sqlText": "SELECT * FROM users",
        "executionTime": 45,
        "rowsAffected": 10,
        "status": "success",
        "executedAt": "2026-06-11T10:00:00Z"
      }
    ]
  }
}
```

---

## SQL片段接口

### GET /api/snippets
获取SQL片段列表

**响应:**
```json
{
  "success": true,
  "data": {
    "snippets": [
      {
        "id": 1,
        "title": "查询用户模板",
        "sqlContent": "SELECT * FROM users WHERE {condition}",
        "description": "通用用户查询模板",
        "tags": "users,query"
      }
    ]
  }
}
```

### POST /api/snippets
保存SQL片段

**请求体:**
```json
{
  "title": "删除过期数据",
  "sqlContent": "DELETE FROM logs WHERE created_at < NOW() - INTERVAL '30 days'",
  "description": "清理30天前的日志",
  "tags": "logs,maintenance"
}
```

### PUT /api/snippets/:id
更新SQL片段

### DELETE /api/snippets/:id
删除SQL片段
