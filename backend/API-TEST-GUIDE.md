# Task #2 API测试指南

## 前置条件
确保应用已启动：
```bash
# 开发模式启动（启用debug-login接口）
java -jar database-management-system.jar --spring.profiles.active=dev

# 或
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

## 测试场景

### 场景1: 开发模式 - Debug登录并访问受保护接口

#### 步骤1: Debug登录获取token
```bash
curl -X POST http://localhost:8080/api/auth/debug-login \
  -H "Content-Type: application/json" \
  -d '{"userId": "testuser123"}'
```

**预期响应** (200 OK):
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0dXNlcjEyMyIsImlhdCI6MTcxODEwMDAwMCwiZXhwIjoxNzE4MTg2NDAwfQ.xxx",
  "userId": "testuser123"
}
```

#### 步骤2: 使用token访问健康检查接口
```bash
# 将上一步的token保存到变量
TOKEN="eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0dXNlcjEyMyIsImlhdCI6MTcxODEwMDAwMCwiZXhwIjoxNzE4MTg2NDAwfQ.xxx"

curl -X GET http://localhost:8080/api/health \
  -H "Authorization: Bearer $TOKEN"
```

**预期响应** (200 OK):
```json
{
  "status": "UP",
  "message": "Database Management System is running",
  "version": "1.0.0",
  "userId": "testuser123"
}
```

#### 步骤3: 不带token访问（应失败）
```bash
curl -X GET http://localhost:8080/api/health
```

**预期响应** (401 Unauthorized):
```json
{
  "timestamp": "2026-06-11T06:00:00.000+00:00",
  "status": 401,
  "error": "Unauthorized",
  "path": "/api/health"
}
```

#### 步骤4: 使用无效token访问（应失败）
```bash
curl -X GET http://localhost:8080/api/health \
  -H "Authorization: Bearer invalid.token.here"
```

**预期响应** (401 Unauthorized)

---

### 场景2: 生产模式 - Debug接口被禁用

#### 重启应用为生产模式
```bash
java -jar database-management-system.jar --spring.profiles.active=prod
```

#### 尝试访问debug-login接口
```bash
curl -X POST http://localhost:8080/api/auth/debug-login \
  -H "Content-Type: application/json" \
  -d '{"userId": "testuser123"}'
```

**预期响应** (403 Forbidden):
```json
{
  "message": "Debug mode is disabled"
}
```

---

### 场景3: 生产模式 - 使用现有系统的JWT token

假设现有系统提供的JWT token：
```bash
PROD_TOKEN="eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJwcm9kdXNlciIsImlhdCI6MTcxODEwMDAwMCwiZXhwIjoxNzE4MTg2NDAwfQ.yyy"

curl -X GET http://localhost:8080/api/health \
  -H "Authorization: Bearer $PROD_TOKEN"
```

**预期响应** (200 OK):
```json
{
  "status": "UP",
  "message": "Database Management System is running",
  "version": "1.0.0",
  "userId": "produser"
}
```

---

## 完整测试脚本

### test-dev-mode.sh
```bash
#!/bin/bash

echo "===== Task #2 开发模式测试 ====="

BASE_URL="http://localhost:8080"

echo ""
echo "测试1: Debug登录"
RESPONSE=$(curl -s -X POST $BASE_URL/api/auth/debug-login \
  -H "Content-Type: application/json" \
  -d '{"userId": "testuser"}')
echo $RESPONSE | jq .

TOKEN=$(echo $RESPONSE | jq -r '.token')

if [ "$TOKEN" != "null" ] && [ -n "$TOKEN" ]; then
  echo "✓ Debug登录成功，获取到token"
else
  echo "✗ Debug登录失败"
  exit 1
fi

echo ""
echo "测试2: 使用token访问/api/health"
HEALTH=$(curl -s -X GET $BASE_URL/api/health \
  -H "Authorization: Bearer $TOKEN")
echo $HEALTH | jq .

USER_ID=$(echo $HEALTH | jq -r '.userId')
if [ "$USER_ID" = "testuser" ]; then
  echo "✓ 认证成功，userId正确"
else
  echo "✗ 认证失败或userId错误"
  exit 1
fi

echo ""
echo "测试3: 不带token访问/api/health（应返回401）"
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" $BASE_URL/api/health)
if [ "$HTTP_CODE" = "401" ]; then
  echo "✓ 正确返回401 Unauthorized"
else
  echo "✗ 预期401，实际返回$HTTP_CODE"
  exit 1
fi

echo ""
echo "测试4: 使用无效token访问（应返回401）"
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" $BASE_URL/api/health \
  -H "Authorization: Bearer invalid.token")
if [ "$HTTP_CODE" = "401" ]; then
  echo "✓ 正确返回401 Unauthorized"
else
  echo "✗ 预期401，实际返回$HTTP_CODE"
  exit 1
fi

echo ""
echo "===== 所有测试通过 ✓ ====="
```

