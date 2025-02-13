package com.simonbrs.aiagent.examples;

import com.simonbrs.aiagent.OpenAIAgent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.stream.Collectors;
import java.text.NumberFormat;
import java.util.Locale;

public class AdvancedCalculator {
    private final OpenAIAgent agent;
    private final Map<String, Double> memory = new HashMap<>();

    public AdvancedCalculator(String apiKey) {
        this.agent = new OpenAIAgent(apiKey, "gpt-4o");
        registerMethods();
    }

    private void registerMethods() {
        agent.registerMethods(this);
    }

    // Basic arithmetic operations
    public double add(double a, double b) { return a + b; }
    public double subtract(double a, double b) { return a - b; }
    public double multiply(double a, double b) { return a * b; }
    public double divide(double a, double b) { return a / b; }

    // Advanced operations
    public double power(double base, double exponent) { return Math.pow(base, exponent); }
    public double sqrt(double number) { return Math.sqrt(number); }
    public double factorial(double n) {
        if (n < 0) throw new IllegalArgumentException("Factorial not defined for negative numbers");
        if (n == 0 || n == 1) return 1;
        return n * factorial(n - 1);
    }

    // Memory operations
    public void storeInMemory(String key, double value) { 
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("Memory key cannot be null or empty");
        }
        memory.put(key.trim(), value); 
    }
    
    public double recallFromMemory(String key) { 
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("Memory key cannot be null or empty");
        }
        return memory.getOrDefault(key.trim(), 0.0); 
    }
    
    public void clearMemory(String key) { 
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("Memory key cannot be null or empty");
        }
        memory.remove(key.trim()); 
    }
    
    public String listMemory() { 
        return memory.isEmpty() ? "Memory is empty" : memory.toString(); 
    }

    // Unit conversions
    public double celsiusToFahrenheit(double celsius) { return celsius * 9/5 + 32; }
    public double fahrenheitToCelsius(double fahrenheit) { return (fahrenheit - 32) * 5/9; }
    public double kilometersToMiles(double km) { return km * 0.621371; }
    public double milesToKilometers(double miles) { return miles / 0.621371; }

    // String manipulations
    public String numberToWords(double number) {
        String[] units = {"zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten"};
        int n = (int) number;
        if (n >= 0 && n <= 10) {
            return units[n];
        }
        return String.valueOf(n);
    }
    public String formatCurrency(double amount) {
        return NumberFormat.getCurrencyInstance(Locale.US).format(amount);
    }

    // Statistical analysis
    public String analyzeNumbers(String input) {
        List<Double> numbers;
        if (input.startsWith("[") && input.endsWith("]")) {
            // Handle JSON array input
            try {
                ObjectMapper mapper = new ObjectMapper();
                numbers = mapper.readValue(input, new TypeReference<List<Double>>() {});
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid JSON array format: " + input);
            }
        } else {
            // Handle comma-separated string input
            numbers = Arrays.stream(input.split(","))
                    .map(String::trim)
                    .map(Double::parseDouble)
                    .collect(Collectors.toList());
        }

        double sum = numbers.stream().mapToDouble(Double::doubleValue).sum();
        double mean = sum / numbers.size();
        double median = getMedian(numbers);
        double mode = getMode(numbers);

        return String.format("Analysis: Mean=%.2f, Median=%.2f, Mode=%.2f", mean, median, mode);
    }

    private double getMedian(List<Double> numbers) {
        List<Double> sorted = new ArrayList<>(numbers);
        Collections.sort(sorted);
        int middle = sorted.size() / 2;
        if (sorted.size() % 2 == 0) {
            return (sorted.get(middle - 1) + sorted.get(middle)) / 2;
        }
        return sorted.get(middle);
    }

    private double getMode(List<Double> numbers) {
        Map<Double, Long> frequency = new HashMap<>();
        for (Double num : numbers) {
            frequency.merge(num, 1L, Long::sum);
        }
        return frequency.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(0.0);
    }

    // Sequence generation
    public String generateSequence(String type, int length) {
        if (type.equalsIgnoreCase("fibonacci")) {
            return generateFibonacci(length);
        } else if (type.equalsIgnoreCase("prime")) {
            return generatePrimes(length);
        }
        return "Unknown sequence type. Supported types: fibonacci, prime";
    }

    public String generateFibonacci(int length) {
        List<Integer> sequence = new ArrayList<>();
        if (length >= 1) sequence.add(0);
        if (length >= 2) sequence.add(1);
        for (int i = 2; i < length; i++) {
            sequence.add(sequence.get(i-1) + sequence.get(i-2));
        }
        return sequence.toString();
    }

    public String generatePrimes(int length) {
        List<Integer> primes = new ArrayList<>();
        int num = 2;
        while (primes.size() < length) {
            if (isPrime(num)) {
                primes.add(num);
            }
            num++;
        }
        return primes.toString();
    }

    public boolean isPrime(double number) {
        if (number <= 1) return false;
        int n = (int) number;
        for (int i = 2; i <= Math.sqrt(n); i++) {
            if (n % i == 0) return false;
        }
        return true;
    }

    public static void main(String[] args) {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("Please set OPENAI_API_KEY environment variable");
            System.exit(1);
        }

        AdvancedCalculator calculator = new AdvancedCalculator(apiKey);

        // Example questions to test various functionalities
        String[] questions = {
            "What is the square root of 16?",
            "Calculate 5 factorial",
            "What is 2 to the power of 8?",
            "Solve the quadratic equation: 1x² - 5x + 6",
            "Analyze these numbers: 2, 4, 4, 4, 5, 5, 7",
            "Generate a Fibonacci sequence of length 8",
            "Generate a prime sequence of length 5",
            "Store 42 in memory with key 'answer'",
            "What is the value stored with key 'answer'?",
            "Show all values in memory",
            "Convert 25 Celsius to Fahrenheit",
            "How many miles is 10 kilometers?",
            "Format the phone number: country code 1, area code 555, number 0123",
            "Convert 7 to words",
            "Format 1234.5678 as currency"
        };

        for (String question : questions) {
            try {
                System.out.println("\nQuestion: " + question);
                String response = calculator.agent.sendMessage(question).get();
                System.out.println("Response: " + response);
            } catch (Exception e) {
                System.err.println("Error processing question: " + question);
                e.printStackTrace();
            }
        }
    }
}