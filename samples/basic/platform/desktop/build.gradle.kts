import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    id("java")
}

group = "io.github.libfdx.samples.basic"

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

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
    archivesName.set("sample_basic_desktop")
}

dependencies {
    implementation(project(":samples:basic:core"))
    implementation(project(":libfdx:runtime:application"))
    implementation(project(":libfdx:runtime:display"))
    implementation(project(":libfdx:extensions:graphics:wgpu:core"))
    implementation(project(":libfdx:backends:desktop"))

    openGlRuntimeClasspath(project(":libfdx:extensions:graphics:gl:platform:desktop"))
    vulkanRuntimeClasspath(project(":libfdx:extensions:graphics:vulkan:platform:desktop"))
    wgpuJniRuntimeClasspath(project(":libfdx:extensions:graphics:wgpu:platform:desktop_jni"))
    wgpuFfmRuntimeClasspath(project(":libfdx:extensions:graphics:wgpu:platform:desktop_ffm"))
}

val sampleMainClass = "io.github.libfdx.samples.basic.desktop.BasicDesktopLauncher"

fun JavaExec.configureSampleRun(
    descriptionText: String,
    graphics: String,
    graphicsLabel: String,
    providerClasspath: FileCollection
) {
    group = "application"
    description = descriptionText
    classpath = sourceSets["main"].runtimeClasspath + providerClasspath
    mainClass.set(sampleMainClass)
    workingDir = rootProject.projectDir
    val exitAfterFrames = System.getProperty("libfdx.sample.exitAfterFrames")
    if (!exitAfterFrames.isNullOrBlank()) {
        systemProperty("libfdx.sample.exitAfterFrames", exitAfterFrames)
    }
    systemProperty("libfdx.sample.graphics", graphics)
    systemProperty("libfdx.sample.graphicsLabel", graphicsLabel)
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

tasks.register<JavaExec>("run_wgpu_jni") {
    configureSampleRun("Runs the basic desktop sample with WGPU JNI.", "wgpu", "WGPU JNI", wgpuJniRuntimeClasspath)
}

tasks.register<JavaExec>("run_wgpu_ffm") {
    configureSampleRun("Runs the basic desktop sample with WGPU FFM.", "wgpu", "WGPU FFM", wgpuFfmRuntimeClasspath)
    useJava25Launcher()
}

tasks.register<JavaExec>("run_open_gl") {
    configureSampleRun("Runs the basic desktop sample with OpenGL.", "opengl", "OpenGL", openGlRuntimeClasspath)
}

tasks.register<JavaExec>("run_open_gl_ffm") {
    configureSampleRun("Runs the basic desktop sample with OpenGL on Java 25 using LWJGL FFM.", "opengl", "OpenGL LWJGL FFM", openGlRuntimeClasspath)
    useJava25Launcher()
}

tasks.register<JavaExec>("run_open_gl_jni") {
    configureSampleRun("Runs the basic desktop sample with OpenGL on Java 25 using LWJGL JNI.", "opengl", "OpenGL LWJGL JNI", openGlRuntimeClasspath)
    useLwjglJniOnJava25()
}

tasks.register<JavaExec>("run_vulkan") {
    configureSampleRun("Runs the basic desktop sample with Vulkan.", "vulkan", "Vulkan", vulkanRuntimeClasspath)
}

tasks.register<JavaExec>("run_vulkan_ffm") {
    configureSampleRun("Runs the basic desktop sample with Vulkan on Java 25 using LWJGL FFM.", "vulkan", "Vulkan LWJGL FFM", vulkanRuntimeClasspath)
    useJava25Launcher()
}

tasks.register<JavaExec>("run_vulkan_jni") {
    configureSampleRun("Runs the basic desktop sample with Vulkan on Java 25 using LWJGL JNI.", "vulkan", "Vulkan LWJGL JNI", vulkanRuntimeClasspath)
    useLwjglJniOnJava25()
}