### test-prod-mode.sh
```bash
#!/bin/bash

echo "===== Task #2 生产模式测试 ====="

BASE_URL="http://localhost:8080"

echo ""
echo "测试1: 生产模式下访问debug-login（应返回403）"
RESPONSE=$(curl -s -X POST $BASE_URL/api/auth/debug-login \
  -H "Content-Type: application/json" \
  -d '{"userId": "testuser"}')
echo $RESPONSE | jq .

MESSAGE=$(echo $RESPONSE | jq -r '.message')
if [ "$MESSAGE" = "Debug mode is disabled" ]; then
  echo "✓ Debug模式正确被禁用"
else
  echo "✗ Debug模式应该被禁用"
  exit 1
fi

echo ""
echo "测试2: 不带token访问/api/health（应返回401）"
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" $BASE_URL/api/health)
if [ "$HTTP_CODE" = "401" ]; then
  echo "✓ 正确返回401 Unauthorized"
else
  echo "✗ 预期401，实际返回$HTTP_CODE"
  exit 1
fi

echo ""
echo "===== 生产模式测试通过 ✓ ====="
```

---

## 使用说明

1. **赋予执行权限**:
```bash
chmod +x test-dev-mode.sh test-prod-mode.sh
```

2. **运行开发模式测试**:
```bash
# 先以dev profile启动应用
java -jar database-management-system.jar --spring.profiles.active=dev &

# 等待启动完成后运行测试
sleep 5
./test-dev-mode.sh
```

3. **运行生产模式测试**:
```bash
# 先以prod profile启动应用
java -jar database-management-system.jar --spring.profiles.active=prod &

# 等待启动完成后运行测试
sleep 5
./test-prod-mode.sh
```

---

## 前端集成示例

### Vue 3 + TypeScript

```typescript
// api/auth.ts
import axios from 'axios';

interface LoginResponse {
  token: string;
  userId: string;
}

export const authApi = {
  // 开发模式登录
  debugLogin: async (userId: string): Promise<LoginResponse> => {
    const response = await axios.post('/api/auth/debug-login', { userId });
    return response.data;
  },
};

// utils/auth.ts
const TOKEN_KEY = 'jwt_token';

export const authUtils = {
  saveToken: (token: string) => {
    localStorage.setItem(TOKEN_KEY, token);
  },
  
  getToken: (): string | null => {
    return localStorage.getItem(TOKEN_KEY);
  },
  
  clearToken: () => {
    localStorage.removeItem(TOKEN_KEY);
  },
  
  setupAxiosInterceptor: () => {
    axios.interceptors.request.use(config => {
      const token = authUtils.getToken();
      if (token) {
        config.headers.Authorization = `Bearer ${token}`;
      }
      return config;
    });
    
    axios.interceptors.response.use(
      response => response,
      error => {
        if (error.response?.status === 401) {
          authUtils.clearToken();
          window.location.href = '/login';
        }
        return Promise.reject(error);
      }
    );
  },
};

// main.ts
import { authUtils } from './utils/auth';

authUtils.setupAxiosInterceptor();

// components/DevLogin.vue
<script setup lang="ts">
import { ref } from 'vue';
import { authApi } from '@/api/auth';
import { authUtils } from '@/utils/auth';
import { useRouter } from 'vue-router';

const userId = ref('');
const router = useRouter();

const handleLogin = async () => {
  try {
    const { token } = await authApi.debugLogin(userId.value);
    authUtils.saveToken(token);
    router.push('/dashboard');
  } catch (error) {
    console.error('Login failed:', error);
  }
};
</script>

<template>
  <div>
    <input v-model="userId" placeholder="输入用户ID" />
    <button @click="handleLogin">登录</button>
  </div>
</template>
```

---

## 验收确认

- [x] 开发模式下`POST /api/auth/debug-login`返回有效token
- [x] 使用token访问`GET /api/health`返回200和userId
- [x] 不带token访问`GET /api/health`返回401
- [x] 使用无效token访问返回401
- [x] 生产模式下`debug-login`接口返回403
- [x] JWT token包含userId并可正确提取
- [x] Token过期时间为24小时
- [x] 配置通过`app.auth.debug-mode`控制
