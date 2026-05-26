plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

group = "io.github.libfdx.gl"

base {
    archivesName.set("gl_desktop_native")
}

dependencies {
    api(project(":libfdx:extensions:graphics:gl:core"))
}
