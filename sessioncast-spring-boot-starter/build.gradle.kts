plugins {
    `java-library`
}

dependencies {
    api(project(":sessioncast-core"))

    // Spring Boot
    compileOnly("org.springframework.boot:spring-boot-autoconfigure:${property("springBootVersion")}")
    compileOnly("org.springframework.boot:spring-boot-configuration-processor:${property("springBootVersion")}")
    compileOnly("jakarta.annotation:jakarta.annotation-api:2.1.1")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:${property("springBootVersion")}")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test:${property("springBootVersion")}")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
}
