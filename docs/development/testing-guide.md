# 测试指南

## 测试策略

- **单元测试覆盖率**: ≥70%
- **集成测试**: 覆盖核心业务流程
- **端到端测试**: 覆盖关键用户路径

## 后端测试

### 技术栈

- **JUnit 5**
- **Spring Boot Test**
- **Mockito**
- **H2 Database** (测试用内存数据库)

### 测试结构

```
src/test/java/com/dbmanager/
├── controller/
│   ├── ConnectionControllerTest.java
│   └── SqlControllerTest.java
├── service/
│   ├── ConnectionServiceTest.java
│   └── SqlExecutionServiceTest.java
├── integration/
│   ├── ConnectionIntegrationTest.java
│   └── SqlExecutionIntegrationTest.java
└── util/
    └── EncryptionUtilTest.java
```

### 单元测试示例

#### Service层测试

```java
@SpringBootTest
@ExtendWith(MockitoExtension.class)
class ConnectionServiceTest {
    
    @Mock
    private ConnectionRepository connectionRepository;
    
    @Mock
    private EncryptionUtil encryptionUtil;
    
    @InjectMocks
    private ConnectionService connectionService;
    
    @Test
    @DisplayName("添加连接 - 成功")
    void testAddConnection_Success() {
        // Given
        String userId = "user123";
        CreateConnectionRequest request = CreateConnectionRequest.builder()
            .name("测试数据库")
            .dbType("postgresql")
            .host("localhost")
            .port(5432)
            .database("testdb")
            .username("postgres")
            .password("password")
            .build();
        
        when(encryptionUtil.encrypt("password")).thenReturn("encrypted_password");
        
        Connection savedConnection = Connection.builder()
            .id(1L)
            .userId(userId)
            .name("测试数据库")
            .build();
        
        when(connectionRepository.save(any(Connection.class))).thenReturn(savedConnection);
        
        // When
        ConnectionDTO result = connectionService.addConnection(userId, request);
        
        // Then
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("测试数据库", result.getName());
        verify(encryptionUtil).encrypt("password");
        verify(connectionRepository).save(any(Connection.class));
    }
    
    @Test
    @DisplayName("添加连接 - 连接测试失败")
    void testAddConnection_ConnectionFailed() {
        // Given
        CreateConnectionRequest request = CreateConnectionRequest.builder()
            .host("invalid-host")
            .build();
        
        // When & Then
        assertThrows(ConnectionTestException.class, () -> {
            connectionService.addConnection("user123", request);
        });
    }
}
```

#### Controller层测试

```java
@SpringBootTest
@AutoConfigureMockMvc
class ConnectionControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private ConnectionService connectionService;
    
    @MockBean
    private JwtTokenProvider jwtTokenProvider;
    
    @Test
    @DisplayName("GET /api/connections - 获取连接列表")
    void testGetConnections() throws Exception {
        // Given
        List<ConnectionDTO> connections = List.of(
            ConnectionDTO.builder()
                .id(1L)
                .name("测试数据库")
                .dbType("postgresql")
                .build()
        );
        
        when(connectionService.getConnections("user123")).thenReturn(connections);
        when(jwtTokenProvider.getUserIdFromToken(anyString())).thenReturn("user123");
        
        // When & Then
        mockMvc.perform(get("/api/connections")
                .header("Authorization", "Bearer mock-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.connections[0].name").value("测试数据库"));
    }
    
    @Test
    @DisplayName("POST /api/connections - 添加连接")
    void testAddConnection() throws Exception {
        // Given
        ConnectionDTO connection = ConnectionDTO.builder()
            .id(1L)
            .name("新数据库")
            .build();
        
        when(connectionService.addConnection(eq("user123"), any())).thenReturn(connection);
        when(jwtTokenProvider.getUserIdFromToken(anyString())).thenReturn("user123");
        
        // When & Then
        mockMvc.perform(post("/api/connections")
                .header("Authorization", "Bearer mock-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "name": "新数据库",
                        "dbType": "postgresql",
                        "host": "localhost",
                        "port": 5432,
                        "database": "testdb",
                        "username": "postgres",
                        "password": "password"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(1));
    }
}
```

#### 工具类测试

