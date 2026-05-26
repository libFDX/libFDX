plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
    maven {
        url = uri("http://teavm.org/maven/repository/")
        isAllowInsecureProtocol = true
    }
}

dependencies {
    implementation(libs.teavm.gradle.plugin)
}

gradlePlugin {
    plugins {
        create("libfdxPlatform") {
            id = "io.github.libfdx.platform"
            implementationClass = "io.github.libfdx.platform.gradle.LibfdxPlatformGradlePlugin"
        }
    }
}
