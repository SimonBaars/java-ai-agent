package com.simonbrs.aiagent.prompt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.simonbrs.aiagent.AgentFunction;
import java.util.Map;

public class SystemPromptGenerator {
    public String generatePrompt(Map<String, AgentFunction> functions, Map<String, ObjectNode> schemas) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a helpful assistant that can use provided functions. ");
        prompt.append("Please follow these guidelines when using functions:\n\n");
        
        appendGeneralGuidelines(prompt);
        appendFunctionGuidelines(prompt, functions, schemas);
        appendErrorHandlingGuidelines(prompt);
        
        return prompt.toString();
    }

    private void appendGeneralGuidelines(StringBuilder prompt) {
        prompt.append("1. Always provide ALL required arguments according to the function schema\n");
        prompt.append("2. Use the correct data types for arguments:\n");
        prompt.append("   - For numbers: Use numeric values without quotes\n");
        prompt.append("   - For strings: Use quoted text\n");
        prompt.append("   - For arrays: Use JSON array format\n");
        prompt.append("   - For booleans: Use true or false\n\n");
    }

    private void appendFunctionGuidelines(StringBuilder prompt, 
                                        Map<String, AgentFunction> functions, 
                                        Map<String, ObjectNode> schemas) {
        prompt.append("3. Function-specific guidelines:\n");
        for (Map.Entry<String, AgentFunction> entry : functions.entrySet()) {
            String functionName = entry.getKey();
            ObjectNode schema = schemas.get(functionName);
            
            if (schema != null) {
                prompt.append(String.format("   - %s:\n", functionName));
                JsonNode properties = schema.path("properties");
                if (properties.isObject()) {
                    properties.fields().forEachRemaining(field -> {
                        String paramName = field.getKey();
                        JsonNode paramInfo = field.getValue();
                        String type = paramInfo.path("type").asText("any");
                        String description = paramInfo.path("description").asText("");
                        prompt.append(String.format("     * %s (%s): %s\n", 
                            paramName, type, description));
                    });
                }
            }
        }
    }

    private void appendErrorHandlingGuidelines(StringBuilder prompt) {
        prompt.append("\n4. Error handling:\n");
        prompt.append("   - If a function call fails, I will provide an error message\n");
        prompt.append("   - For numeric operations, ensure inputs are within valid ranges\n");
        prompt.append("   - For string operations, ensure inputs are properly formatted\n");
        prompt.append("   - For array operations, ensure array indices are valid\n");
        prompt.append("   - Handle null values appropriately\n");
    }
} 