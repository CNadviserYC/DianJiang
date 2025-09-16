// build.gradle.kts（项目根目录）

plugins {
    // Kotlin DSL 插件（可选）
    kotlin("jvm") version "1.9.25" apply false
    id("com.android.application") version "8.11.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.25" apply false
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}
