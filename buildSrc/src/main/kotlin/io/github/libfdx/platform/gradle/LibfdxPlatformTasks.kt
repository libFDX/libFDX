package io.github.libfdx.platform.gradle

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.zip.ZipFile

abstract class LibfdxPlatformWebAppTask : DefaultTask() {
    @get:OutputDirectory
    abstract val webappDir: DirectoryProperty

    @get:Input
    abstract val title: Property<String>

    @get:Input
    abstract val width: Property<Int>

    @get:Input
    abstract val height: Property<Int>

    @get:Input
    abstract val canvasId: Property<String>

    @get:Input
    abstract val entryPointName: Property<String>

    @get:Input
    abstract val mainClassArgs: Property<String>

    @get:Input
    abstract val targetFileName: Property<String>

    @get:Input
    abstract val wasm: Property<Boolean>

    @TaskAction
    fun writeWebApp() {
        val root = webappDir.get().asFile
        val webInf = File(root, "WEB-INF")
        root.mkdirs()
        webInf.mkdirs()
        File(root, "index.html").writeText(indexHtml(), StandardCharsets.UTF_8)
        File(webInf, "web.xml").writeText("<web-app></web-app>\n", StandardCharsets.UTF_8)
    }

    private fun indexHtml(): String {
        val escapedTitle = html(title.get())
        val escapedCanvas = js(canvasId.get())
        val args = mainClassArgs.get()
        val mode = if(wasm.get()) {
            """
            (async function() {
                const teavm = await TeaVM.wasmGC.load("${js(targetFileName.get())}");
                teavm.exports.${entryPointName.get()}([$args]);
            })();
            """.trimIndent()
        }
        else {
            "${entryPointName.get()}($args);"
        }
        val script = if(wasm.get()) {
            """<script type="text/javascript" src="${html(targetFileName.get())}-runtime.js"></script>"""
        }
        else {
            """<script type="text/javascript" src="${html(targetFileName.get())}"></script>"""
        }
        return """
            <!doctype html>
            <html>
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>$escapedTitle</title>
                <style>
                    html, body { margin: 0; width: 100%; height: 100%; overflow: hidden; background: #ffffff; }
                    canvas { display: block; width: 100vw; height: 100vh; }
                </style>
            </head>
            <body>
                <canvas id="$escapedCanvas" width="${width.get()}" height="${height.get()}"></canvas>
                $script
                <script type="text/javascript">
                    $mode
                </script>
            </body>
            </html>
        """.trimIndent() + "\n"
    }

    private fun html(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }

    private fun js(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
    }
}

@DisableCachingByDefault(because = "Starts a blocking local HTTP server")
abstract class LibfdxPlatformRunWebTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val webappDir: DirectoryProperty

    @get:Input
    abstract val port: Property<Int>

    @TaskAction
    fun run() {
        val root = webappDir.get().asFile.canonicalFile
        val server = HttpServer.create(InetSocketAddress(port.get()), 0)
        server.createContext("/") { exchange ->
            serve(root, exchange)
        }
        server.executor = null
        val shutdownHook = Thread {
            server.stop(0)
        }
        Runtime.getRuntime().addShutdownHook(shutdownHook)
        try {
            server.start()
            logger.lifecycle("Serving ${root.absolutePath} at http://localhost:${port.get()}/")
            CountDownLatch(1).await()
        }
        finally {
            server.stop(0)
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook)
            }
            catch(_: IllegalStateException) {
            }
        }
    }

    private fun serve(root: File, exchange: HttpExchange) {
        val rawPath = exchange.requestURI.path.trimStart('/')
        val requested = File(root, if(rawPath.isEmpty()) "index.html" else rawPath).canonicalFile
        val file = if(requested.isDirectory) File(requested, "index.html") else requested
        if(!file.toPath().startsWith(root.toPath()) || !file.isFile) {
            exchange.sendResponseHeaders(404, -1)
            exchange.close()
            return
        }
        val bytes = Files.readAllBytes(file.toPath())
        exchange.responseHeaders.add("Content-Type", contentType(file.name))
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun contentType(name: String): String {
        return when {
            name.endsWith(".html") -> "text/html; charset=utf-8"
            name.endsWith(".js") -> "text/javascript; charset=utf-8"
            name.endsWith(".wasm") -> "application/wasm"
            name.endsWith(".png") -> "image/png"
            name.endsWith(".jpg") || name.endsWith(".jpeg") -> "image/jpeg"
            else -> "application/octet-stream"
        }
    }
}

abstract class LibfdxPlatformDesktopNativeProjectTask : DefaultTask() {
    @get:OutputDirectory
    abstract val buildRoot: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val generatedSourcesDir: DirectoryProperty

    @get:OutputDirectory
    abstract val releaseDir: DirectoryProperty

    @get:Input
    abstract val projectName: Property<String>

    @get:Input
    abstract val buildType: Property<String>

    @get:Classpath
    abstract val nativeResourceClasspath: ConfigurableFileCollection