```java
class EncryptionUtilTest {
    
    private EncryptionUtil encryptionUtil;
    
    @BeforeEach
    void setUp() {
        encryptionUtil = new EncryptionUtil();
        ReflectionTestUtils.setField(encryptionUtil, "secretKey", "test-secret-key-32-chars-long!");
    }
    
    @Test
    @DisplayName("加密解密 - 往返一致")
    void testEncryptDecrypt() {
        // Given
        String plainText = "password123";
        
        // When
        String encrypted = encryptionUtil.encrypt(plainText);
        String decrypted = encryptionUtil.decrypt(encrypted);
        
        // Then
        assertNotEquals(plainText, encrypted);
        assertEquals(plainText, decrypted);
    }
    
    @Test
    @DisplayName("加密 - 相同明文产生不同密文（因为IV不同）")
    void testEncrypt_DifferentIV() {
        String plainText = "password123";
        
        String encrypted1 = encryptionUtil.encrypt(plainText);
        String encrypted2 = encryptionUtil.encrypt(plainText);
        
        assertNotEquals(encrypted1, encrypted2);
        assertEquals(plainText, encryptionUtil.decrypt(encrypted1));
        assertEquals(plainText, encryptionUtil.decrypt(encrypted2));
    }
}
```

### 集成测试

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "app.auth.debug-mode=true"
})
class ConnectionIntegrationTest {
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Autowired
    private ConnectionRepository connectionRepository;
    
    private String token;
    
    @BeforeEach
    void setUp() {
        // 使用debug模式获取token
        ResponseEntity<Map> response = restTemplate.postForEntity(
            "/api/auth/debug-login",
            Map.of("userId", "test-user"),
            Map.class
        );
        token = (String) response.getBody().get("token");
    }
    
    @AfterEach
    void tearDown() {
        connectionRepository.deleteAll();
    }
    
    @Test
    @DisplayName("完整流程：添加 -> 获取 -> 切换 -> 删除连接")
    void testConnectionFullFlow() {
        // 1. 添加连接
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        
        Map<String, Object> connectionRequest = Map.of(
            "name", "测试PostgreSQL",
            "dbType", "postgresql",
            "host", "localhost",
            "port", 5432,
            "database", "testdb",
            "username", "postgres",
            "password", "password"
        );
        
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(connectionRequest, headers);
        ResponseEntity<Map> addResponse = restTemplate.postForEntity(
            "/api/connections",
            request,
            Map.class
        );
        
        assertEquals(HttpStatus.OK, addResponse.getStatusCode());
        Integer connectionId = (Integer) addResponse.getBody().get("connectionId");
        assertNotNull(connectionId);
        
        // 2. 获取连接列表
        HttpEntity<Void> getRequest = new HttpEntity<>(headers);
        ResponseEntity<Map> getResponse = restTemplate.exchange(
            "/api/connections",
            HttpMethod.GET,
            getRequest,
            Map.class
        );
        
        assertEquals(HttpStatus.OK, getResponse.getStatusCode());
        List<Map> connections = (List<Map>) getResponse.getBody().get("connections");
        assertEquals(1, connections.size());
        assertEquals("测试PostgreSQL", connections.get(0).get("name"));
        
        // 3. 切换连接
        ResponseEntity<Map> switchResponse = restTemplate.postForEntity(
            "/api/connections/" + connectionId + "/switch",
            new HttpEntity<>(headers),
            Map.class
        );
        
        assertEquals(HttpStatus.OK, switchResponse.getStatusCode());
        assertNotNull(switchResponse.getBody().get("schema"));
        
        // 4. 删除连接
        restTemplate.exchange(
            "/api/connections/" + connectionId,
            HttpMethod.DELETE,
            new HttpEntity<>(headers),
            Map.class
        );
        
        // 验证删除
        ResponseEntity<Map> verifyResponse = restTemplate.exchange(
            "/api/connections",
            HttpMethod.GET,
            getRequest,
            Map.class
        );
        List<Map> remainingConnections = (List<Map>) verifyResponse.getBody().get("connections");
        assertEquals(0, remainingConnections.size());
    }
}
```

### SQL执行测试

```java
@SpringBootTest
class SqlExecutionServiceTest {
    
    @Autowired
    private SqlExecutionService sqlExecutionService;
    
