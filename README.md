# Java AI Agent Library

A Java library for creating AI agents that can interact with OpenAI's language models and execute Java functions via reflection.

## Features

- Easy integration with OpenAI's GPT models
- Asynchronous message handling using CompletableFuture
- Support for function registration and execution
- Automatic schema generation for Java methods
- Conversation history management
- Context-aware messaging

## Prerequisites

- Java 17 or higher
- Maven
- OpenAI API key

## Installation

Add the following dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>com.ai.agent</groupId>
    <artifactId>java-agent</artifactId>
    <version>1.0-SNAPSHOT</version>
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

### Manual Function Registration

```java
// Create a schema for the function
ObjectMapper mapper = new ObjectMapper();
ObjectNode schema = mapper.createObjectNode();
schema.put("type", "object");

ObjectNode properties = schema.putObject("properties");
ObjectNode arg0 = properties.putObject("arg0");
arg0.put("type", "number");
arg0.put("description", "First operand");

ObjectNode arg1 = properties.putObject("arg1");
arg1.put("type", "number");
arg1.put("description", "Second operand");

ArrayNode required = schema.putArray("required");
required.add("arg0");
required.add("arg1");

// Register a function with schema
agent.registerFunction("add", params -> {
    double x = ((Number) params.get("arg0")).doubleValue();
    double y = ((Number) params.get("arg1")).doubleValue();
    return x + y;
}, schema);
```

### Automatic Method Registration

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

// Register all methods automatically
Calculator calculator = new Calculator();
agent.registerMethods(calculator);
```

## Examples

The library includes two example applications:

1. `SimpleCalculator`: Demonstrates manual function registration with schemas
2. `ReflectionCalculator`: Shows automatic method registration using reflection

To run the examples:

```bash
export OPENAI_API_KEY=your_api_key_here
mvn exec:java -Dexec.mainClass="com.ai.agent.examples.SimpleCalculator"
mvn exec:java -Dexec.mainClass="com.ai.agent.examples.ReflectionCalculator"
```

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