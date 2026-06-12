# 数据库管理软件 - 实施任务清单

**来源文档**: [设计文档](../specs/2026-06-10-database-management-system-design.md)  
**创建日期**: 2026-06-10  
**任务总数**: 24

---

## Phase 1: 基础设施 (Infrastructure)

### Task #1: 项目脚手架搭建
- **Type**: AFK
- **Blocked by**: None - 可立即开始
- **Priority**: P0

**What to build:**
搭建前后端项目骨架：
- 后端：Spring Boot 3.2.5 + Java 21 + Maven项目结构
- 前端：Vue 3 + TypeScript + Vite项目结构
- 配置文件：application.yml (dev/prod)
- 依赖引入：Spring JDBC, HikariCP, SQLite, Element Plus, Monaco Editor

**Acceptance criteria:**
- [ ] 后端项目可启动（空端点返回200）
- [ ] 前端项目可启动（显示欢迎页）
- [ ] 构建脚本正常工作（mvn package, npm run build）

---

### Task #2: JWT认证 + 开发调试模式
- **Type**: AFK
- **Blocked by**: #1
- **Priority**: P0

**What to build:**
实现双模式认证系统：
- 生产模式：JWT token验证 + 从token提取userId
- 开发模式：`app.auth.debug-mode=true` + 简易登录页面输入userId

**Acceptance criteria:**
- [ ] 生产模式：携带有效JWT token可访问API
- [ ] 生产模式：无token或无效token返回401
- [ ] 开发模式：输入userId后可访问所有API
- [ ] Spring Security配置正确

---

### Task #3: 内置SQLite元数据库初始化
- **Type**: AFK
- **Blocked by**: #1
- **Priority**: P0

**What to build:**
创建并初始化内置元数据库：
- 路径：`{应用目录}/data/metadata.db`
- 表结构：db_connections, ai_configs, sql_history, sql_snippets, ai_conversations, markdown_uploads
- 启动时自动检查并创建

**Acceptance criteria:**
- [ ] 应用首次启动自动创建metadata.db
- [ ] 所有6张表正确创建
- [ ] 索引正确创建
- [ ] 重启应用不会重复创建

---

## Phase 2: 核心功能 - 数据库连接 (P0)

### Task #4: 添加PostgreSQL连接（端到端）
- **Type**: AFK
- **Blocked by**: #2, #3
- **Priority**: P0

**What to build:**
完整的PostgreSQL连接管理流程：
- 前端：连接表单（名称、host、port、database、username、password）
- 后端：AES-256加密password → 存储到metadata.db → 测试连接 → HikariCP连接池
- 动态数据源：ThreadLocal + userId:connectionId隔离

**Acceptance criteria:**
- [ ] 前端表单验证正确
- [ ] 密码加密存储
- [ ] 测试连接失败时返回清晰错误信息
- [ ] 连接成功后显示在连接列表
- [ ] 连接池正确初始化

---

### Task #5: 添加SQLite业务库连接（端到端）
- **Type**: AFK
- **Blocked by**: #4
- **Priority**: P0

**What to build:**
支持SQLite文件数据库连接：
- 前端：文件路径输入或文件上传
- 后端：验证文件存在/可读 → 创建SQLite连接
- 区分元数据SQLite和用户业务SQLite

**Acceptance criteria:**
- [ ] 支持输入本地文件路径
- [ ] 支持上传SQLite文件（≤50MB）
- [ ] 文件不存在时返回明确错误
- [ ] SQLite连接正确建立

---

### Task #6: 连接列表 + 切换连接
- **Type**: AFK
- **Blocked by**: #4
- **Priority**: P0

**What to build:**
连接管理页面和切换功能：
- 连接卡片网格展示（状态指示、连接信息）
- Header连接选择器
- 切换连接时加载schema（表列表）

**Acceptance criteria:**
- [ ] 连接列表正确展示
- [ ] 在线/离线状态指示
- [ ] 切换连接后schema加载成功
- [ ] 当前连接高亮显示

---

