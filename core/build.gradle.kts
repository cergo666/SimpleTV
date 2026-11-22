plugins {
    alias(libs.plugins.kotlin.jvm)
    // AGP is already on the build classpath through :app; the lint plugin must not pin a second version.
    id("com.android.lint")
    alias(libs.plugins.ktlint)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xstring-concat=inline")
    }
}

dependencies {
    implementation(libs.coroutines.core)
    implementation(libs.json)
    testImplementation(kotlin("test"))
}
