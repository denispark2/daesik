import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.13.0")
    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    // 개인 규칙 테스트용 폴더 (저장소 밖, gitignore). 없으면 그 테스트는 건너뜀
    systemProperty("personalDataDir", rootProject.file("../data/workout").absolutePath)
    testLogging { events("passed", "skipped", "failed"); showStandardStreams = true }
}
