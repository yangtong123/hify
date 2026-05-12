---
name: provider-adapter
description: >
  Hify 项目接入新 LLM 提供商。流程：分析 API → 实现 Adapter → 编译验证 → 启动测试。
  当用户提到"接入新模型"、"支持XX平台"、"添加XX供应商"、"对接XX API"、"加个XX适配器"、
  "provider adapter"、"怎么加新的LLM"时使用此技能。
---

# Provider Adapter 接入流程

Hify 用策略模式对接不同 LLM 提供商，每种类型一个 Adapter 实现类。加新提供商只需新增一个 Adapter 文件，自动注册生效。

## 架构概览

```
infra/adapter/
├── ProviderAdapter.java          ← 策略接口（不改）
├── ProviderAdapterFactory.java   ← 自动发现所有 Adapter Bean（不改）
├── OpenAiAdapter.java            ← 参考实现
├── AnthropicAdapter.java         ← 参考实现
├── OllamaAdapter.java            ← 参考实现
└── OpenAiCompatibleAdapter.java  ← 继承复用示例
```

- 每个 Adapter 是 `@Component`，Spring 自动注入到 `ProviderAdapterFactory` 的 `List<ProviderAdapter>` 中
- Factory 通过 `getType()` 返回的字符串做路由
- 如果是 OpenAI 兼容协议，直接继承 `OpenAiAdapter`，只需覆盖 `getType()`

## 接入流程

### Step 1 — 分析目标 API

收集以下信息（查文档或让用户提供）：

| 信息 | 说明 | 示例 |
|------|------|------|
| type 值 | 存入数据库的标识字符串，全小写下划线分隔 | `"gemini"`、`"deepseek"` |
| List Models API | 列出模型的 HTTP 端点 | `GET /v1/models` |
| 认证方式 | Header 名称和格式 | `Authorization: Bearer sk-xxx` 或 `x-api-key: xxx` |
| 响应 JSON 结构 | 模型列表在哪个字段、模型 ID 字段名 | `{"data":[{"id":"gpt-4o"}]}` 或 `{"models":[{"name":"llama3"}]}` |
| 额外 Header | 是否需要固定 Header（如 API 版本） | `anthropic-version: 2023-06-01` |

用 `curl` 快速验证 API 是否可达：

```bash
curl -s <list-models-url> -H "<auth-header>: <api-key>" | jq .
```

### Step 2 — 实现 Adapter

在 `hify-provider/src/main/java/com/hify/modules/provider/infra/adapter/` 下新建 `XxxAdapter.java`。

**情况 A：API 与 OpenAI 兼容（推荐先检查）**

只需继承 `OpenAiAdapter`，覆盖 `getType()`：

```java
package com.hify.modules.provider.infra.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.http.LlmHttpClient;
import org.springframework.stereotype.Component;

@Component
public class DeepSeekAdapter extends OpenAiAdapter {

    public DeepSeekAdapter(LlmHttpClient llmHttpClient, ObjectMapper objectMapper) {
        super(llmHttpClient, objectMapper);
    }

    @Override
    public String getType() {
        return "deepseek";
    }
}
```

判断"OpenAI 兼容"的条件：
- List Models API: `GET /v1/models`
- 认证头: `Authorization: Bearer <key>`
- 响应格式: `{"data": [{"id": "model-name"}, ...]}`

**情况 B：API 不同，完整实现 `ProviderAdapter` 接口**

需要实现 3 个方法：

1. `getType()` → 返回类型标识字符串
2. `testConnection(provider)` → 连通性测试（模板代码 + 差异化 3 步）
3. `listModels(provider)` → 列出模型名称