    @TaskAction
    fun writeProject() {
        val root = buildRoot.get().asFile
        val sources = generatedSourcesDir.get().asFile
        val release = releaseDir.get().asFile
        root.mkdirs()
        sources.mkdirs()
        release.mkdirs()
        copyNativeResources(root)
        File(sources, "app_include.c").writeText("""
            #include <GL/glew.h>
            #if defined(__has_include)
            #  if __has_include("teavm_optimizations.h")
            #    include "teavm_optimizations.h"
            #  endif
            #endif

            #include "all.c"
        """.trimIndent() + "\n", StandardCharsets.UTF_8)
        File(root, "CMakeLists.txt").writeText(cmake(root, release), StandardCharsets.UTF_8)
        writeBuildScript(root, "app_debug", "Debug")
        writeBuildScript(root, "app_release", "Release")
    }

    private fun copyNativeResources(root: File) {
        val outputRoot = File(root, "c/external_cpp")
        outputRoot.mkdirs()
        nativeResourceClasspath.files.forEach { entry ->
            if(entry.isDirectory) {
                val nativeRoot = File(entry, "libfdx-native/desktop")
                if(nativeRoot.isDirectory) {
                    nativeRoot.copyRecursively(outputRoot, overwrite = true)
                }
            }
            else if(entry.isFile && entry.extension.equals("jar", ignoreCase = true)) {
                copyNativeResourcesFromJar(entry, outputRoot)
            }
        }
    }

    private fun copyNativeResourcesFromJar(jar: File, outputRoot: File) {
        ZipFile(jar).use { zip ->
            val entries = zip.entries()
            while(entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val prefix = "libfdx-native/desktop/"
                if(entry.isDirectory || !entry.name.startsWith(prefix)) {
                    continue
                }
                val relativePath = entry.name.removePrefix(prefix)
                if(relativePath.isBlank()) {
                    continue
                }
                val output = File(outputRoot, relativePath).canonicalFile
                if(!output.toPath().startsWith(outputRoot.canonicalFile.toPath())) {
                    throw IllegalStateException("Refusing to extract native resource outside output directory: ${entry.name}")
                }
                output.parentFile.mkdirs()
                zip.getInputStream(entry).use { input ->
                    output.outputStream().use { outputStream ->
                        input.copyTo(outputStream)
                    }
                }
            }
        }
    }

    private fun cmake(root: File, release: File): String {
        val releasePath = release.absolutePath.replace("\\", "/")
        val rootPath = root.absolutePath.replace("\\", "/")
        return """
            cmake_minimum_required(VERSION 3.10)
            project(${projectName.get()} C)
            set(CMAKE_C_STANDARD 11)

            if(CMAKE_CONFIGURATION_TYPES)
              foreach(config ${'$'}{CMAKE_CONFIGURATION_TYPES})
                string(TOUPPER ${'$'}{config} config_upper)
                set(CMAKE_RUNTIME_OUTPUT_DIRECTORY_${'$'}{config_upper} "$releasePath")
              endforeach()
            else()
              set(CMAKE_RUNTIME_OUTPUT_DIRECTORY "$releasePath")
            endif()

            if(NOT CMAKE_BUILD_TYPE)
              set(CMAKE_BUILD_TYPE ${buildType.get()})
            endif()

            if(UNIX)
              find_package(glfw3 CONFIG REQUIRED)
              find_package(OpenGL REQUIRED)
              find_package(GLEW REQUIRED)
            endif()

            if(WIN32)
              add_definitions(-DGLEW_STATIC)
              include_directories("$rootPath/c/external_cpp/glfw/include")
              include_directories("$rootPath/c/external_cpp/glew-2.3.0/include")
              link_directories("$rootPath/c/external_cpp/glfw/lib-vc2022")
              link_directories("$rootPath/c/external_cpp/glew-2.3.0/lib/Release/x64")
            endif()

            include_directories("$rootPath/c/external_cpp/teavm_optimizations/pure")
            include_directories("$rootPath/c/external_cpp/teavm_optimizations/teavm")
            include_directories("$rootPath/c/external_cpp/teavm_stats")

            set(SOURCES "$rootPath/c/src/app_include.c")
            set(TEAVM_FASTMATH_SOURCE "$rootPath/c/external_cpp/teavm_optimizations/teavm/teavm_fastmath.c")
            set(TEAVM_MATRIX4_SOURCE "$rootPath/c/external_cpp/teavm_optimizations/teavm/teavm_matrix4.c")
            set(TEAVM_MEMORY_STATS_SOURCE "$rootPath/c/external_cpp/teavm_stats/teavm_memory_stats.c")
            foreach(optional_source ${'$'}{TEAVM_FASTMATH_SOURCE} ${'$'}{TEAVM_MATRIX4_SOURCE} ${'$'}{TEAVM_MEMORY_STATS_SOURCE})
              if(EXISTS "${'$'}{optional_source}")
                list(APPEND SOURCES "${'$'}{optional_source}")
              endif()
            endforeach()
            set(TEAVM_SPRITEBATCH_SOURCE "$rootPath/c/external_cpp/teavm_optimizations/teavm/teavm_spritebatch.c")
            set(GDX_SPRITEBATCH_HEADER "$rootPath/c/src/classes/com/badlogic/gdx/graphics/g2d/SpriteBatch.h")
            if(EXISTS "${'$'}{TEAVM_SPRITEBATCH_SOURCE}" AND EXISTS "${'$'}{GDX_SPRITEBATCH_HEADER}")
              list(APPEND SOURCES "${'$'}{TEAVM_SPRITEBATCH_SOURCE}")
            endif()

            add_executable(${projectName.get()} ${'$'}{SOURCES})

            if(MSVC)
              target_compile_options(${projectName.get()} PRIVATE
                ${'$'}<${'$'}<CONFIG:Debug>:/Od /Zi>
                ${'$'}<${'$'}<CONFIG:Release>:/O2 /Ob2 /Oi /Ot>)
            elseif(CMAKE_C_COMPILER_ID MATCHES "GNU|Clang")
              target_compile_options(${projectName.get()} PRIVATE
                ${'$'}<${'$'}<CONFIG:Debug>:-g>
                ${'$'}<${'$'}<CONFIG:Release>:-O3>)
            endif()

            set_target_properties(${projectName.get()} PROPERTIES
              OUTPUT_NAME "${projectName.get()}_${'$'}<IF:${'$'}<CONFIG:Debug>,debug,release>")

            if(WIN32)
              set_target_properties(${projectName.get()} PROPERTIES
                VS_DEBUGGER_WORKING_DIRECTORY "$releasePath")
              target_link_libraries(${projectName.get()} glfw3 opengl32 glew32s)
              set_property(DIRECTORY ${'$'}{CMAKE_CURRENT_SOURCE_DIR} PROPERTY VS_STARTUP_PROJECT ${projectName.get()})
            endif()
            if(UNIX)
              target_link_libraries(${projectName.get()} PRIVATE OpenGL::GL glfw GLEW::GLEW m)
            endif()
        """.trimIndent() + "\n"
    }