## Phase 3: 核心功能 - SQL执行 (P0)

### Task #7: Monaco Editor集成 + 手动执行SQL
- **Type**: AFK
- **Blocked by**: #6
- **Priority**: P0

**What to build:**
SQL编辑器和执行功能：
- Monaco Editor配置（SQL语法高亮、JetBrains Mono字体）
- 执行按钮：执行全部/选中SQL
- 结果表格：展示columns + rows

**Acceptance criteria:**
- [ ] Monaco Editor正常加载和渲染
- [ ] 语法高亮正确
- [ ] Ctrl+Enter快捷键执行SQL
- [ ] SELECT查询结果正确展示
- [ ] INSERT/UPDATE/DELETE返回影响行数

---

### Task #8: SQL智能提示（schema感知）
- **Type**: AFK
- **Blocked by**: #7
- **Priority**: P1

**What to build:**
基于schema的智能提示：
- SQL关键字补全（SELECT, FROM, WHERE等）
- 表名补全（显示行数）
- 字段名补全（显示类型和约束）

**Acceptance criteria:**
- [ ] 输入表名时显示当前连接的所有表
- [ ] 输入字段名时显示当前表的所有字段
- [ ] SQL关键字补全正常
- [ ] 补全信息包含详细提示（行数、类型）

---

### Task #9: 危险操作检测 + 二次确认
- **Type**: AFK
- **Blocked by**: #7
- **Priority**: P0

**What to build:**
危险SQL操作保护：
- 后端检测：DELETE/DROP/TRUNCATE关键字
- 前端二次确认对话框
- 用户确认后带`confirmed: true`重新请求

**Acceptance criteria:**
- [ ] DELETE语句触发二次确认
- [ ] DROP语句触发二次确认
- [ ] TRUNCATE语句触发二次确认
- [ ] 用户取消时不执行SQL
- [ ] 用户确认后正确执行

---

### Task #10: 查询结果分页
- **Type**: AFK
- **Blocked by**: #7
- **Priority**: P0

**What to build:**
结果集分页功能：
- 后端：LIMIT/OFFSET分页
- 前端：分页控件（10/50/100/500/1000可选）
- 返回总行数

**Acceptance criteria:**
- [ ] 默认每页100行
- [ ] 分页控件正确显示
- [ ] 翻页时正确加载数据
- [ ] 总行数显示正确

---

## Phase 4: AI功能 - 基础 (P1)

### Task #11: AI配置管理
- **Type**: AFK
- **Blocked by**: #3
- **Priority**: P1

**What to build:**
AI配置页面：
- 表单：API提供商、API Key、API地址、模型名称、temperature、max_tokens
- 加密存储API Key
- 测试连接按钮

**Acceptance criteria:**
- [ ] AI配置表单提交成功
- [ ] API Key加密存储
- [ ] 测试连接返回成功/失败状态
- [ ] 配置自动记忆（下次打开自动填充）

---

### Task #12: 自然语言转SQL（基础版）
- **Type**: AFK
- **Blocked by**: #6, #11
- **Priority**: P1

**What to build:**
AI生成SQL功能：
- 输入框：自然语言描述
- 后端：获取schema → 构造Prompt → 调用AI API
- 将生成的SQL显示到Monaco Editor

**Acceptance criteria:**
- [ ] 输入"查询所有用户"生成正确SQL
- [ ] schema context正确传递给AI
- [ ] 生成的SQL可直接执行
- [ ] AI API超时控制（30秒）

---

### Task #13: AI对话历史 + 多轮优化
- **Type**: AFK
- **Blocked by**: #12
- **Priority**: P1

**What to build:**
AI助手侧边栏：
- 对话历史展示（用户消息 + AI回复）
- conversationId管理
- 新建对话按钮
- 应用到编辑器按钮

**Acceptance criteria:**
- [ ] 对话历史正确保存和展示
- [ ] 多轮对话使用同一conversationId
- [ ] 可新建对话（生成新conversationId）
- [ ] 应用SQL到编辑器功能正常