    @Test
    @DisplayName("执行SELECT查询")
    void testExecuteQuery() {
        String sql = "SELECT * FROM test_table LIMIT 10";
        
        QueryResult result = sqlExecutionService.executeQuery(sql);
        
        assertNotNull(result);
        assertNotNull(result.getColumns());
        assertNotNull(result.getRows());
        assertTrue(result.getExecutionTime() > 0);
    }
    
    @Test
    @DisplayName("检测危险操作 - DELETE")
    void testDangerousOperation_Delete() {
        String sql = "DELETE FROM users WHERE id = 1";
        
        assertThrows(DangerousOperationException.class, () -> {
            sqlExecutionService.executeUpdate(sql, false);
        });
    }
    
    @Test
    @DisplayName("危险操作确认后执行")
    void testDangerousOperation_Confirmed() {
        String sql = "DELETE FROM users WHERE id = 1";
        
        int affected = sqlExecutionService.executeUpdate(sql, true);
        
        assertTrue(affected >= 0);
    }
}
```

## 前端测试

### 技术栈

- **Vitest** (单元测试)
- **Vue Test Utils** (组件测试)
- **Playwright** (E2E测试)

### 组件测试

```typescript
// src/components/__tests__/SQLEditor.spec.ts
import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import SQLEditor from '../SQLEditor.vue'

describe('SQLEditor', () => {
  it('渲染编辑器容器', () => {
    const wrapper = mount(SQLEditor, {
      props: { tabId: 'test-tab' },
      global: {
        plugins: [createPinia()]
      }
    })
    
    expect(wrapper.find('.sql-editor').exists()).toBe(true)
  })
  
  it('Ctrl+Enter执行SQL', async () => {
    const wrapper = mount(SQLEditor, {
      props: { tabId: 'test-tab' },
      global: {
        plugins: [createPinia()]
      }
    })
    
    // 模拟按键
    await wrapper.find('.sql-editor').trigger('keydown', {
      key: 'Enter',
      ctrlKey: true
    })
    
    // 验证SQL执行被调用
    // (需要mock store)
  })
})
```

### Store测试

```typescript
// src/stores/__tests__/connection.spec.ts
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useConnectionStore } from '../connection'
import * as connectionApi from '@/api/connection'

vi.mock('@/api/connection')

describe('Connection Store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })
  
  it('获取连接列表', async () => {
    const mockConnections = [
      { id: 1, name: '测试数据库', dbType: 'postgresql' }
    ]
    
    vi.mocked(connectionApi.getConnections).mockResolvedValue({
      success: true,
      data: { connections: mockConnections }
    })
    
    const store = useConnectionStore()
    await store.fetchConnections()
    
    expect(store.connections).toEqual(mockConnections)
  })
  
  it('切换连接并加载schema', async () => {
    const mockSchema = {
      tables: [{ tableName: 'users', rowCount: 100 }]
    }
    
    vi.mocked(connectionApi.switchConnection).mockResolvedValue({
      success: true,
      data: { schema: mockSchema }
    })
    
    const store = useConnectionStore()
    store.connections = [{ id: 1, name: '测试', dbType: 'postgresql' }]
    
    await store.switchConnection(1)
    
    expect(store.currentConnection?.id).toBe(1)
    expect(store.schema).toEqual(mockSchema)
  })
})
```

### E2E测试

```typescript
// tests/e2e/sql-execution.spec.ts
import { test, expect } from '@playwright/test'

