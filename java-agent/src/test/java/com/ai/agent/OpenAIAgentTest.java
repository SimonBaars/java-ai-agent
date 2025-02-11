package com.ai.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

class OpenAIAgentTest {
    private static final String API_KEY = System.getenv("OPENAI_API_KEY");
    private static final String MODEL = "gpt-3.5-turbo";
    private OpenAIAgent agent;

    @BeforeEach
    void setUp() {
        if (API_KEY != null) {
            agent = new OpenAIAgent(API_KEY, MODEL);
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
    void testBasicConversation() throws ExecutionException, InterruptedException {
        CompletableFuture<String> response = agent.sendMessage("What is 2+2?");
        String result = response.get();
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
    void testConversationWithContext() throws ExecutionException, InterruptedException {
        Map<String, Object> context = new HashMap<>();
        context.put("language", "English");
        context.put("style", "concise");

        CompletableFuture<String> response = agent.sendMessage("What is the capital of France?", context);
        String result = response.get();
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertTrue(result.toLowerCase().contains("paris"));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
    void testFunctionRegistration() {
        AgentFunction testFunction = parameters -> "test result";
        agent.registerFunction("test", testFunction);
        // Function registration doesn't throw an exception
        assertTrue(true);
    }

    @Test
    void testMissingApiKey() {
        assertThrows(IllegalArgumentException.class, () -> new OpenAIAgent(null, MODEL));
    }
} 