---

## Phase 5: AI功能 - 智能填表 (P1)

### Task #14: 智能填表 - INSERT新数据
- **Type**: AFK
- **Blocked by**: #6, #11
- **Priority**: P1

**What to build:**
智能填表页面：
- 选择目标表
- 文本描述输入区
- AI解析按钮
- 动态生成表单 + AI填充标识
- 生成INSERT语句

**Acceptance criteria:**
- [ ] 选择表后动态生成表单
- [ ] 输入描述后AI正确解析字段值
- [ ] AI填充的字段有标识
- [ ] 生成的INSERT语句正确
- [ ] 可手动修改AI填充的值

---

### Task #15: 智能填充NULL - UPDATE已有数据
- **Type**: AFK
- **Blocked by**: #10, #11
- **Priority**: P1

**What to build:**
NULL值智能填充：
- 结果表格选择有NULL值的行
- 智能填充NULL按钮
- 输入描述 → AI推测NULL字段值
- 预览填充结果（高亮）
- 生成UPDATE语句

**Acceptance criteria:**
- [ ] 可选择有NULL值的记录
- [ ] AI根据已有数据推测NULL值
- [ ] 预览界面高亮显示填充字段
- [ ] 生成的UPDATE语句正确
- [ ] 可手动修改推测值

---

## Phase 6: 增强功能 (P2)

### Task #16: Markdown文档解析
- **Type**: AFK
- **Blocked by**: #6, #11
- **Priority**: P2

**What to build:**
Markdown解析页面：
- 文件上传（≤10MB）
- 选择目标表
- AI提取数据 → 预览表格（可编辑）
- 批量INSERT（事务模式）

**Acceptance criteria:**
- [ ] 支持上传Markdown文件
- [ ] AI正确提取表格/列表数据
- [ ] 预览表格可编辑
- [ ] 批量INSERT使用事务（全部成功或全部回滚）
- [ ] 显示插入成功/失败行数

---

### Task #17: 复杂类型解析（JSON/YAML/XML）
- **Type**: AFK
- **Blocked by**: #7
- **Priority**: P2

**What to build:**
复杂类型字段格式化展示：
- 后端检测JSON/YAML/XML字段
- 前端格式化查看器（语法高亮）
- 复制按钮

**Acceptance criteria:**
- [ ] JSON字段格式化展示
- [ ] XML字段格式化展示
- [ ] YAML字段格式化展示
- [ ] 语法高亮正确
- [ ] 复制功能正常

---

### Task #18: 结果导出（CSV/Excel）
- **Type**: AFK
- **Blocked by**: #10
- **Priority**: P2

**What to build:**
查询结果导出功能：
- 导出CSV按钮（UTF-8 BOM编码）
- 导出Excel按钮（XLSX格式）
- 后端生成文件 → 返回下载

**Acceptance criteria:**
- [ ] CSV导出成功（Excel可正常打开）
- [ ] Excel导出成功（≤1,048,576行）
- [ ] 列宽自动调整
- [ ] 文件名包含时间戳

---

### Task #19: SQL执行历史
- **Type**: AFK
- **Blocked by**: #7
- **Priority**: P2

**What to build:**
执行历史页面：
- 历史记录列表（SQL、耗时、行数、状态、时间）
- 筛选器：日期范围、连接、状态
- 复制SQL按钮
- 重新执行按钮

**Acceptance criteria:**
- [ ] 所有SQL执行自动记录
- [ ] 筛选功能正常
- [ ] 复制SQL到剪贴板
- [ ] 重新执行功能正常
- [ ] 详情展开显示完整SQL和错误信息

---

### Task #20: SQL片段管理
- **Type**: AFK
- **Blocked by**: #7
- **Priority**: P2

**What to build:**
SQL片段管理页面：
- 片段列表（标题、描述、标签）
- 保存当前SQL为片段
- 加载片段到编辑器
- 编辑/删除片段