    private fun writeBuildScript(root: File, baseName: String, config: String) {
        if(isWindows()) {
            File(root, "$baseName.bat").writeText("""
                @echo off
                setlocal
                cd /d "%~dp0"
                cmake -S . -B build\cmake
                if errorlevel 1 exit /b 1
                cmake --build build\cmake --config $config
                if errorlevel 1 exit /b 1
                endlocal
            """.trimIndent() + "\n", StandardCharsets.UTF_8)
        }
        else {
            val script = File(root, "$baseName.sh")
            script.writeText("""
                #!/usr/bin/env bash
                set -euo pipefail
                cd "$(dirname "$0")"
                cmake -S . -B build/cmake -DCMAKE_BUILD_TYPE="$config"
                cmake --build build/cmake --config "$config"
            """.trimIndent() + "\n", StandardCharsets.UTF_8)
            script.setExecutable(true, false)
        }
    }
}

@DisableCachingByDefault(because = "Runs external native build scripts")
abstract class LibfdxPlatformNativeBuildTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val buildRoot: DirectoryProperty

    @get:Input
    abstract val scriptBaseName: Property<String>

    @TaskAction
    fun build() {
        val root = buildRoot.get().asFile
        val script = File(root, scriptBaseName.get() + if(isWindows()) ".bat" else ".sh")
        if(!script.isFile) {
            throw IllegalStateException("Native build script was not generated: ${script.absolutePath}")
        }
        val command = if(isWindows()) listOf("cmd", "/c", script.absolutePath) else listOf("bash", script.absolutePath)
        val process = ProcessBuilder(command)
            .directory(root)
            .redirectErrorStream(true)
            .start()
        process.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { logger.lifecycle(it) }
        }
        val exitCode = process.waitFor()
        if(exitCode != 0) {
            throw IllegalStateException("Native build failed with exit code $exitCode")
        }
    }
}

abstract class LibfdxPlatformDesktopNativeRunTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val releaseDir: DirectoryProperty

    @get:Input
    abstract val projectName: Property<String>

    @get:Input
    abstract val buildType: Property<String>

    @get:Input
    abstract val runArgs: ListProperty<String>

    @TaskAction
    fun run() {
        val suffix = if(buildType.get().equals("release", ignoreCase = true)) "release" else "debug"
        val executable = File(releaseDir.get().asFile, projectName.get() + "_" + suffix + if(isWindows()) ".exe" else "")
        if(!executable.isFile) {
            throw IllegalStateException("Native executable was not built: ${executable.absolutePath}")
        }
        val command = mutableListOf(executable.absolutePath)
        command.addAll(runArgs.get())
        val process = ProcessBuilder(command)
            .directory(releaseDir.get().asFile)
            .inheritIO()
            .start()
        val exitCode = process.waitFor()
        if(exitCode != 0) {
            throw IllegalStateException("Native executable failed with exit code $exitCode")
        }
    }
}

private fun isWindows(): Boolean {
    return System.getProperty("os.name").lowercase().contains("windows")
}
