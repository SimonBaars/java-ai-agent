package com.simonbrs.aiagent.examples;

import com.simonbrs.aiagent.OpenAIAgent;
import java.util.concurrent.ExecutionException;

/**
 * Example demonstrating multiple tool calls and mixed responses from the AI agent.
 */
public class AdvancedMathOperations {
    private double memory = 0.0;
    private double lastResult = 0.0;

    public double add(double a, double b) {
        System.out.println("Adding " + a + " and " + b);
        double result = a + b;
        lastResult = result;
        return result;
    }

    public double multiply(double a, double b) {
        System.out.println("Multiplying " + a + " by " + b);
        double result = a * b;
        lastResult = result;
        return result;
    }

    public double power(double base, double exponent) {
        System.out.println("Raising " + base + " to the power of " + exponent);
        double result = Math.pow(base, exponent);
        if (Double.isInfinite(result)) {
            throw new IllegalArgumentException("Result would be infinite");
        }
        lastResult = result;
        return result;
    }

    public void storeInMemory(double value) {
        System.out.println("Storing " + value + " in memory");
        memory = value;
        lastResult = value;
    }

    public double getMemory() {
        System.out.println("Getting value from memory: " + memory);
        lastResult = memory;
        return memory;
    }

    public double getLastResult() {
        System.out.println("Getting last result: " + lastResult);
        return lastResult;
    }

    public double squareRoot(double value) {
        System.out.println("Calculating square root of " + value);
        if (value < 0) {
            throw new IllegalArgumentException("Cannot calculate square root of negative number");
        }
        double result = Math.sqrt(value);
        lastResult = result;
        return result;
    }

    public static void main(String[] args) throws ExecutionException, InterruptedException {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            System.err.println("Please set the OPENAI_API_KEY environment variable");
            System.exit(1);
        }

        AdvancedMathOperations math = new AdvancedMathOperations();
        OpenAIAgent agent = new OpenAIAgent(apiKey, "gpt-4");
        agent.registerMethods(math);

        // Complex questions that require multiple operations
        String[] questions = {
            "Calculate 5 plus 3, then multiply the result by 2, and finally store it in memory",
            "What's the square root of the number in memory? Also, tell me what the original number was.",
            "Take the last result, raise it to the power of 2, and add it to what's in memory. Show all steps.",
            "Here's a complex one: add 10 to the number in memory, take the square root, multiply it by 3, and explain each step.",
            "What was the final result of our last calculation? Also show what's stored in memory."
        };

        for (String question : questions) {
            try {
                System.out.println("\nQuestion: " + question);
                String response = agent.sendMessage(question).get();
                System.out.println("Response: " + response);
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
} 