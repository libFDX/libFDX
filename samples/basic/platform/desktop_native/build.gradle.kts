plugins {
    id("java")
    id("io.github.libfdx.platform")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

group = "io.github.libfdx.samples.basic"

base {
    archivesName.set("sample_basic_desktop_native")
}

dependencies {
    implementation(project(":samples:basic:core"))
    implementation(project(":libfdx:backends:desktop_native"))

    runtimeOnly(project(":libfdx:extensions:graphics:gl:platform:desktop_native"))
}

libfdxPlatform {
    desktopNative {
        mainClass.set("io.github.libfdx.samples.basic.desktopnative.BasicDesktopNativeLauncher")
        targetFileName.set("libfdx-basic-opengl-desktop-native")
        buildType.set("Debug")
    }
}