**Acceptance criteria:**
- [ ] 保存片段功能正常
- [ ] 片段列表正确展示
- [ ] 加载片段到编辑器
- [ ] 支持标签筛选
- [ ] 编辑和删除功能正常

---

### Task #21: EXPLAIN执行计划分析
- **Type**: AFK
- **Blocked by**: #7
- **Priority**: P2

**What to build:**
SQL执行计划分析：
- EXPLAIN按钮
- 执行`EXPLAIN` + 原SQL
- 结果展示区（独立标签页）

**Acceptance criteria:**
- [ ] EXPLAIN按钮触发执行计划查询
- [ ] PostgreSQL执行计划正确展示
- [ ] SQLite执行计划正确展示
- [ ] 执行计划格式化展示

---

## Phase 7: 优化和完善 (P2/P3)

### Task #22: 多标签页管理
- **Type**: AFK
- **Blocked by**: #7
- **Priority**: P2

**What to build:**
Monaco Editor多标签页：
- 新建标签页按钮
- 标签页切换
- 关闭标签页（二次确认未保存）
- 标签页名称编辑

**Acceptance criteria:**
- [ ] 新建标签页功能正常
- [ ] 切换标签页内容正确切换
- [ ] 未保存标签页有*标识
- [ ] 关闭未保存标签页时二次确认

---

### Task #23: 连接池监控 + 空闲超时释放
- **Type**: HITL (需要监控策略决策)
- **Blocked by**: #6
- **Priority**: P2

**What to build:**
连接池生命周期管理：
- 监控连接池状态（活跃连接数、空闲连接数）
- 30分钟未使用自动释放
- 管理员页面查看连接池状态

**Acceptance criteria:**
- [ ] 连接池状态可监控
- [ ] 空闲超过30分钟自动释放
- [ ] 释放时正确清理资源
- [ ] 下次使用时自动重建连接池

**决策点：**
- 超时时间是否需要可配置？
- 是否需要告警机制？
- 是否需要连接池预热？

---

### Task #24: 虚拟滚动 + 大结果集优化
- **Type**: AFK
- **Blocked by**: #10
- **Priority**: P3

**What to build:**
大结果集性能优化：
- 前端虚拟滚动（只渲染可见行）
- 后端流式读取ResultSet
- 支持10,000+行流畅滚动

**Acceptance criteria:**
- [ ] 10,000行数据流畅滚动
- [ ] 内存占用合理
- [ ] 滚动性能≥60fps
- [ ] 后端不会一次性加载全部结果到内存

---

## 依赖关系图

```
Phase 1: 基础设施
#1 (脚手架)
  ├─ #2 (认证)
  └─ #3 (元数据库)

Phase 2: 数据库连接
#2, #3 → #4 (PostgreSQL连接)
  └─ #5 (SQLite连接)
    └─ #6 (连接列表 + 切换)

Phase 3: SQL执行
#6 → #7 (Monaco + 执行)
  ├─ #8 (智能提示)
  ├─ #9 (危险操作)
  └─ #10 (分页)

Phase 4: AI基础
#3 → #11 (AI配置)
#6, #11 → #12 (AI生成SQL)
  └─ #13 (对话历史)

Phase 5: AI智能填表
#6, #11 → #14 (INSERT填表)
#10, #11 → #15 (UPDATE填充NULL)

Phase 6: 增强功能
#6, #11 → #16 (Markdown解析)
#7 → #17 (复杂类型)
#10 → #18 (导出)
#7 → #19 (历史)
#7 → #20 (片段)
#7 → #21 (EXPLAIN)

Phase 7: 优化
#7 → #22 (多标签页)
#6 → #23 (连接池监控) [HITL]
#10 → #24 (虚拟滚动)
```

---

## 开发周期估算

- **Phase 1-2**: 1周（基础设施 + 连接管理）
- **Phase 3**: 1周（SQL执行核心功能）
- **Phase 4-5**: 2周（AI功能）
- **Phase 6**: 1-2周（增强功能）
- **Phase 7**: 1周（优化和测试）

**总计：6-7周**

---

**文档结束**
