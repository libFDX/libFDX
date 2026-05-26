package io.github.libfdx.platform.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.teavm.gradle.TeaVMPlugin
import org.teavm.gradle.api.TeaVMExtension
import org.teavm.gradle.tasks.GenerateCTask
import java.io.File

class LibfdxPlatformGradlePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply(JavaPlugin::class.java)
        project.pluginManager.apply(TeaVMPlugin::class.java)

        val teavm = project.extensions.getByType<TeaVMExtension>()
        val extension = project.extensions.create<LibfdxPlatformExtension>(
            "libfdxPlatform",
            project,
            teavm.getJs(),
            teavm.getWasmGC(),
            teavm.getC()
        )

        project.afterEvaluate {
            configureTargets(project, extension)
            registerTasks(project, extension)
        }
    }

    private fun configureTargets(project: Project, extension: LibfdxPlatformExtension) {
        if(extension.isDeclared(LibfdxPlatformTarget.DESKTOP_NATIVE)) {
            configureDesktopNative(project, extension)
        }
    }

    private fun configureDesktopNative(project: Project, extension: LibfdxPlatformExtension) {
        val desktopNative = extension.desktopNative
        project.tasks.named(TeaVMPlugin.C_TASK_NAME, GenerateCTask::class.java).configure {
            targetFileName.set(desktopNative.targetFileName)
            properties.put("libfdx.native.backend", "desktop_native")
        }
    }

    private fun registerTasks(project: Project, extension: LibfdxPlatformExtension) {
        if(extension.isDeclared(LibfdxPlatformTarget.JS)) {
            registerJsTasks(project, extension)
        }
        if(extension.isDeclared(LibfdxPlatformTarget.WASM)) {
            registerWasmTasks(project, extension)
        }
        if(extension.isDeclared(LibfdxPlatformTarget.DESKTOP_NATIVE)) {
            registerDesktopNativeTasks(project, extension)
        }
    }

    private fun registerJsTasks(project: Project, extension: LibfdxPlatformExtension) {
        val prepare = project.tasks.register<LibfdxPlatformWebAppTask>("libfdx_web_js_prepare") {
            group = TASK_GROUP
            description = "Generate the libfdx Web JavaScript web application shell."
            dependsOn(project.tasks.named(TeaVMPlugin.JS_TASK_NAME))
            webappDir.set(extension.js.webappDir())
            title.set(extension.js.htmlTitle)
            width.set(extension.js.htmlWidth)
            height.set(extension.js.htmlHeight)
            canvasId.set(extension.js.canvasId)
            entryPointName.set(extension.js.entryPointName)
            mainClassArgs.set(extension.js.mainClassArgs)
            targetFileName.set(extension.js.targetFileName)
            wasm.set(false)
        }
        val build = project.tasks.register("libfdx_web_js_build") {
            group = TASK_GROUP
            description = "Build the libfdx Web JavaScript web application."
            dependsOn(prepare)
        }
        project.tasks.register<LibfdxPlatformRunWebTask>("libfdx_web_js_run") {
            group = TASK_GROUP
            description = "Build and serve the libfdx Web JavaScript web application."
            dependsOn(build)
            webappDir.set(extension.js.webappDir())
            port.set(extension.js.serverPort)
        }
    }

    private fun registerWasmTasks(project: Project, extension: LibfdxPlatformExtension) {
        val prepare = project.tasks.register<LibfdxPlatformWebAppTask>("libfdx_web_wasm_prepare") {
            group = TASK_GROUP
            description = "Generate the libfdx Web Wasm web application shell."
            dependsOn(project.tasks.named(TeaVMPlugin.BUILD_WASM_GC_TASK_NAME))
            webappDir.set(extension.wasm.webappDir())
            title.set(extension.wasm.htmlTitle)
            width.set(extension.wasm.htmlWidth)
            height.set(extension.wasm.htmlHeight)
            canvasId.set(extension.wasm.canvasId)
            entryPointName.set(extension.wasm.entryPointName)
            mainClassArgs.set(extension.wasm.mainClassArgs)
            targetFileName.set(extension.wasm.targetFileName)
            wasm.set(true)
        }
        val build = project.tasks.register("libfdx_web_wasm_build") {
            group = TASK_GROUP
            description = "Build the libfdx Web Wasm web application."
            dependsOn(prepare)
        }
        project.tasks.register<LibfdxPlatformRunWebTask>("libfdx_web_wasm_run") {
            group = TASK_GROUP
            description = "Build and serve the libfdx Web Wasm web application."
            dependsOn(build)
            webappDir.set(extension.wasm.webappDir())
            port.set(extension.wasm.serverPort)
        }
    }

    private fun registerDesktopNativeTasks(project: Project, extension: LibfdxPlatformExtension) {
        val sourceSets = project.extensions.getByType<SourceSetContainer>()
        val runtimeClasspath = sourceSets.getByName("main").runtimeClasspath
        val generate = project.tasks.register<LibfdxPlatformDesktopNativeProjectTask>("libfdx_desktop_native_generate") {
            group = TASK_GROUP
            description = "Generate the libfdx desktop_native project."
            dependsOn(project.tasks.named(TeaVMPlugin.C_TASK_NAME))
            buildRoot.set(extension.desktopNative.outputDir)
            generatedSourcesDir.set(extension.desktopNative.generatedSourcesDir())
            releaseDir.set(extension.desktopNative.releasePath)
            projectName.set(extension.desktopNative.targetFileName)
            buildType.set(extension.desktopNative.buildType)
            nativeResourceClasspath.from(runtimeClasspath)
        }
        val build = project.tasks.register<LibfdxPlatformNativeBuildTask>("libfdx_desktop_native_build") {
            group = TASK_GROUP
            description = "Generate and build the libfdx desktop_native executable."
            dependsOn(generate)
            buildRoot.set(extension.desktopNative.outputDir)
            scriptBaseName.set(extension.desktopNative.buildType.map { type ->
                if(type.equals("release", ignoreCase = true)) "app_release" else "app_debug"
            })
        }
        project.tasks.register<LibfdxPlatformDesktopNativeRunTask>("libfdx_desktop_native_run") {
            group = TASK_GROUP
            description = "Generate, build, and run the libfdx desktop_native executable."
            dependsOn(build)
            releaseDir.set(extension.desktopNative.releasePath)
            projectName.set(extension.desktopNative.targetFileName)
            buildType.set(extension.desktopNative.buildType)
            runArgs.set(project.providers.gradleProperty("libfdx.desktopNative.runArgs").map { value ->
                value.split(Regex("\\s+")).filter { it.isNotBlank() }
            }.orElse(emptyList()))
        }
    }

    internal companion object {
        const val TASK_GROUP = "libfdx platform"
    }
}