test.describe('SQL执行流程', () => {
  test.beforeEach(async ({ page }) => {
    // 登录
    await page.goto('/login')
    await page.fill('[data-testid="user-id-input"]', 'test-user')
    await page.click('[data-testid="login-button"]')
    await expect(page).toHaveURL('/editor')
  })
  
  test('执行SELECT查询', async ({ page }) => {
    // 1. 选择数据库连接
    await page.click('[data-testid="connection-selector"]')
    await page.click('text=测试数据库')
    
    // 2. 输入SQL
    await page.fill('.monaco-editor textarea', 'SELECT * FROM users LIMIT 10')
    
    // 3. 执行
    await page.click('[data-testid="execute-button"]')
    
    // 4. 验证结果
    await expect(page.locator('.result-table')).toBeVisible()
    await expect(page.locator('.result-table tr')).toHaveCount(11) // header + 10 rows
  })
  
  test('危险操作二次确认', async ({ page }) => {
    await page.fill('.monaco-editor textarea', 'DELETE FROM users WHERE id = 1')
    await page.click('[data-testid="execute-button"]')
    
    // 验证确认对话框出现
    await expect(page.locator('.el-message-box')).toBeVisible()
    await expect(page.locator('.el-message-box__message')).toContainText('危险操作')
    
    // 取消操作
    await page.click('text=取消')
    await expect(page.locator('.el-message-box')).not.toBeVisible()
  })
  
  test('AI生成SQL', async ({ page }) => {
    // 1. 打开AI助手
    await page.click('[data-testid="ai-assistant-toggle"]')
    
    // 2. 输入自然语言
    await page.fill('[data-testid="ai-prompt-input"]', '查询所有年龄大于18岁的用户')
    await page.click('[data-testid="ai-generate-button"]')
    
    // 3. 等待AI响应
    await page.waitForSelector('[data-testid="ai-response"]', { timeout: 15000 })
    
    // 4. 应用到编辑器
    await page.click('[data-testid="apply-sql-button"]')
    
    // 5. 验证SQL被填充
    const editorContent = await page.textContent('.monaco-editor')
    expect(editorContent).toContain('SELECT')
    expect(editorContent).toContain('age > 18')
  })
})
```

## 测试覆盖率要求

### 必须测试的场景

#### 后端
- [x] JWT认证（有效/无效/过期token）
- [x] 数据库连接管理（添加/删除/切换）
- [x] 密码加密/解密
- [x] SQL执行（SELECT/INSERT/UPDATE/DELETE）
- [x] 危险操作检测
- [x] AI调用（成功/超时/失败）
- [x] Schema查询（PostgreSQL/SQLite）

#### 前端
- [x] Monaco Editor初始化和智能提示
- [x] SQL执行和结果展示
- [x] 连接切换和状态管理
- [x] AI对话历史
- [x] 复杂类型字段渲染
- [x] 结果导出

## 运行测试

### 后端

```bash
# 运行所有测试
mvn test

# 运行特定测试类
mvn test -Dtest=ConnectionServiceTest

# 生成覆盖率报告
mvn test jacoco:report

# 查看覆盖率报告
open target/site/jacoco/index.html
```

### 前端

```bash
# 运行单元测试
npm run test

# 运行测试并生成覆盖率
npm run test:coverage

# 运行E2E测试
npm run test:e2e

# E2E测试（UI模式）
npm run test:e2e:ui
```

## 持续集成

### GitHub Actions配置示例

```yaml
# .github/workflows/test.yml
name: Tests

on: [push, pull_request]

jobs:
  backend-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Set up JDK 21
        uses: actions/setup-java@v3
        with:
          java-version: '21'
      - name: Run tests
        run: mvn test
      - name: Generate coverage report
        run: mvn jacoco:report
      - name: Upload coverage
        uses: codecov/codecov-action@v3
  
  frontend-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Set up Node.js
        uses: actions/setup-node@v3
        with:
          node-version: '18'
      - name: Install dependencies
        run: npm ci
      - name: Run tests
        run: npm run test:coverage
      - name: Upload coverage
        uses: codecov/codecov-action@v3
```

## 测试数据准备

### 测试数据库初始化

```sql
-- src/test/resources/test-data.sql
CREATE TABLE users (
    id INTEGER PRIMARY KEY,
    name VARCHAR(100),
    age INTEGER,
    email VARCHAR(200)
);

INSERT INTO users (id, name, age, email) VALUES
(1, 'Alice', 25, 'alice@example.com'),
(2, 'Bob', 30, 'bob@example.com'),
(3, 'Charlie', 17, 'charlie@example.com');
```

### Mock数据工厂

```java
// src/test/java/com/dbmanager/TestDataFactory.java
public class TestDataFactory {
    
    public static Connection createTestConnection() {
        return Connection.builder()
            .id(1L)
            .userId("test-user")
            .name("测试数据库")
            .dbType("postgresql")
            .host("localhost")
            .port(5432)
            .database("testdb")
            .isActive(true)
            .build();
    }
    
    public static CreateConnectionRequest createConnectionRequest() {
        return CreateConnectionRequest.builder()
            .name("测试数据库")
            .dbType("postgresql")
            .host("localhost")
            .port(5432)
            .database("testdb")
            .username("postgres")
            .password("password")
            .build();
    }
}
```
