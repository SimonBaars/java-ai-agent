package com.ai.agent.examples;

import com.ai.agent.Agent;
import com.ai.agent.OpenAIAgent;

import java.lang.reflect.Method;
import java.util.concurrent.ExecutionException;

/**
 * A more complex example demonstrating how to use the AI agent with reflection-based function calling.
 */
public class ReflectionCalculator {
    private double memory = 0.0;

    public double add(double a, double b) {
        return a + b;
    }

    public double subtract(double a, double b) {
        return a - b;
    }

    public double multiply(double a, double b) {
        return a * b;
    }

    public double divide(double a, double b) {
        if (b == 0) {
            throw new IllegalArgumentException("Cannot divide by zero");
        }
        return a / b;
    }

    public double getMemory() {
        return memory;
    }

    public void setMemory(double value) {
        this.memory = value;
    }

    public static void main(String[] args) throws ExecutionException, InterruptedException {
        // Get API key from environment variable
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            System.err.println("Please set the OPENAI_API_KEY environment variable");
            System.exit(1);
        }

        // Create calculator instance and agent
        ReflectionCalculator calculator = new ReflectionCalculator();
        Agent agent = new OpenAIAgent(apiKey, "gpt-3.5-turbo");

        // Register calculator methods using reflection
        for (Method method : ReflectionCalculator.class.getDeclaredMethods()) {
            if (method.getParameterCount() <= 2 && !method.getName().equals("main")) {
                agent.registerFunction(method.getName(), params -> {
                    try {
                        if (method.getParameterCount() == 2) {
                            double a = ((Number) params.get("a")).doubleValue();
                            double b = ((Number) params.get("b")).doubleValue();
                            return method.invoke(calculator, a, b);
                        } else if (method.getParameterCount() == 1) {
                            double value = ((Number) params.get("value")).doubleValue();
                            return method.invoke(calculator, value);
                        } else {
                            return method.invoke(calculator);
                        }
                    } catch (Exception e) {
                        throw new RuntimeException("Error calling method: " + method.getName(), e);
                    }
                });
            }
        }

        // Example conversation with the agent
        String[] questions = {
            "What is 15 divided by 3?",
            "Can you subtract 7 from 20?",
            "Store the number 42 in memory",
            "What number is currently stored in memory?",
            "Take the number in memory and multiply it by 2",
            "What is 1 divided by 0?"
        };

        for (String question : questions) {
            try {
                System.out.println("\nQuestion: " + question);
                String response = agent.sendMessage(question).get();
                System.out.println("Answer: " + response);
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }
} 