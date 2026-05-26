plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

base {
    archivesName.set("backend_desktop")
}

dependencies {
    val osName = System.getProperty("os.name").lowercase()
    val osArch = System.getProperty("os.arch").lowercase()
    val lwjglNatives = when {
        osName.contains("windows") -> "natives-windows"
        osName.contains("linux") -> "natives-linux"
        osName.contains("mac") && (osArch.contains("aarch64") || osArch.contains("arm64")) -> "natives-macos-arm64"
        osName.contains("mac") -> "natives-macos"
        else -> throw GradleException("Unsupported LWJGL native platform: $osName/$osArch")
    }

    api(project(":libfdx:foundation:core"))
    api(project(":libfdx:assets:manager"))
    api(project(":libfdx:runtime:application"))
    api(project(":libfdx:runtime:display"))
    api(project(":libfdx:runtime:files"))
    api(project(":libfdx:graphics:api"))
    api(project(":libfdx:extensions:graphics:gl:core"))
    api(project(":libfdx:extensions:graphics:vulkan:core"))

    implementation(libs.lwjgl)
    implementation(libs.lwjgl.glfw)
    compileOnly(libs.lwjgl.opengl)
    compileOnly(libs.lwjgl.vulkan)

    runtimeOnly(variantOf(libs.lwjgl) { classifier(lwjglNatives) })
    runtimeOnly(variantOf(libs.lwjgl.glfw) { classifier(lwjglNatives) })
}
