package com.simonbrs.aiagent.examples;

import com.simonbrs.aiagent.Agent;
import com.simonbrs.aiagent.OpenAIAgent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.concurrent.ExecutionException;

/**
 * A simple example demonstrating how to use the AI agent with function calling.
 */
public class SimpleCalculator {
    public static void main(String[] args) throws ExecutionException, InterruptedException {
        // Get API key from environment variable
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            System.err.println("Please set the OPENAI_API_KEY environment variable");
            System.exit(1);
        }

        // Create an agent instance
        OpenAIAgent agent = new OpenAIAgent(apiKey, "gpt-4o");

        // Create JSON mapper for function schemas
        ObjectMapper mapper = new ObjectMapper();

        // Create schema for binary operations
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        
        ObjectNode properties = schema.putObject("properties");
        
        ObjectNode arg0 = properties.putObject("arg0");
        arg0.put("type", "number");
        arg0.put("description", "First operand for operation");
        
        ObjectNode arg1 = properties.putObject("arg1");
        arg1.put("type", "number");
        arg1.put("description", "Second operand for operation");
        
        ArrayNode required = schema.putArray("required");
        required.add("arg0");
        required.add("arg1");
        
        schema.put("additionalProperties", false);

        // Register a function for addition with schema
        agent.registerFunction("add", params -> {
            double x = ((Number) params.get("arg0")).doubleValue();
            double y = ((Number) params.get("arg1")).doubleValue();
            double result = x + y;
            if (Double.isInfinite(result)) {
                throw new IllegalArgumentException("Addition would result in infinity");
            }
            return result;
        }, schema);

        // Register a function for multiplication with schema
        agent.registerFunction("multiply", params -> {
            double x = ((Number) params.get("arg0")).doubleValue();
            double y = ((Number) params.get("arg1")).doubleValue();
            double result = x * y;
            if (Double.isInfinite(result)) {
                throw new IllegalArgumentException("Multiplication would result in infinity");
            }
            return result;
        }, schema);

        // Example conversation with the agent
        String[] questions = {
            "What is 5 plus 3?",
            "What is 7 times 6?",
            "If I have 4 apples and get 3 more, how many do I have?",
            "What is the product of 8 and 9?",
            "What happens if we multiply a very large number by itself?"
        };

        for (String question : questions) {
            System.out.println("\nQuestion: " + question);
            String response = agent.sendMessage(question).get();
            System.out.println("Answer: " + response);
        }
    }
} 