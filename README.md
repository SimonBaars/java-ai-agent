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
    <groupId>com.simonbrs</groupId>
    <artifactId>aiagent</artifactId>
    <version>1.0</version>
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

String[] questions = {
    "What is 15 added to 3?",
    "Store the number 42 in memory",
    "What number is currently stored in memory?",
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

## Examples

The library includes two example applications:

1. `SimpleCalculator`: Demonstrates manual function registration with schemas
2. `ReflectionCalculator`: Shows automatic method registration using reflection

To run the examples:

```bash
export OPENAI_API_KEY=your_api_key_here
mvn exec:java -Dexec.mainClass="com.simonbrs.aiagent.examples.SimpleCalculator"
mvn exec:java -Dexec.mainClass="com.simonbrs.aiagent.examples.ReflectionCalculator"
```

## Building from Source

1. Clone the repository
2. Run `mvn clean install`

## Running Tests

```bash
export OPENAI_API_KEY=your_api_key_here
mvn test
```

## Publishing to Maven Central

To publish to Maven Central:

1. Create an account on [Sonatype JIRA](https://issues.sonatype.org/)
2. Create a JIRA ticket to request access to publish under `com.simonbrs`
3. Set up GPG:
   ```bash
   gpg --gen-key
   gpg --list-keys
   gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID
   ```
4. Configure your Maven settings (`~/.m2/settings.xml`):
   ```xml
   <settings>
     <servers>
       <server>
         <id>ossrh</id>
         <username>your-jira-username</username>
         <password>your-jira-password</password>
       </server>
     </servers>
     <profiles>
       <profile>
         <id>ossrh</id>
         <activation>
           <activeByDefault>true</activeByDefault>
         </activation>
         <properties>
           <gpg.executable>gpg</gpg.executable>
           <gpg.passphrase>your-gpg-passphrase</gpg.passphrase>
         </properties>
       </profile>
     </profiles>
   </settings>
   ```
5. Deploy:
   ```bash
   mvn clean deploy
   ```

## License

This project is licensed under the MIT License - see the LICENSE file for details.
