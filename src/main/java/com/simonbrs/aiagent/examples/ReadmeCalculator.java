package com.simonbrs.aiagent.examples;

import com.simonbrs.aiagent.OpenAIAgent;

public class ReadmeCalculator {
    // Create a class with methods
    public static class Calculator {
        private double memory = 0.0;

        public double add(double a, double b) {
            System.out.println("Tool called: add(" + a + ", " + b + ")");
            return a + b;
        }

        public double getMemory() {
            System.out.println("Tool called: getMemory()");
            return memory;
        }

        public void setMemory(double value) {
            System.out.println("Tool called: setMemory(" + value + ")");
            this.memory = value;
        }
    }

    public static void main(String[] args) {
        // Register all methods automatically
        Calculator calculator = new Calculator();
        String apiKey = System.getenv("OPENAI_API_KEY");
        OpenAIAgent agent = new OpenAIAgent(apiKey, "gpt-4o");
        agent.registerMethods(calculator);

        String[] questions = {
            "What is 15 added to 3?",
            "Store the number 42 in memory",
            "Add ten to the number stored in memory",
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
