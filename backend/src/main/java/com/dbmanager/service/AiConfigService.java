package com.dbmanager.service;

import com.dbmanager.entity.AiConfig;
import com.dbmanager.repository.AiConfigRepository;
import com.dbmanager.util.AESUtil;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class AiConfigService {

    private final AiConfigRepository repository;
    private final AESUtil aesUtil;
    private final RestTemplate restTemplate = new RestTemplate();

    public AiConfigService(AiConfigRepository repository, AESUtil aesUtil) {
        this.repository = repository;
        this.aesUtil = aesUtil;
    }

    public AiConfig getConfig(String userId) {
        AiConfig config = repository.findByUserId(userId);
        if (config != null) {
            config.setApiKeyEncrypted(null); // never return encrypted key
        }
        return config;
    }

    public AiConfig saveConfig(String userId, Map<String, Object> request) throws Exception {
        AiConfig config = new AiConfig();
        config.setUserId(userId);
        config.setApiProvider((String) request.get("apiProvider"));
        config.setApiBaseUrl((String) request.get("apiBaseUrl"));
        config.setModelName((String) request.get("modelName"));
        config.setTemperature(request.get("temperature") != null ?
            Double.valueOf(request.get("temperature").toString()) : 0.7);
        config.setMaxTokens(request.get("maxTokens") != null ?
            Integer.valueOf(request.get("maxTokens").toString()) : 2000);

        String apiKey = (String) request.get("apiKey");
        if (apiKey != null && !apiKey.isEmpty()) {
            config.setApiKeyEncrypted(aesUtil.encrypt(apiKey));
        }

        AiConfig saved = repository.save(config);
        saved.setApiKeyEncrypted(null);
        return saved;
    }

    public Map<String, Object> testConnection(String userId) throws Exception {
        AiConfig config = repository.findByUserId(userId);
        if (config == null) {
            throw new RuntimeException("请先配置AI参数");
        }

        String apiKey = aesUtil.decrypt(config.getApiKeyEncrypted());
        String baseUrl = config.getApiBaseUrl();
        if (!baseUrl.endsWith("/")) baseUrl += "/";

        // Try to list models as a connectivity test
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> result = new HashMap<>();
        long start = System.currentTimeMillis();

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl + "models",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class
            );

            long elapsed = System.currentTimeMillis() - start;
            result.put("success", response.getStatusCode().is2xxSuccessful());
            result.put("latency", elapsed);
            result.put("message", "连接成功 (" + elapsed + "ms)");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "连接失败: " + e.getMessage());
        }

        return result;
    }

    public String getDecryptedApiKey(String userId) throws Exception {
        AiConfig config = repository.findByUserId(userId);
        if (config == null) return null;
        return aesUtil.decrypt(config.getApiKeyEncrypted());
    }
}
