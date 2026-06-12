# Task #2: JWT认证 + 开发调试模式 - 实现文档

## 实现概述

已完成JWT认证和开发调试模式的实现，包括以下组件：

### 1. JWT工具类 (JwtUtil.java)
**位置**: `backend/src/main/java/com/dbmanager/util/JwtUtil.java`

**功能**:
- `generateToken(userId)`: 生成JWT token，有效期24小时
- `extractUserId(token)`: 从token中提取userId
- `validateToken(token)`: 验证token有效性
- 使用JJWT 0.12.5库，HMAC-SHA算法签名

### 2. JWT认证过滤器 (JwtAuthenticationFilter.java)
**位置**: `backend/src/main/java/com/dbmanager/security/JwtAuthenticationFilter.java`

**功能**:
- 继承`OncePerRequestFilter`，每个请求执行一次
- 从请求头`Authorization: Bearer <token>`提取token
- 验证token并设置Spring Security上下文
- 验证失败时不抛出异常，继续过滤链（由SecurityConfig决定是否拒绝）

### 3. 开发模式登录接口 (AuthController.java)
**位置**: `backend/src/main/java/com/dbmanager/controller/AuthController.java`

**接口**: `POST /api/auth/debug-login`
**请求体**: 
```json
{
  "userId": "user123"
}
```
**响应**:
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "userId": "user123"
}
```

**安全控制**:
- 仅在`app.auth.debug-mode=true`时可用
- 生产模式下返回403 Forbidden

### 4. Spring Security配置 (SecurityConfig.java)
**位置**: `backend/src/main/java/com/dbmanager/config/SecurityConfig.java`

**配置**:
- 禁用CSRF（因为使用JWT无状态认证）
- Session策略：STATELESS
- `/api/auth/**`路径允许匿名访问
- 其他所有路径需要认证
- JWT过滤器在UsernamePasswordAuthenticationFilter之前执行

### 5. 健康检查接口增强 (HealthController.java)
**位置**: `backend/src/main/java/com/dbmanager/controller/HealthController.java`

**接口**: `GET /api/health`
**响应**:
```json
{
  "status": "UP",
  "message": "Database Management System is running",
  "version": "1.0.0",
  "userId": "user123"  // 仅在已认证时返回
}
```

### 6. 配置文件

**application.yml**:
```yaml
app:
  auth:
    debug-mode: false  # 默认关闭
    jwt-secret: ${JWT_SECRET:your-secret-key-change-in-production}
    jwt-expiration: 86400000  # 24小时
```

**application-dev.yml**:
```yaml
app:
  auth:
    debug-mode: true  # 开发模式开启
```

## 验收标准测试

### 测试场景1: 生产模式 - 无token访问
**命令**:
```bash
curl -X GET http://localhost:8080/api/health
```
**预期结果**: 401 Unauthorized

### 测试场景2: 生产模式 - 无效token
**命令**:
```bash
curl -X GET http://localhost:8080/api/health \
  -H "Authorization: Bearer invalid-token"
```
**预期结果**: 401 Unauthorized

### 测试场景3: 生产模式 - 有效token
**命令**:
```bash
# 假设已有有效token
curl -X GET http://localhost:8080/api/health \
  -H "Authorization: Bearer <valid-token>"
```
**预期结果**: 200 OK，返回健康信息和userId

### 测试场景4: 开发模式 - debug登录
**前提**: 启动应用时使用`--spring.profiles.active=dev`
**命令**:
```bash
curl -X POST http://localhost:8080/api/auth/debug-login \
  -H "Content-Type: application/json" \
  -d '{"userId": "testuser"}'
```
**预期结果**: 200 OK
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "userId": "testuser"
}
```

### 测试场景5: 开发模式 - 使用debug token访问
**命令**:
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/debug-login \
  -H "Content-Type: application/json" \
  -d '{"userId": "testuser"}' | jq -r '.token')

curl -X GET http://localhost:8080/api/health \
  -H "Authorization: Bearer $TOKEN"
```
**预期结果**: 200 OK
```json
{
  "status": "UP",
  "message": "Database Management System is running",
  "version": "1.0.0",
  "userId": "testuser"
}
```

### 测试场景6: 生产模式 - debug接口被禁用
**前提**: 使用默认配置或`--spring.profiles.active=prod`
**命令**:
```bash
curl -X POST http://localhost:8080/api/auth/debug-login \
  -H "Content-Type: application/json" \
  -d '{"userId": "testuser"}'
```
**预期结果**: 403 Forbidden
```json
{
  "message": "Debug mode is disabled"
}
```

## 技术要点

### 1. JWT库选择
- 使用JJWT 0.12.5（最新版本）
- 自动处理token过期验证
- 类型安全的API

### 2. 安全考虑
- JWT密钥从环境变量读取，避免硬编码
- 开发模式通过配置严格控制
- 无状态认证，不在服务器端存储session
- token有效期24小时，可配置

### 3. 代码质量
- 最小化实现，无冗余代码
- 符合Spring Boot最佳实践
- 异常处理清晰
- 日志记录完善（通过配置控制级别）

## 依赖项

已在pom.xml中配置：
```xml
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.5</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.5</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.5</version>
    <scope>runtime</scope>
</dependency>
```

## 启动方式

### 开发模式
```bash
java -jar database-management-system.jar --spring.profiles.active=dev
```

### 生产模式
```bash
java -jar database-management-system.jar \
  --spring.profiles.active=prod \
  --app.auth.jwt-secret=your-production-secret-key
```

## 集成说明

### 前端集成
1. **开发模式登录**:
```typescript
const response = await axios.post('/api/auth/debug-login', {
  userId: 'testuser'
});
const token = response.data.token;
localStorage.setItem('token', token);
```

2. **携带token访问API**:
```typescript
axios.defaults.headers.common['Authorization'] = `Bearer ${token}`;
```

3. **生产模式**:
直接从现有系统获取JWT token，无需调用debug-login接口

## 验收清单

- [x] JWT工具类实现（生成、验证、提取userId）
- [x] JWT认证过滤器实现
- [x] 开发模式登录接口实现
- [x] Spring Security配置正确
- [x] 配置项`app.auth.debug-mode`控制开发模式
- [x] 生产模式强制JWT验证
- [x] 开发模式debug接口可用
- [x] 无token或无效token返回401
- [x] 有效token可访问/api/health
- [x] 代码最小化，无冗余实现

## 后续任务

Task #2已完成，可继续进行：
- Task #1: 内置SQLite元数据库初始化
- Task #3: 数据库连接管理模块
- Task #4: SQL执行引擎

## 文件清单

新增文件：
1. `backend/src/main/java/com/dbmanager/util/JwtUtil.java`
2. `backend/src/main/java/com/dbmanager/security/JwtAuthenticationFilter.java`
3. `backend/src/main/java/com/dbmanager/controller/AuthController.java`

修改文件：
1. `backend/src/main/java/com/dbmanager/config/SecurityConfig.java`
2. `backend/src/main/java/com/dbmanager/controller/HealthController.java`

配置文件（已存在，无需修改）：
1. `backend/src/main/resources/application.yml`
2. `backend/src/main/resources/application-dev.yml`
