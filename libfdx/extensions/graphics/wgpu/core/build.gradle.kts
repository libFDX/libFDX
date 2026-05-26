plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

group = "io.github.libfdx.wgpu"

base {
    archivesName.set("wgpu_core")
}

dependencies {
    api(project(":libfdx:foundation:core"))
    api(project(":libfdx:graphics:api"))
    compileOnlyApi(libs.jwebgpu.core)
    compileOnly(libs.jwebgpu.jni)
}