`testConnection` 是模板方法，从 `OpenAiAdapter` 复制骨架，只改 3 个步骤：

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiAdapter implements ProviderAdapter {

    private final LlmHttpClient llmHttpClient;
    private final ObjectMapper objectMapper;

    @Override
    public String getType() {
        return "gemini";
    }

    @Override
    public ConnectionTestResult testConnection(ProviderPo provider) {
        // --- 第 1 步：改 URL 构建 ---
        String url = buildTestUrl(provider.getBaseUrl());
        // --- 第 2 步：改 Header 构建 ---
        Map<String, String> headers = buildHeaders(provider.getAuthConfig());

        // ⬇ 以下模板代码不变 ⬇
        long start = System.currentTimeMillis();
        try {
            String responseBody = llmHttpClient.get(url, headers, 10);
            long latencyMs = System.currentTimeMillis() - start;
            // --- 第 3 步：改响应解析 ---
            int modelCount = parseModelCount(responseBody);
            log.info("Connection test OK: provider={}, latency={}ms, models={}", provider.getName(), latencyMs, modelCount);
            return ConnectionTestResult.success(latencyMs, modelCount);
        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - start;
            log.warn("Connection test failed: provider={}, latency={}ms, error={}", provider.getName(), latencyMs, e.getMessage());
            return ConnectionTestResult.fail(e.getMessage());
        }
        // ⬆ 模板代码结束 ⬆
    }

    @Override
    public List<String> listModels(ProviderPo provider) {
        String url = buildTestUrl(provider.getBaseUrl());
        Map<String, String> headers = buildHeaders(provider.getAuthConfig());
        String responseBody = llmHttpClient.get(url, headers, 10);
        return parseModelNames(responseBody);
    }

    // ===== 以下 4 个 private 方法是真正的差异化逻辑 =====

    private String buildTestUrl(String baseUrl) {
        return stripTrailingSlash(baseUrl) + "/v1/models";  // 按实际 API 改
    }

    private Map<String, String> buildHeaders(AuthConfig authConfig) {
        Map<String, String> headers = new HashMap<>();
        String apiKey = authConfig != null ? authConfig.getApiKey() : null;
        if (apiKey != null && !apiKey.isBlank()) {
            headers.put("x-goog-api-key", apiKey);  // 按实际 API 改
        }
        return headers;
    }

    private int parseModelCount(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode models = root.get("models");  // 按实际 JSON 结构改
            return models != null ? models.size() : 0;
        } catch (Exception e) {
            log.warn("Failed to parse model count from response", e);
            return 0;
        }
    }

    private List<String> parseModelNames(String responseBody) {
        List<String> names = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode models = root.get("models");  // 按实际 JSON 结构改
            if (models != null) {
                for (JsonNode node : models) {
                    if (node.has("name")) {  // 按实际字段名改
                        names.add(node.get("name").asText());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse model names from response", e);
        }
        return names;
    }

    private String stripTrailingSlash(String url) {
        if (url != null && url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }
}
```

**差异化清单**（从 `OpenAiAdapter` 复制后只需改这些）：

| 方法 | 改什么 |
|------|--------|
| `buildTestUrl` | API 路径，如 `/v1/models` → `/api/tags` |
| `buildHeaders` | 认证 Header 名称，如 `Authorization: Bearer` → `x-api-key`；有无固定 Header |
| `parseModelCount` | JSON 路径，如 `root.get("data")` → `root.get("models")` |
| `parseModelNames` | JSON 路径 + 模型 ID 字段名，如 `node.get("id")` → `node.get("name")` |

### Step 3 — 编译验证

Adapter 加了 `@Component` 后，Spring Boot 启动时会自动扫描并注册。只需编译通过即可。

```bash
mvn compile -pl hify-provider -am -q
```

如果编译报错：
- 检查 import 是否完整（`AuthConfig`、`ConnectionTestResult`、`LlmHttpClient`、`ObjectMapper`、`JsonNode`）
- 检查依赖注入：`LlmHttpClient` 和 `ObjectMapper` 是 Spring Bean，构造注入即可
- 如果继承了 `OpenAiAdapter`，构造器必须显式调用 `super(llmHttpClient, objectMapper)`

### Step 4 — 编写测试（推荐）

在 `src/test/java/com/hify/modules/provider/infra/adapter/` 下新建 `XxxAdapterTest.java`：

```java
@ExtendWith(MockitoExtension.class)
class GeminiAdapterTest {

    @Mock
    private LlmHttpClient llmHttpClient;

    private GeminiAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new GeminiAdapter(llmHttpClient, new ObjectMapper());
    }

    @Test
    void shouldBuildCorrectUrl() {
        ProviderPo provider = new ProviderPo();
        provider.setBaseUrl("https://api.google.com/");
        provider.setAuthConfig(new AuthConfig());

        when(llmHttpClient.get(eq("https://api.google.com/v1/models"), any(), eq(10L)))
                .thenReturn("{\"models\":[{\"name\":\"gemini-pro\"}]}");

        ConnectionTestResult result = adapter.testConnection(provider);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getModelCount()).isEqualTo(1);
    }

    @Test
    void shouldHandleAuthHeader() {
        ProviderPo provider = new ProviderPo();
        provider.setBaseUrl("https://api.google.com");
        AuthConfig auth = new AuthConfig();
        auth.setApiKey("google-key");
        provider.setAuthConfig(auth);

        when(llmHttpClient.get(any(), any(), eq(10L)))
                .thenReturn("{\"models\":[]}");

        adapter.testConnection(provider);

        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(llmHttpClient).get(any(), captor.capture(), eq(10L));
        assertThat(captor.getValue()).containsEntry("x-goog-api-key", "google-key");
    }

    @Test
    void shouldReturnFailureOnException() {
        ProviderPo provider = new ProviderPo();
        provider.setBaseUrl("https://api.google.com");
        provider.setAuthConfig(new AuthConfig());

        when(llmHttpClient.get(any(), any(), eq(10L)))
                .thenThrow(new RuntimeException("timeout"));

        ConnectionTestResult result = adapter.testConnection(provider);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isEqualTo("timeout");
    }
}
```

### Step 5 — 启动验证

启动应用，通过 API 创建一个新类型的 Provider 并测试连接：

```bash
# 启动后端
java -jar hify-app/target/hify-app-0.0.1-SNAPSHOT.jar --spring.profiles.active=mock

