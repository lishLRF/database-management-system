# 后端开发指南

## 技术栈

- **Java 21**
- **Spring Boot 3.2.5**
- **Spring Security 3.2.5** (JWT验证)
- **Spring JDBC 3.2.5** (多数据源)
- **SQLite 3.45.3.0** (元数据库)
- **HikariCP** (连接池)

## 项目结构

```
backend/
├── src/main/java/com/dbmanager/
│   ├── config/              # 配置类
│   │   ├── SecurityConfig.java
│   │   ├── DataSourceConfig.java
│   │   └── EncryptionConfig.java
│   ├── controller/          # 控制器
│   │   ├── AuthController.java
│   │   ├── ConnectionController.java
│   │   ├── SqlController.java
│   │   └── AiController.java
│   ├── service/             # 业务逻辑
│   │   ├── AuthService.java
│   │   ├── ConnectionService.java
│   │   ├── SqlExecutionService.java
│   │   └── AiService.java
│   ├── repository/          # 数据访问
│   │   ├── ConnectionRepository.java
│   │   └── HistoryRepository.java
│   ├── model/               # 数据模型
│   │   ├── entity/
│   │   ├── dto/
│   │   └── vo/
│   ├── security/            # 安全组件
│   │   ├── JwtTokenProvider.java
│   │   └── JwtAuthenticationFilter.java
│   ├── exception/           # 异常处理
│   │   ├── GlobalExceptionHandler.java
│   │   └── CustomExceptions.java
│   └── util/                # 工具类
│       ├── EncryptionUtil.java
│       └── SqlParser.java
└── src/main/resources/
    ├── application.yml
    ├── application-dev.yml
    ├── application-prod.yml
    └── schema.sql           # 元数据库初始化脚本
```

## 核心模式

### 1. 动态数据源管理

```java
@Component
public class DynamicDataSourceHolder {
    private static final ThreadLocal<String> contextHolder = new ThreadLocal<>();
    
    public static void setDataSource(String userId, String connectionId) {
        contextHolder.set(userId + ":" + connectionId);
    }
    
    public static String getDataSourceKey() {
        return contextHolder.get();
    }
    
    public static void clear() {
        contextHolder.remove();
    }
}

@Configuration
public class DynamicDataSource extends AbstractRoutingDataSource {
    @Override
    protected Object determineCurrentLookupKey() {
        return DynamicDataSourceHolder.getDataSourceKey();
    }
}
```

### 2. 连接池管理

```java
@Service
public class DataSourceManager {
    private final ConcurrentHashMap<String, HikariDataSource> dataSources = new ConcurrentHashMap<>();
    
    public DataSource getOrCreateDataSource(String userId, ConnectionConfig config) {
        String key = userId + ":" + config.getId();
        return dataSources.computeIfAbsent(key, k -> createDataSource(config));
    }
    
    private HikariDataSource createDataSource(ConnectionConfig config) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(buildJdbcUrl(config));
        hikariConfig.setUsername(config.getUsername());
        hikariConfig.setPassword(decrypt(config.getPasswordEncrypted()));
        hikariConfig.setMaximumPoolSize(10);
        hikariConfig.setMinimumIdle(2);
        hikariConfig.setConnectionTimeout(30000);
        hikariConfig.setIdleTimeout(1800000); // 30分钟
        return new HikariDataSource(hikariConfig);
    }
}
```

### 3. SQL执行模式

```java
@Service
public class SqlExecutionService {
    private final JdbcTemplate jdbcTemplate;
    
    public QueryResult executeQuery(String sql) {
        long startTime = System.currentTimeMillis();
        
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
            long executionTime = System.currentTimeMillis() - startTime;
            
            return QueryResult.builder()
                .columns(extractColumns(rows))
                .rows(rows)
                .executionTime(executionTime)
                .build();
        } catch (DataAccessException e) {
            throw new SqlExecutionException("SQL执行失败: " + e.getMessage());
        }
    }
    
    public int executeUpdate(String sql, boolean confirmed) {
        if (!confirmed && isDangerousOperation(sql)) {
            throw new DangerousOperationException("需要确认的危险操作");
        }
        
        return jdbcTemplate.update(sql);
    }
    
    private boolean isDangerousOperation(String sql) {
        String upperSql = sql.trim().toUpperCase();
        return upperSql.startsWith("DELETE") 
            || upperSql.startsWith("DROP") 
            || upperSql.startsWith("TRUNCATE");
    }
}
```

### 4. 数据加密

