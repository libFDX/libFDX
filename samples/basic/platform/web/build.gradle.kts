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
    archivesName.set("sample_basic_web")
}

dependencies {
    implementation(project(":samples:basic:core"))
    implementation(project(":libfdx:backends:web"))
    implementation(project(":libfdx:extensions:graphics:gl:platform:web"))
}

libfdxPlatform {
    js {
        mainClass.set("io.github.libfdx.samples.basic.web.BasicWebJsLauncher")
        htmlTitle.set("libfdx Basic - WebGL JS")
        canvasId.set("libfdx-canvas")
        htmlWidth.set(640)
        htmlHeight.set(480)
    }
    wasm {
        mainClass.set("io.github.libfdx.samples.basic.web.BasicWebWasmLauncher")
        htmlTitle.set("libfdx Basic - WebGL Wasm")
        canvasId.set("libfdx-canvas")
        htmlWidth.set(640)
        htmlHeight.set(480)
    }
}
