# 数据库管理软件

支持AI辅助的Web端数据库管理工具，提供自然语言转SQL、智能填表、数据解析等功能。

## 核心功能

- **数据库连接管理**: 支持PostgreSQL和SQLite，多连接管理
- **SQL编辑器**: Monaco Editor，语法高亮、智能提示、多标签页
- **AI智能辅助**:
  - 自然语言转SQL（对话式）
  - 智能填表（INSERT新数据）
  - 智能填充NULL值（UPDATE已有数据）
  - Markdown文档解析并提取数据
- **查询结果处理**: 表格展示、复杂类型解析（JSON/YAML/XML）、导出（CSV/Excel）
- **历史和片段**: SQL执行历史记录、常用SQL模板管理

## 技术栈

**后端**
- Java 21
- Spring Boot 3.2.5
- Spring Security (JWT验证)
- Spring JDBC (多数据源)
- SQLite (元数据库)
- HikariCP (连接池)

**前端**
- Vue 3.4.31
- TypeScript 5.3.3
- Vite 5.3.3
- Element Plus 2.7.6
- Monaco Editor 0.55.1
- Pinia 2.1.7

## 快速开始

### 环境要求

- JDK 21+
- Node.js 18+
- Maven 3.8+

### 后端启动

```bash
# 开发模式（支持调试登录）
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 生产模式
mvn clean package
java -jar target/database-management-system.jar --spring.profiles.active=prod
```

### 前端启动

```bash
cd frontend
npm install
npm run dev
```

访问 `http://localhost:5173`

### 开发模式登录

开发模式下，使用简易登录入口：
1. 输入任意userId（如：`user123`）
2. 系统自动生成临时token
3. 开始使用所有功能

## 项目结构

```
database-management-system/
├── backend/                 # 后端代码
│   ├── src/main/java/
│   └── src/main/resources/
├── frontend/                # 前端代码
│   ├── src/
│   └── public/
├── docs/                    # 文档
│   ├── api/                 # API文档
│   ├── development/         # 开发指南
│   └── superpowers/         # 设计和任务文档
├── .gitignore
└── README.md
```

## 文档

- [设计文档](docs/superpowers/specs/2026-06-10-database-management-system-design.md)
- [实施任务清单](docs/superpowers/tasks/implementation-tasks.md)
- [API文档](docs/api/README.md)
- [后端开发指南](docs/development/backend-guide.md)
- [前端开发指南](docs/development/frontend-guide.md)
- [测试指南](docs/development/testing-guide.md)

## 配置说明

### 后端配置

创建 `application-local.yml` 用于本地开发：

```yaml
app:
  encryption:
    secret-key: your-32-char-secret-key-here!
  auth:
    jwt-secret: your-jwt-secret
    debug-mode: true
```

### AI配置

首次使用前需配置AI服务：
1. 进入设置页面
2. 选择AI提供商（DeepSeek/OpenAI/通义千问等）
3. 填写API Key和API地址
4. 测试连接

支持的AI服务商（OpenAI兼容接口）：
- DeepSeek
- OpenAI
- 通义千问
- 其他兼容服务

## 开发指南

### 后端开发

参考 [后端开发指南](docs/development/backend-guide.md)

核心模式：
- 动态数据源管理（ThreadLocal隔离）
- 连接池管理（HikariCP）
- AES-256数据加密
- 危险操作检测

### 前端开发

参考 [前端开发指南](docs/development/frontend-guide.md)

核心组件：
- SQLEditor（Monaco Editor封装）
- ResultTable（虚拟滚动）
- AIAssistant（对话式交互）
- ComplexTypeViewer（JSON/XML/YAML查看器）

### 测试

```bash
# 后端测试
mvn test
mvn test jacoco:report

# 前端测试
npm run test
npm run test:coverage
npm run test:e2e
```

## 安全注意事项

- 生产环境必须使用JWT验证
- 敏感数据（密码、API Key）使用AES-256加密
- 危险操作（DELETE/DROP/TRUNCATE）二次确认
- 使用参数化查询防止SQL注入
- HTTPS传输

## 性能优化

- 连接池复用（HikariCP）
- 大结果集分页处理
- Schema信息缓存（5分钟）
- 空闲连接池自动释放（30分钟）
- 前端虚拟滚动渲染

## 限制

- 单用户最大数据库连接数：10
- 单次查询结果建议：≤10,000行
- Markdown文件上传：≤10MB
- Excel导出：≤1,048,576行
- AI请求超时：30秒

## 路线图

### P0（MVP）- 已规划
- [x] 基础设施建设
- [ ] JWT认证 + 开发调试模式
- [ ] 数据库连接管理
- [ ] SQL手动编辑和执行
- [ ] 基础查询结果展示
- [ ] 危险操作二次确认

### P1（核心价值）- 已规划
- [ ] AI自然语言转SQL
- [ ] AI智能填表（INSERT）
- [ ] AI智能填充NULL（UPDATE）
- [ ] SQL执行历史记录
- [ ] Monaco Editor智能提示

### P2（增强体验）- 已规划
- [ ] Markdown文档解析
- [ ] 复杂类型解析（JSON/YAML/XML）
- [ ] 结果导出（CSV/Excel）
- [ ] SQL片段管理
- [ ] EXPLAIN执行计划分析

### P3（未来扩展）
- [ ] 查询结果可视化（图表）
- [ ] ER图自动生成
- [ ] SQL性能优化建议
- [ ] 多人协作功能
- [ ] 数据迁移工具

## 贡献指南

1. Fork本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 创建Pull Request

## 许可证

MIT License

## 联系方式

- 项目地址: [GitHub Repository]
- 问题反馈: [Issues]
- 文档: [Documentation]
