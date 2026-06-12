# Database Management System - Backend

## 项目说明
AI驱动的数据库管理系统后端服务，基于 Spring Boot 3.2.5 + Java 21 构建。

## 技术栈
- **Java**: 21
- **Spring Boot**: 3.2.5
- **Spring JDBC**: 动态多数据源管理
- **Spring Security**: JWT认证
- **HikariCP**: 数据库连接池
- **SQLite**: 内置元数据库
- **PostgreSQL**: 支持PostgreSQL数据源
- **jjwt**: JWT令牌处理

## 项目结构
```
backend/
├── src/
│   ├── main/
│   │   ├── java/com/dbmanager/
│   │   │   ├── DatabaseManagementApplication.java  # 主启动类
│   │   │   ├── config/                              # 配置类
│   │   │   │   └── SecurityConfig.java              # Spring Security配置
│   │   │   ├── controller/                          # 控制器层
│   │   │   │   └── HealthController.java            # 健康检查接口
│   │   │   ├── service/                             # 业务逻辑层
│   │   │   │   ├── ConnectionService.java           # 连接管理服务
│   │   │   │   ├── SqlExecutionService.java         # SQL执行服务
│   │   │   │   └── AiService.java                   # AI辅助服务
│   │   │   ├── repository/                          # 数据访问层
│   │   │   │   └── ConnectionRepository.java        # 连接配置仓库
│   │   │   └── entity/                              # 实体类
│   │   │       └── DbConnection.java                # 数据库连接实体
│   │   └── resources/
│   │       ├── application.yml                      # 基础配置
│   │       ├── application-dev.yml                  # 开发环境配置
│   │       └── application-prod.yml                 # 生产环境配置
│   └── test/
└── pom.xml
```

## 快速开始

### 前置要求
- JDK 21
- Maven 3.6+

### 编译项目
```bash
cd backend
mvn clean install
```

### 运行项目

**开发模式**:
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

**生产模式**:
```bash
java -jar target/database-management-system-1.0.0-SNAPSHOT.jar --spring.profiles.active=prod
```

### 验证运行
访问健康检查接口：
```bash
curl http://localhost:8080/api/health
```

预期返回：
```json
{
  "status": "UP",
  "message": "Database Management System is running",
  "version": "1.0.0"
}
```

## 配置说明

### 环境变量
- `JWT_SECRET`: JWT签名密钥（生产环境必须修改）
- `AES_SECRET_KEY`: 数据加密密钥（生产环境必须修改）

### 内置SQLite数据库
- 路径: `./data/metadata.db`
- 用途: 存储用户连接配置、AI配置、SQL历史等元数据

## 开发进度
- [x] 项目脚手架搭建
- [x] 基础包结构创建
- [x] 主启动类配置
- [x] Spring Security临时配置
- [x] 健康检查接口
- [ ] JWT认证实现
- [ ] 动态数据源管理
- [ ] SQL执行引擎
- [ ] AI集成

## 后续任务
1. 实现JWT验证Filter和调试模式
2. 实现动态数据源管理（AbstractRoutingDataSource）
3. 创建内置SQLite元数据表初始化脚本
4. 实现数据库连接管理API
5. 实现SQL执行引擎
6. 集成AI服务调用

## 参考文档
详细设计参考：`../docs/superpowers/specs/2026-06-10-database-management-system-design.md`
