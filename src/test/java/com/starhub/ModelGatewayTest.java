package com.starhub;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ModelGatewayTest {
    ObjectNode request(String provider) {
        ObjectNode r = ModelGateway.JSON.createObjectNode().put("provider", provider).put("model", ModelGateway.PROVIDERS.containsKey(provider)?ModelGateway.PROVIDERS.get(provider).example:"test-model").put("apiKey", "test-key").put("system", "中文回答");
        r.putArray("messages").addObject().put("role", "user").put("content", "你好");
        return r;
    }
    ModelGateway.Reply reply(int status, String text) { return new ModelGateway.Reply(status, text.getBytes(StandardCharsets.UTF_8)); }
    @Test void compatibleProvidersPreserveConversationAndNeverUseUserEndpoint() throws Exception {
        for (ModelGateway.Provider p : ModelGateway.PROVIDERS.values()) {
            if (!p.protocol.equals("openai")) continue;
            ObjectNode request = request(p.id);
            request.put("baseUrl", "http://127.0.0.1:3306");
            ((ArrayNode) request.path("messages")).addObject().put("role", "assistant").put("content", "你好！");
            ((ArrayNode) request.path("messages")).addObject().put("role", "user").put("content", "继续");
            ModelGateway g = new ModelGateway((uri, headers, bytes) -> {
                assertEquals(p.base + "/chat/completions", uri.toString());
                assertEquals("Bearer test-key", headers.get("Authorization"));
                JsonNode body = ModelGateway.JSON.readTree(bytes);
                assertEquals(4, body.path("messages").size());
                assertEquals("system", body.path("messages").get(0).path("role").asText());
                assertEquals("你好！", body.path("messages").get(2).path("content").asText());
                assertEquals(request.path("model").asText(), body.path("model").asText());
                return reply(200, "{\"choices\":[{\"message\":{\"content\":\"回答\"}}],\"usage\":{\"total_tokens\":10}}");
            });
            ObjectNode result = g.chat(request);
            assertEquals("回答", result.path("text").asText());
            assertEquals(10, result.path("usage").path("total_tokens").asInt());
        }
    }
    @Test void anthropicSeparatesSystemAndExtractsTextOnly() throws Exception {
        ModelGateway g = new ModelGateway((uri, headers, bytes) -> {
            assertEquals("https://api.anthropic.com/v1/messages", uri.toString());
            assertEquals("test-key", headers.get("x-api-key"));
            assertEquals("2023-06-01", headers.get("anthropic-version"));
            JsonNode body = ModelGateway.JSON.readTree(bytes);
            assertEquals("中文回答", body.path("system").asText());
            assertEquals(4096, body.path("max_tokens").asInt());
            assertEquals("user", body.path("messages").get(0).path("role").asText());
            return reply(200, "{\"content\":[{\"type\":\"thinking\",\"thinking\":\"private\"},{\"type\":\"text\",\"text\":\"hello\"}]}");
        });
        assertEquals("hello", g.chat(request("anthropic")).path("text").asText());
    }
    @Test void geminiMapsRolesAndOmitsThoughts() throws Exception {
        ObjectNode req = request("gemini");
        ((ArrayNode) req.path("messages")).addObject().put("role", "assistant").put("content", "你好！");
        ((ArrayNode) req.path("messages")).addObject().put("role", "user").put("content", "继续");
        ModelGateway g = new ModelGateway((uri, headers, bytes) -> {
            assertEquals("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent", uri.toString());
            assertEquals("test-key", headers.get("x-goog-api-key"));
            assertNull(uri.getQuery());
            JsonNode body = ModelGateway.JSON.readTree(bytes);
            assertEquals("model", body.path("contents").get(1).path("role").asText());
            assertEquals("中文回答", body.path("systemInstruction").path("parts").get(0).path("text").asText());
            return reply(200, "{\"candidates\":[{\"content\":{\"parts\":[{\"thought\":true,\"text\":\"private\"},{\"text\":\"hello\"}]}}]}");
        });
        assertEquals("hello", g.chat(req).path("text").asText());
    }
    @Test void malformedInputsAreRejectedBeforeAnyNetworkRequest() {
        AtomicInteger calls = new AtomicInteger();
        ModelGateway g = new ModelGateway((u,h,b) -> { calls.incrementAndGet(); return reply(200,"{}"); });
        ObjectNode r = request("unknown"); assertThrows(ModelGateway.Problem.class, () -> g.chat(r));
        r.put("provider", "openai").put("apiKey", "secret\r\nInjected: header"); assertThrows(ModelGateway.Problem.class, () -> g.chat(r));
        r.put("apiKey", "test").put("model", "../bad?key=abc"); assertThrows(ModelGateway.Problem.class, () -> g.chat(r));
        r.put("model", "good").put("system", "x".repeat(8001)); assertThrows(ModelGateway.Problem.class, () -> g.chat(r));
        r.put("system", ""); r.putArray("messages"); assertThrows(ModelGateway.Problem.class, () -> g.chat(r));
        assertEquals(0, calls.get());
    }
    @Test void upstreamFailuresAndRedirectsNeverDiscloseRawBodies() {
        for (int status : new int[]{301,400,401,403,404,422,429,500}) {
            ModelGateway g = new ModelGateway((u,h,b) -> reply(status, "credential-should-never-appear"));
            ModelGateway.Problem problem = assertThrows(ModelGateway.Problem.class, () -> g.chat(request("openai")));
            assertEquals(502, problem.status);
            assertFalse(problem.getMessage().contains("credential-should-never-appear"));
        }
    }
    @Test void emptyTextIsReportedAsFailure() {
        ModelGateway g = new ModelGateway((u,h,b) -> reply(200, "{\"choices\":[]}"));
        assertThrows(ModelGateway.Problem.class, () -> g.chat(request("openai")));
    }
}
