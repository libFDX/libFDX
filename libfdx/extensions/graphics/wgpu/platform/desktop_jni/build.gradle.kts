plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

base {
    archivesName.set("wgpu_desktop_jni")
}

dependencies {
    api(project(":libfdx:extensions:graphics:wgpu:core"))
    runtimeOnly(libs.jwebgpu.jni)
    runtimeOnly(libs.jwebgpu.jni.desktop)
}