```java
@Component
public class EncryptionUtil {
    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";
    
    @Value("${app.encryption.secret-key}")
    private String secretKey;
    
    public String encrypt(String plainText) {
        try {
            SecretKeySpec key = new SecretKeySpec(secretKey.getBytes(), "AES");
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            
            byte[] iv = generateIV();
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, ivSpec);
            
            byte[] encrypted = cipher.doFinal(plainText.getBytes());
            String ivBase64 = Base64.getEncoder().encodeToString(iv);
            String encryptedBase64 = Base64.getEncoder().encodeToString(encrypted);
            
            return ivBase64 + ":" + encryptedBase64;
        } catch (Exception e) {
            throw new EncryptionException("加密失败", e);
        }
    }
    
    public String decrypt(String encryptedText) {
        // 解密实现
    }
}
```

### 5. AI调用封装

```java
@Service
public class AiService {
    private final RestTemplate restTemplate;
    private final AiConfigRepository configRepository;
    
    public String generateSql(String userId, String prompt, String schemaContext) {
        AiConfig config = configRepository.findByUserId(userId)
            .orElseThrow(() -> new AiNotConfiguredException("请先配置AI"));
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(decrypt(config.getApiKeyEncrypted()));
        
        Map<String, Object> request = Map.of(
            "model", config.getModelName(),
            "messages", List.of(
                Map.of("role", "system", "content", buildSystemPrompt(schemaContext)),
                Map.of("role", "user", "content", prompt)
            ),
            "temperature", config.getTemperature(),
            "max_tokens", config.getMaxTokens()
        );
        
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
        
        try {
            ResponseEntity<AiResponse> response = restTemplate.exchange(
                config.getApiBaseUrl() + "/chat/completions",
                HttpMethod.POST,
                entity,
                AiResponse.class
            );
            
            return response.getBody().getChoices().get(0).getMessage().getContent();
        } catch (RestClientException e) {
            throw new AiCallException("AI调用失败: " + e.getMessage());
        }
    }
}
```

## 配置示例

### application.yml

```yaml
spring:
  application:
    name: database-management-system
  profiles:
    active: dev

server:
  port: 8080

app:
  encryption:
    secret-key: ${AES_SECRET_KEY:your-32-char-secret-key-here!}
  auth:
    jwt-secret: ${JWT_SECRET:your-jwt-secret}
    debug-mode: false
  datasource:
    meta:
      path: ./data/metadata.db
```

### application-dev.yml

```yaml
app:
  auth:
    debug-mode: true

logging:
  level:
    com.dbmanager: DEBUG
```

## 编码规范

### 1. 控制器规范

```java
@RestController
@RequestMapping("/api/connections")
@RequiredArgsConstructor
public class ConnectionController {
    private final ConnectionService connectionService;
    
    @PostMapping
    public ResponseEntity<ApiResponse<ConnectionDTO>> addConnection(
        @RequestBody @Valid CreateConnectionRequest request,
        @AuthenticationPrincipal JwtUser user
    ) {
        ConnectionDTO connection = connectionService.addConnection(user.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success(connection, "连接添加成功"));
    }
}
```

### 2. Service层规范

```java
@Service
@Transactional
@RequiredArgsConstructor
public class ConnectionService {
    private final ConnectionRepository connectionRepository;
    private final EncryptionUtil encryptionUtil;
    
    public ConnectionDTO addConnection(String userId, CreateConnectionRequest request) {
        // 测试连接
        testConnection(request);
        
        // 加密密码
        String encryptedPassword = encryptionUtil.encrypt(request.getPassword());
        
        // 保存
        Connection connection = Connection.builder()
            .userId(userId)
            .name(request.getName())
            .passwordEncrypted(encryptedPassword)
            .build();
            
        return connectionRepository.save(connection);
    }
}
```

### 3. 异常处理

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(DangerousOperationException.class)
    public ResponseEntity<ApiResponse<?>> handleDangerousOperation(DangerousOperationException e) {
        return ResponseEntity.ok(ApiResponse.warning(e.getMessage(), true));
    }
    
    @ExceptionHandler(SqlExecutionException.class)
    public ResponseEntity<ApiResponse<?>> handleSqlExecution(SqlExecutionException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error("SQL_ERROR", e.getMessage()));
    }
}
```

## 测试规范

### 单元测试

```java
@SpringBootTest
@AutoConfigureMockMvc
class ConnectionControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private ConnectionService connectionService;
    
    @Test
    void testAddConnection() throws Exception {
        mockMvc.perform(post("/api/connections")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "name": "测试数据库",
                        "dbType": "postgresql",
                        "host": "localhost",
                        "port": 5432
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }
}
```

## 安全注意事项

1. **永远不要明文存储密码**，使用AES-256加密
2. **使用参数化查询**防止SQL注入
3. **验证所有用户输入**
4. **记录所有危险操作**到审计日志
5. **限制SQL执行时间**，防止长时间占用连接

## 性能优化建议

1. 使用连接池，避免频繁创建连接
2. 大结果集使用流式读取
3. Schema信息缓存5分钟
4. AI请求超时设置30秒
5. 定期清理空闲超过30分钟的连接池