# 创建 Provider
curl -s -X POST http://localhost:8080/api/v1/providers \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "Gemini",
    "type": "gemini",
    "baseUrl": "https://generativelanguage.googleapis.com",
    "enabled": 1,
    "authConfig": {"authType": "bearer", "apiKey": "<your-key>"}
  }' | jq .

# 测试连通性（用返回的 id）
curl -s -X POST http://localhost:8080/api/v1/providers/1/test-connection | jq .
```

预期响应：
```json
{"success": true, "latencyMs": 230, "modelCount": 5}
```

## 参考：现有 Adapter 差异对比

| Adapter | `getType()` | URL | Auth Header | 响应模型字段 | 模型 ID 字段 |
|---------|-------------|-----|-------------|-------------|-------------|
| OpenAiAdapter | `openai` | `/v1/models` | `Authorization: Bearer <key>` | `data` | `id` |
| AnthropicAdapter | `anthropic` | `/v1/models` | `x-api-key: <key>` + `anthropic-version` | `data` | `id` |
| OllamaAdapter | `ollama` | `/api/tags` | 无 | `models` | `name` |
| OpenAiCompatibleAdapter | `openai_compatible` | 继承自 OpenAiAdapter | 继承 | 继承 | 继承 |

## 常见坑

| 现象 | 原因 | 修复 |
|------|------|------|
| 编译报错找不到符号 | 缺少 import（AuthConfig、JsonNode 等） | 从 OpenAiAdapter 复制完整 import 块 |
| `getType()` 返回的类型名和数据库存的 `type` 不一致 | 大小写或拼写差异 | `getType()` 返回值必须与数据库 `type` 字段完全一致（忽略大小写，Factory 做了 `toLowerCase`） |
| 继承 OpenAiAdapter 编译报错 | Lombok `@RequiredArgsConstructor` 不生成父类构造调用 | 手动写构造器并在其中 `super(llmHttpClient, objectMapper)` |
| 连接测试返回 401 | API Key 未传或 Header 名不对 | 检查 `buildHeaders` 中的 Header 名称和 Key 来源 |
| 连接测试返回失败但 curl 正常 | 响应 JSON 路径不匹配 | 用 curl 打印响应，对比 `parseModelCount` 中的 JSON 路径 |
| 新增 Adapter 后测试不生效 | 忘记加 `@Component` | 检查类上是否有 `@Component`，否则 Spring 不会扫描到 |
