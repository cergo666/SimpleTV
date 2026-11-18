plugins {
    java
}

dependencies {
    implementation("com.android.tools.build:gradle-api:${libs.versions.agp.get()}") {
        exclude(group = "com.android.tools.build", module = "gradle")
    }
    implementation("org.ow2.asm:asm:9.9")
}
