# Task #3: 内置SQLite元数据库初始化

## 实现概述

已完成内置SQLite元数据库的自动初始化功能，包括6张元数据表的DDL脚本和启动时自动执行机制。

## 实现内容

### 1. DDL脚本 (src/main/resources/db/schema.sql)

创建了6张元数据表：

| 表名 | 用途 | 关键字段 |
|------|------|----------|
| `db_connections` | 数据库连接配置 | user_id, db_type, host, port, password_encrypted |
| `ai_configs` | AI配置 | user_id, api_provider, api_key_encrypted, model_name |
| `sql_history` | SQL执行历史 | user_id, connection_id, sql_text, execution_time |
| `sql_snippets` | SQL片段模板 | user_id, title, sql_content, tags |
| `ai_conversations` | AI对话历史 | conversation_id, user_id, message_type, message_content |
| `markdown_uploads` | Markdown上传记录 | user_id, connection_id, file_name, extracted_rows |

**特性：**
- 使用 `CREATE TABLE IF NOT EXISTS` 确保幂等性
- 所有表包含时间戳字段（created_at, updated_at）
- 创建索引优化查询性能
- 外键约束保证数据完整性

### 2. DatabaseInitializer类 (src/main/java/com/dbmanager/config/DatabaseInitializer.java)

**核心功能：**

```java
@Component
public class DatabaseInitializer {
    @PostConstruct
    public void initialize() {
        // 1. 确保 ./data 目录存在
        ensureDataDirectoryExists();
        
        // 2. 执行 schema.sql 脚本
        executeSchemaScript();
    }
}
```

**关键特性：**
- `@PostConstruct` 注解：应用启动后自动执行
- `@Component` 注解：自动注入到Spring容器
- 自动创建 `./data` 目录
- 逐条执行DDL语句
- 完整的日志记录
- 异常处理机制

### 3. 配置 (src/main/resources/application.yml)

```yaml
spring:
  datasource:
    url: jdbc:sqlite:./data/metadata.db
    driver-class-name: org.sqlite.JDBC
    hikari:
      maximum-pool-size: 5
      connection-timeout: 30000
      idle-timeout: 600000
```

## 工作流程

```
应用启动
    ↓
Spring容器初始化
    ↓
DatabaseInitializer.initialize() 执行（@PostConstruct）
    ↓
检查并创建 ./data 目录
    ↓
读取 classpath:db/schema.sql
    ↓
逐条执行DDL语句
    ↓
创建6张表 + 6个索引
    ↓
初始化完成，应用正常启动
```

## 验证方法

### 方式1：运行验证脚本

```bash
cd backend
bash verify-init.sh
```

### 方式2：启动应用验证

```bash
# 首次启动
mvn spring-boot:run

# 检查日志输出
# 应该看到：
# - "开始初始化内置SQLite元数据库..."
# - "创建数据目录: /path/to/data"
# - "数据库表结构初始化完成"
# - "元数据库初始化完成"

# 检查数据库文件
ls -lh ./data/metadata.db
```

### 方式3：使用SQLite客户端验证

```bash
# 使用sqlite3命令行工具
sqlite3 ./data/metadata.db

# 查看所有表
.tables

# 应该输出：
# ai_configs        db_connections    markdown_uploads  sql_history
# ai_conversations  sql_snippets

# 查看表结构
.schema db_connections

# 退出
.quit
```

## 验收标准检查

- [x] 应用首次启动自动创建metadata.db
- [x] 所有6张表正确创建
- [x] 索引正确创建
- [x] 重启应用不会重复创建（使用IF NOT EXISTS）
- [x] 数据库路径：`./data/metadata.db`
- [x] 使用Spring JDBC执行DDL
- [x] @PostConstruct初始化

## 文件清单

```
backend/
├── src/main/resources/
│   ├── db/
│   │   └── schema.sql                         # DDL脚本（新增）
│   └── application.yml                        # 已配置SQLite数据源
├── src/main/java/com/dbmanager/
│   └── config/
│       └── DatabaseInitializer.java           # 初始化器（新增）
└── verify-init.sh                              # 验证脚本（新增）
```

## 后续集成

其他模块可以直接使用这些表：

```java
@Repository
public class ConnectionRepository {
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    public void saveConnection(DbConnection connection) {
        String sql = "INSERT INTO db_connections (...) VALUES (...)";
        jdbcTemplate.update(sql, ...);
    }
}
```

## 注意事项

1. **数据目录权限**：确保应用有权限在运行目录创建 `./data` 文件夹
2. **首次启动**：首次启动会创建数据库文件，后续启动会复用
3. **生产环境**：建议使用绝对路径或环境变量配置数据库位置
4. **备份建议**：定期备份 `./data/metadata.db` 文件

## 与P9规划的对应

- **Task #3.1**：✅ DDL脚本创建（6张表）
- **Task #3.2**：✅ DatabaseInitializer类实现
- **Task #3.3**：✅ @PostConstruct自动初始化
- **Task #3.4**：✅ 数据目录和SQLite连接配置
- **Task #3.5**：✅ 幂等性保证（IF NOT EXISTS）
