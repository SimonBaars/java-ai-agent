# Java AI Agent Library

A Java library for creating AI agents that can interact with OpenAI's language models and execute Java functions:

```java
// Create a class with methods
public class Calculator {
    private double memory = 0.0;

    public double add(double a, double b) {
        return a + b;
    }

    public double getMemory() {
        return memory;
    }

    public void setMemory(double value) {
        this.memory = value;
    }
}

// Create an agent instance
String apiKey = System.getenv("OPENAI_API_KEY");
OpenAIAgent agent = new OpenAIAgent(apiKey, "gpt-4o");

// Register all methods automatically
Calculator calculator = new Calculator();
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
```

Output:
```
Question: What is 15 added to 3?
Tool called: add(15.0, 3.0)
Answer: 15 added to 3 is 18.0.

Question: Store the number 42 in memory
Tool called: setMemory(42.0)
Answer: The number 42 has been stored in memory.

Question: Add ten to the number stored in memory
Tool called: getMemory()
Tool called: add(42.0, 10.0)
Answer: The number stored in memory is 42. Adding ten to it results in 52.0.
```

No more extensive schema creation, no more manual API calls. Just a single `agent.registerMethods` to register the available tools, and the agent will handle the rest.

See the [ReadmeCalculator](src/main/java/com/simonbrs/aiagent/examples/ReadmeCalculator.java) for the runnable version of above example.

## Features

- Easy integration with OpenAI's GPT models
- Asynchronous message handling using CompletableFuture
- Support for function registration and execution
- Automatic schema generation for Java methods
- Conversation history management
- Context-aware messaging
- Tool chaining: The AI decides which tools to run to best answer the query

## Prerequisites

- Java 17 or higher
- Maven
- OpenAI API key

## Installation

Add the following dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>com.simonbrs</groupId>
    <artifactId>aiagent</artifactId>
    <version>1.2</version>
</dependency>
```

## Usage

### Basic Usage

```java
// Create an agent instance
String apiKey = System.getenv("OPENAI_API_KEY");
OpenAIAgent agent = new OpenAIAgent(apiKey, "gpt-4o");

// Send a message and get the response
CompletableFuture<String> response = agent.sendMessage("What is 2+2?");
String result = response.get();
System.out.println(result);
```

### Using Context

```java
Map<String, Object> context = new HashMap<>();
context.put("language", "English");
context.put("style", "concise");

CompletableFuture<String> response = agent.sendMessage("What is the capital of France?", context);
String result = response.get();
System.out.println(result);
```

## Examples

The library includes several example applications demonstrating different features:

1. `SimpleCalculator`: Demonstrates manual function registration with schemas. Shows how to:
   - Create and register functions with custom schemas
   - Handle basic arithmetic operations
   - Validate inputs and handle errors
   - Example: `mvn exec:java -Dexec.mainClass="com.simonbrs.aiagent.examples.SimpleCalculator"`

2. `ReflectionCalculator`: Shows automatic method registration using reflection. Features:
   - Automatic registration of all public methods
   - Memory operations (store/recall values)
   - Error handling for edge cases (division by zero, infinity)
   - Example: `mvn exec:java -Dexec.mainClass="com.simonbrs.aiagent.examples.ReflectionCalculator"`

3. `MultiToolCalculator`: Demonstrates multi-tool-call pipelines. Shows how to:
   - Chain multiple tool calls together
   - Mix text responses with calculations
   - Track state between operations
   - Provide step-by-step explanations
   - Example: `mvn exec:java -Dexec.mainClass="com.simonbrs.aiagent.examples.MultiToolCalculator"`

4. `AdvancedCalculator`: Showcases advanced mathematical and utility functions:
   - Advanced math operations (power, sqrt, factorial)
   - Statistical analysis (mean, median, mode)
   - Unit conversions (temperature, distance)
   - Sequence generation (Fibonacci, primes)
   - String formatting and number-to-words conversion
   - Multi-key memory operations
   - Example: `mvn exec:java -Dexec.mainClass="com.simonbrs.aiagent.examples.AdvancedCalculator"`

To run any example:

```bash
export OPENAI_API_KEY=your_api_key_here
mvn exec:java -Dexec.mainClass="com.simonbrs.aiagent.examples.$EXAMPLE_NAME"
```

Each example demonstrates different aspects of the library's capabilities. The `MultiToolCalculator` and `AdvancedCalculator` are particularly interesting as they show how the AI agent can:
- Chain multiple operations together
- Maintain state between operations
- Provide detailed explanations
- Handle complex multi-step calculations
- Mix natural language responses with computational results

See the individual example files for detailed documentation and usage scenarios.

## Building from Source

1. Clone the repository
2. Run `mvn clean install`

## Running Tests

```bash
export OPENAI_API_KEY=your_api_key_here
mvn test
```

## License

This project is licensed under the MIT License - see the LICENSE file for details.
