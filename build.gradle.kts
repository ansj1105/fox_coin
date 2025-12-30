import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    java
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("com.google.cloud.tools.jib") version "3.4.0"
}

group = "com.foxya"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val vertxVersion = "4.5.1"
val log4jVersion = "2.22.0"
val jacksonVersion = "2.16.0"
val junitVersion = "5.10.1"
val bcryptVersion = "0.10.2"
val jbcryptVersion = "0.4"
val postgresqlVersion = "42.7.1"
val assertjVersion = "3.25.1"
val lombokVersion = "1.18.30"
val mockitoVersion = "5.8.0"

val mainVerticleName = "com.foxya.coin.MainVerticle"
val launcherClassName = "io.vertx.core.Launcher"

val watchForChange = "src/**/*"
val doOnChange = "${projectDir}/gradlew classes"

application {
    mainClass.set(launcherClassName)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    // Vert.x Core
    implementation(platform("io.vertx:vertx-stack-depchain:$vertxVersion"))
    implementation("io.vertx:vertx-core")
    implementation("io.vertx:vertx-web")
    implementation("io.vertx:vertx-web-client")
    implementation("io.vertx:vertx-web-validation")
    implementation("io.vertx:vertx-json-schema")
    implementation("io.vertx:vertx-web-openapi")
    
    // Vert.x Config
    implementation("io.vertx:vertx-config")
    
    // Vert.x PostgreSQL Client
    implementation("io.vertx:vertx-pg-client")
    implementation("io.vertx:vertx-sql-client-templates")
    
    // Vert.x Auth JWT
    implementation("io.vertx:vertx-auth-jwt")
    
    // Vert.x Redis Client
    implementation("io.vertx:vertx-redis-client")
    
    // Vert.x Mail Client (SMTP)
    implementation("io.vertx:vertx-mail-client")
    
    // Vert.x Micrometer Metrics
    implementation("io.vertx:vertx-micrometer-metrics")
    implementation("io.micrometer:micrometer-registry-prometheus:1.12.0")
    
    // PostgreSQL JDBC (Flyway용)
    implementation("org.postgresql:postgresql:$postgresqlVersion")
    
    // PostgreSQL SCRAM 인증
    implementation("com.ongres.scram:client:2.1")
    
    // Flyway
    implementation("org.flywaydb:flyway-core:10.4.1")
    implementation("org.flywaydb:flyway-database-postgresql:10.4.1")
    
    // Log4j2
    implementation("org.apache.logging.log4j:log4j-api:$log4jVersion")
    implementation("org.apache.logging.log4j:log4j-core:$log4jVersion")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:$log4jVersion")
    
    // Jackson
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
    
    // BCrypt
    implementation("org.mindrot:jbcrypt:$jbcryptVersion")
    
    // Lombok
    compileOnly("org.projectlombok:lombok:$lombokVersion")
    annotationProcessor("org.projectlombok:lombok:$lombokVersion")
    testCompileOnly("org.projectlombok:lombok:$lombokVersion")
    testAnnotationProcessor("org.projectlombok:lombok:$lombokVersion")
    
    // Test
    testImplementation("io.vertx:vertx-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testImplementation("org.assertj:assertj-core:$assertjVersion")
    testImplementation("org.mockito:mockito-core:$mockitoVersion")
    testImplementation("org.mockito:mockito-junit-jupiter:$mockitoVersion")
}

tasks.withType<ShadowJar> {
    archiveClassifier.set("fat")
    manifest {
        attributes(mapOf(
            "Main-Verticle" to mainVerticleName,
            "Main-Class" to launcherClassName
        ))
    }
    mergeServiceFiles()
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

tasks.withType<JavaExec> {
    args = listOf(
        "run", 
        mainVerticleName,
        "--redeploy=$watchForChange",
        "--launcher-class=$launcherClassName",
        "--on-redeploy=$doOnChange"
    )
}

tasks.register("stage") {
    dependsOn("shadowJar")
}

// Jib 설정 (Docker 이미지 빌드)
jib {
    from {
        image = "eclipse-temurin:17-jre-alpine"
        platforms {
            platform {
                architecture = "amd64"
                os = "linux"
            }
        }
    }
    to {
        image = "foxya-coin-api"
        tags = setOf("latest", version.toString())
    }
    container {
        mainClass = launcherClassName
        jvmFlags = listOf(
            "-Xmx1024m",
            "-Xms512m", 
            "-XX:+UseG1GC",
            "-XX:MaxGCPauseMillis=200"
        )
        ports = listOf("8080")
        environment = mapOf(
            "TZ" to "Asia/Seoul"
        )
        creationTime.set("USE_CURRENT_TIMESTAMP")
    }
}
