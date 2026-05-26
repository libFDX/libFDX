import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    id("java")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

group = "io.github.libfdx.tests"

val openGlRuntimeClasspath by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val wgpuJniRuntimeClasspath by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val vulkanRuntimeClasspath by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val wgpuFfmRuntimeClasspath by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
    }
}

base {
    archivesName.set("tests_desktop")
}

dependencies {
    implementation(project(":tests:core"))
    implementation(project(":libfdx:backends:desktop"))
    implementation(project(":libfdx:extensions:graphics:wgpu:core"))

    openGlRuntimeClasspath(project(":libfdx:extensions:graphics:gl:platform:desktop"))
    vulkanRuntimeClasspath(project(":libfdx:extensions:graphics:vulkan:platform:desktop"))
    wgpuJniRuntimeClasspath(project(":libfdx:extensions:graphics:wgpu:platform:desktop_jni"))
    wgpuFfmRuntimeClasspath(project(":libfdx:extensions:graphics:wgpu:platform:desktop_ffm"))
}

val testLauncherMainClass = "io.github.libfdx.tests.desktop.DesktopTestLauncher"

fun JavaExec.configureTestRun(
    descriptionText: String,
    graphics: String,
    graphicsLabel: String,
    providerClasspath: FileCollection
) {
    group = "application"
    description = descriptionText
    classpath = sourceSets["main"].runtimeClasspath + providerClasspath
    mainClass.set(testLauncherMainClass)
    workingDir = rootProject.projectDir
    val testName = System.getProperty("libfdx.test.name")
    if (!testName.isNullOrBlank()) {
        systemProperty("libfdx.test.name", testName)
    }
    systemProperty("libfdx.test.graphics", graphics)
    systemProperty("libfdx.test.graphicsLabel", graphicsLabel)
    systemProperty("libfdx.test.frames", System.getProperty("libfdx.test.frames", "0"))
    systemProperty("libfdx.test.visible", System.getProperty("libfdx.test.visible", "true"))
    systemProperty("libfdx.test.vsync", System.getProperty("libfdx.test.vsync", "true"))
    systemProperty("libfdx.test.foregroundFps", System.getProperty("libfdx.test.foregroundFps", "60"))
    val capturePath = System.getProperty("libfdx.test.capture")
    if (!capturePath.isNullOrBlank()) {
        systemProperty("libfdx.test.capture", capturePath)
    }
    val captureEvery = System.getProperty("libfdx.test.captureEvery")
    if (!captureEvery.isNullOrBlank()) {
        systemProperty("libfdx.test.captureEvery", captureEvery)
    }
    jvmArgs("-Dorg.lwjgl.system.stackSize=1048576")
    if (JavaVersion.current().majorVersion.toInt() >= 22) {
        jvmArgs("--enable-native-access=ALL-UNNAMED")
    }
}

fun JavaExec.useJava25Launcher() {
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(25))
    })
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

fun JavaExec.useLwjglJniOnJava25() {
    useJava25Launcher()
    // LWJGL 3.4.x stores its FFM backend in Java 25 multi-release entries.
    jvmArgs("-Djdk.util.jar.enableMultiRelease=false")
}

fun JavaExec.configureReadbackRegressionRun() {
    systemProperty("libfdx.test.name", "readback")
    systemProperty("libfdx.test.frames", System.getProperty("libfdx.test.frames", "1"))
    systemProperty("libfdx.test.visible", System.getProperty("libfdx.test.visible", "false"))
}

fun currentLwjglBindingName(): String {
    val multiRelease = System.getProperty("jdk.util.jar.enableMultiRelease", "true")
    return if (JavaVersion.current().majorVersion.toInt() >= 25 && !multiRelease.equals("false", true)) {
        "FFM"
    } else {
        "JNI"
    }
}

tasks.register<JavaExec>("test_wgpu_jni") {
    configureTestRun("Runs graphics tests with WGPU JNI.", "wgpu", "WGPU JNI", wgpuJniRuntimeClasspath)
}

tasks.register<JavaExec>("test_wgpu_jni_readback") {
    configureTestRun("Runs the WGPU JNI framebuffer readback regression test.", "wgpu", "WGPU JNI",
            wgpuJniRuntimeClasspath)
    configureReadbackRegressionRun()
}

tasks.register<JavaExec>("test_wgpu_ffm") {
    configureTestRun("Runs graphics tests with WGPU FFM.", "wgpu", "WGPU FFM", wgpuFfmRuntimeClasspath)
    useJava25Launcher()
}

tasks.register<JavaExec>("test_wgpu_ffm_readback") {
    configureTestRun("Runs the WGPU FFM framebuffer readback regression test.", "wgpu", "WGPU FFM",
            wgpuFfmRuntimeClasspath)
    configureReadbackRegressionRun()
    useJava25Launcher()
}

tasks.register<JavaExec>("test_gl") {
    configureTestRun("Runs graphics tests with desktop OpenGL on the current JVM.",
            "opengl", "OpenGL LWJGL ${currentLwjglBindingName()}", openGlRuntimeClasspath)
}

tasks.register<JavaExec>("test_gl_ffm") {
    configureTestRun("Runs graphics tests with desktop OpenGL on Java 25 using LWJGL FFM.", "opengl", "OpenGL LWJGL FFM", openGlRuntimeClasspath)
    useJava25Launcher()
}

tasks.register<JavaExec>("test_gl_jni") {
    configureTestRun("Runs graphics tests with desktop OpenGL on Java 25 using LWJGL JNI.", "opengl", "OpenGL LWJGL JNI", openGlRuntimeClasspath)
    useLwjglJniOnJava25()
}

tasks.register<JavaExec>("test_vulkan") {
    configureTestRun("Runs graphics tests with desktop Vulkan on the current JVM.",
            "vulkan", "Vulkan LWJGL ${currentLwjglBindingName()}", vulkanRuntimeClasspath)
}

tasks.register<JavaExec>("test_vulkan_ffm") {
    configureTestRun("Runs graphics tests with desktop Vulkan on Java 25 using LWJGL FFM.", "vulkan", "Vulkan LWJGL FFM", vulkanRuntimeClasspath)
    useJava25Launcher()
}

tasks.register<JavaExec>("test_vulkan_jni") {
    configureTestRun("Runs graphics tests with desktop Vulkan on Java 25 using LWJGL JNI.", "vulkan", "Vulkan LWJGL JNI", vulkanRuntimeClasspath)
    useLwjglJniOnJava25()
}
