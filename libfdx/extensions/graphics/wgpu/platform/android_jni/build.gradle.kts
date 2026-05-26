plugins {
    alias(libs.plugins.android.library)
}

val androidCompileSdkVersion = providers.gradleProperty("androidCompileSdk").get().toInt()
val androidMinSdkVersion = providers.gradleProperty("androidMinSdk").get().toInt()

group = "io.github.libfdx.wgpu"

android {
    namespace = "io.github.libfdx.graphics.wgpu.android"
    compileSdk = androidCompileSdkVersion

    defaultConfig {
        minSdk = androidMinSdkVersion
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

base {
    archivesName.set("wgpu_android_jni")
}

dependencies {
    api(project(":libfdx:extensions:graphics:wgpu:core"))
    api(libs.jwebgpu.android)
}
