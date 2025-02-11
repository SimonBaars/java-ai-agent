# Java AI Agent Library

A Java library for creating AI agents that can interact with OpenAI's language models and execute Java functions via reflection.

## Features

- Easy integration with OpenAI's GPT models
- Asynchronous message handling using CompletableFuture
- Support for function registration and execution
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
OpenAIAgent agent = new OpenAIAgent(apiKey, "gpt-3.5-turbo");

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

### Registering Functions

```java
// Define a function
AgentFunction calculateSum = parameters -> {
    int a = ((Number) parameters.get("a")).intValue();
    int b = ((Number) parameters.get("b")).intValue();
    return a + b;
};

// Register the function
agent.registerFunction("calculateSum", calculateSum);
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