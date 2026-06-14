plugins {
    kotlin("jvm") version "2.4.0"
    `java-library`
    `maven-publish`
}

group = "com.soluna"
version = "0.3.1"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }

    withSourcesJar()
    withJavadocJar()
}

dependencies {
    api("org.openpnp:opencv:4.9.0-0")

    implementation("org.slf4j:slf4j-api:2.0.16")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

val appiumTest by sourceSets.creating {
    java.srcDir("src/appiumTest/kotlin")
    resources.srcDir("src/appiumTest/resources")
    compileClasspath += sourceSets.main.get().output + sourceSets.test.get().output
    runtimeClasspath += output + compileClasspath
}

configurations[appiumTest.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
configurations[appiumTest.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())

dependencies {
    add(appiumTest.implementationConfigurationName, "io.appium:java-client:10.1.1")
}

tasks.register<Test>("appiumTest") {
    description = "Runs Android Appium integration tests against a connected device."
    group = LifecycleBasePlugin.VERIFICATION_GROUP

    testClassesDirs = appiumTest.output.classesDirs
    classpath = appiumTest.runtimeClasspath
    shouldRunAfter(tasks.test)
    useJUnitPlatform()

    systemProperty("appium.serverUrl", findProperty("appiumServerUrl") ?: "http://127.0.0.1:4723")
    findProperty("appiumUdid")?.let { systemProperty("appium.udid", it) }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            groupId = "com.soluna"
            artifactId = "kt-visual"
            version = project.version.toString()
        }
    }

    repositories {
        maven {
            name = "localBuildRepo"
            url = uri(layout.buildDirectory.dir("repo"))
        }

        // 公司私服示例：
        // maven {
        //     name = "companyRepo"
        //     url = uri("https://nexus.example.com/repository/maven-releases/")
        //     credentials {
        //         username = findProperty("repoUser") as String
        //         password = findProperty("repoPassword") as String
        //     }
        // }
    }
}
