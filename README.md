# SessionCast Java Library

Java library for integrating with [SessionCast](https://sessioncast.io) - a real-time terminal sharing platform.

## Installation

### JitPack

Add JitPack repository and the dependency:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

// build.gradle.kts
dependencies {
    implementation("com.github.sessioncast.sessioncast-java:sessioncast-core:v1.0.0")

    // Or with Spring Boot
    implementation("com.github.sessioncast.sessioncast-java:sessioncast-spring-boot-starter:v1.0.0")
}
```

```xml
<!-- Maven -->
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.sessioncast.sessioncast-java</groupId>
    <artifactId>sessioncast-core</artifactId>
    <version>v1.0.0</version>
</dependency>
```

## Modules

| Module | Description |
|--------|-------------|
| `sessioncast-core` | Core library with WebSocket client, tmux control, screen capture |
| `sessioncast-spring-boot-starter` | Spring Boot auto-configuration |

> Spring AI 통합은 별도 레포 [sessioncast-spring-ai](https://github.com/sessioncast/sessioncast-spring-ai)를 참조하세요.

## Usage

### Standalone

```java
SessionCastClient client = SessionCastClient.builder()
    .relay("wss://relay.sessioncast.io/ws")
    .token("agt_your_token_here")
    .machineId("my-machine")
    .build();

// Connect to relay
client.connect().join();

// Create a session and run Claude Code
client.createSession("my-session", "/path/to/project")
    .thenRun(() -> client.sendKeys("my-session", "claude", true));

// Subscribe to screen updates
client.onScreen("my-session", data -> {
    System.out.println(data.content());
});

// Send keys to session
client.sendKeys("my-session", "ls -la", true);

// Cleanup
client.close();
```

### Spring Boot

```yaml
# application.yml
sessioncast:
  relay:
    url: wss://relay.sessioncast.io/ws
    token: ${SESSIONCAST_TOKEN}
  agent:
    machine-id: threadcast-server
    label: ThreadCast Server
    auto-connect: true
```

```java
@Service
public class TerminalService {

    private final SessionCastClient sessionCast;

    public TerminalService(SessionCastClient sessionCast) {
        this.sessionCast = sessionCast;
    }

    public void startTodoSession(String todoId) {
        String sessionName = "todo-" + todoId;
        sessionCast.createSession(sessionName, "/work/" + todoId)
            .thenRun(() -> sessionCast.sendKeys(sessionName, "claude", true));
    }

    public void sendToTodo(String todoId, String text) {
        sessionCast.sendKeys("todo-" + todoId, text, true);
    }
}
```

## Features

### TmuxController

Direct tmux session management:

```java
TmuxController tmux = new TmuxController();

// List sessions
List<TmuxSession> sessions = tmux.listSessions();

// Create session
tmux.createSession("my-session", "/home/user");

// Send keys
tmux.sendKeys("my-session", "echo hello", true);
tmux.sendKeysWithEnter("my-session", "ls -la");
tmux.sendSpecialKey("my-session", SpecialKey.CTRL_C);

// Capture screen
String screen = tmux.capturePane("my-session", true);  // with ANSI colors
```

### Screen Capture

Adaptive polling screen capture:

```java
ScreenCapture capture = new ScreenCapture(tmux);

// Configure intervals
capture.setActiveInterval(Duration.ofMillis(50))   // Fast when active
       .setIdleInterval(Duration.ofMillis(200))    // Slow when idle
       .setIdleThreshold(Duration.ofSeconds(2));   // Switch to idle after 2s

// Start capturing
capture.start("my-session", data -> {
    System.out.println("Screen: " + data.content());
    System.out.println("Compressed: " + data.isCompressed());
});

// Stop
capture.stop("my-session");
```

### Event System

Subscribe to various events:

```java
// Connection events
client.onConnect(() -> System.out.println("Connected!"));
client.onDisconnect(reason -> System.out.println("Disconnected: " + reason));

// Screen updates
client.onScreen("my-session", data -> {
    // Handle screen data
});

// Keys from remote (viewer)
client.onKeysReceived(event -> {
    System.out.println("Received keys: " + event.keys());
});

// Errors
client.onError(ex -> {
    System.err.println("Error: " + ex.getMessage());
});
```

## Building

```bash
# Build all modules
./gradlew build

# Install to local Maven repository
./gradlew publishToMavenLocal

# Skip tests
./gradlew build -x test
```

## Requirements

- Java 17+
- tmux installed on the host machine

## License

MIT License
