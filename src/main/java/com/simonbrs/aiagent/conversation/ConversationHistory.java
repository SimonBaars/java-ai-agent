package com.simonbrs.aiagent.conversation;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

public class ConversationHistory {
    private final List<Map<String, Object>> messages;

    public ConversationHistory() {
        this.messages = new ArrayList<>();
    }

    public void addUserMessage(String content) {
        addMessage("user", content, null);
    }

    public void addAssistantMessage(String content, JsonNode toolCalls) {
        addMessage("assistant", content, toolCalls);
    }

    public void addToolResponse(String toolCallId, String content) {
        Map<String, Object> toolMessage = new HashMap<>();
        toolMessage.put("role", "tool");
        toolMessage.put("tool_call_id", toolCallId);
        toolMessage.put("content", content);
        messages.add(toolMessage);
    }

    public void clear() {
        messages.clear();
    }

    public List<Map<String, Object>> getMessages() {
        return Collections.unmodifiableList(messages);
    }

    private void addMessage(String role, String content, JsonNode toolCalls) {
        Map<String, Object> messageMap = new HashMap<>();
        messageMap.put("role", role);
        messageMap.put("content", content);
        if (toolCalls != null) {
            messageMap.put("tool_calls", toolCalls);
        }
        messages.add(messageMap);
    }
} 