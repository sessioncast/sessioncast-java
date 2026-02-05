plugins {
    `java-library`
}

dependencies {
    // WebSocket
    api("org.java-websocket:Java-WebSocket:${property("websocketVersion")}")

    // JSON
    api("com.fasterxml.jackson.core:jackson-databind:${property("jacksonVersion")}")
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:${property("jacksonVersion")}")

    // Logging
    api("org.slf4j:slf4j-api:${property("slf4jVersion")}")

    // Test
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.assertj:assertj-core:3.24.2")
    testImplementation("org.mockito:mockito-core:5.8.0")
    testRuntimeOnly("ch.qos.logback:logback-classic:1.4.14")
}
