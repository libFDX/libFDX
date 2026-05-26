plugins {
    alias(libs.plugins.android.library)
}

val androidCompileSdkVersion = providers.gradleProperty("androidCompileSdk").get().toInt()
val androidMinSdkVersion = providers.gradleProperty("androidMinSdk").get().toInt()

android {
    namespace = "io.github.libfdx.backend.android"
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
    archivesName.set("backend_android")
}

dependencies {
    api(project(":libfdx:foundation:core"))
    api(project(":libfdx:assets:manager"))
    api(project(":libfdx:runtime:application"))
    api(project(":libfdx:runtime:display"))
    api(project(":libfdx:runtime:files"))
    api(project(":libfdx:graphics:api"))
    api(project(":libfdx:extensions:graphics:gl:core"))
    api(project(":libfdx:extensions:graphics:vulkan:core"))
}
