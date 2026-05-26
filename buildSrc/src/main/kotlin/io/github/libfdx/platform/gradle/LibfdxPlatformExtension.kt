package io.github.libfdx.platform.gradle

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.teavm.gradle.api.OptimizationLevel
import org.teavm.gradle.api.SourceFilePolicy
import org.teavm.gradle.api.TeaVMConfiguration
import org.teavm.gradle.api.TeaVMCConfiguration
import org.teavm.gradle.api.TeaVMJSConfiguration
import org.teavm.gradle.api.TeaVMWasmGCConfiguration
import javax.inject.Inject

open class LibfdxPlatformExtension @Inject constructor(
    private val objects: ObjectFactory,
    private val project: Project,
    private val jsConfig: TeaVMJSConfiguration,
    private val wasmConfig: TeaVMWasmGCConfiguration,
    private val cConfig: TeaVMCConfiguration
) {
    internal val declaredTargets = linkedSetOf<LibfdxPlatformTarget>()

    val assets: ConfigurableFileCollection = project.files()

    val js: LibfdxPlatformJsExtension by lazy {
        objects.newInstance(LibfdxPlatformJsExtension::class.java, project, jsConfig)
    }

    val wasm: LibfdxPlatformWasmExtension by lazy {
        objects.newInstance(LibfdxPlatformWasmExtension::class.java, project, wasmConfig)
    }

    val desktopNative: LibfdxPlatformDesktopNativeExtension by lazy {
        objects.newInstance(LibfdxPlatformDesktopNativeExtension::class.java, project, cConfig)
    }

    fun assets(vararg paths: Any) {
        assets.from(*paths)
    }

    fun js(action: Action<in LibfdxPlatformJsExtension>) {
        declaredTargets.add(LibfdxPlatformTarget.JS)
        action.execute(js)
    }

    fun wasm(action: Action<in LibfdxPlatformWasmExtension>) {
        declaredTargets.add(LibfdxPlatformTarget.WASM)
        action.execute(wasm)
    }

    fun desktopNative(action: Action<in LibfdxPlatformDesktopNativeExtension>) {
        declaredTargets.add(LibfdxPlatformTarget.DESKTOP_NATIVE)
        action.execute(desktopNative)
    }

    internal fun isDeclared(target: LibfdxPlatformTarget): Boolean {
        return declaredTargets.contains(target)
    }
}

@Suppress("UNCHECKED_CAST")
open class LibfdxPlatformTargetExtension internal constructor(
    internal val teavmConfig: TeaVMConfiguration
) {
    val outputDir: DirectoryProperty
        get() = teavmConfig.outputDir

    val mainClass: Property<String>
        get() = teavmConfig.mainClass as Property<String>

    val relativePathInOutputDir: Property<String>
        get() = teavmConfig.relativePathInOutputDir as Property<String>

    val optimization: Property<OptimizationLevel>
        get() = teavmConfig.optimization as Property<OptimizationLevel>

    val debugInformation: Property<Boolean>
        get() = teavmConfig.debugInformation as Property<Boolean>

    val fastGlobalAnalysis: Property<Boolean>
        get() = teavmConfig.fastGlobalAnalysis as Property<Boolean>

    val outOfProcess: Property<Boolean>
        get() = teavmConfig.outOfProcess as Property<Boolean>

    val processMemory: Property<Int>
        get() = teavmConfig.processMemory as Property<Int>

    internal fun outputSubDir(): Provider<Directory> {
        return outputDir.flatMap { output ->
            relativePathInOutputDir.map { relativePath ->
                output.dir(relativePath)
            }
        }
    }
}

open class LibfdxPlatformWebExtension @Inject constructor(
    objects: ObjectFactory,
    project: Project,
    teavmConfig: TeaVMConfiguration
) : LibfdxPlatformTargetExtension(teavmConfig) {
    open val entryPointName: Property<String> = objects.property(String::class.java).convention("main")
    val mainClassArgs: Property<String> = objects.property(String::class.java).convention("")
    val htmlTitle: Property<String> = objects.property(String::class.java).convention("libfdx")
    val htmlWidth: Property<Int> = objects.property(Int::class.javaObjectType).convention(640)
    val htmlHeight: Property<Int> = objects.property(Int::class.javaObjectType).convention(480)
    val canvasId: Property<String> = objects.property(String::class.java).convention("libfdx-canvas")
    val serverPort: Property<Int> = objects.property(Int::class.javaObjectType)
        .convention(project.providers.gradleProperty("libfdx.web.port").map(String::toInt).orElse(8080))

    internal fun webappDir(): Provider<Directory> {
        return outputSubDir()
    }
}

@Suppress("UNCHECKED_CAST")
open class LibfdxPlatformJsExtension @Inject constructor(
    objects: ObjectFactory,
    project: Project,
    private val jsConfig: TeaVMJSConfiguration
) : LibfdxPlatformWebExtension(objects, project, jsConfig) {
    init {
        outputDir.convention(project.layout.buildDirectory.dir("dist/web-js"))
        relativePathInOutputDir.convention("webapp")
        targetFileName.convention("app.js")
        optimization.convention(OptimizationLevel.BALANCED)
        debugInformation.convention(false)
        fastGlobalAnalysis.convention(false)
        outOfProcess.convention(false)
        processMemory.convention(512)
        obfuscated.convention(true)
        strict.convention(false)
        sourceMap.convention(false)
        sourceFilePolicy.convention(SourceFilePolicy.LINK_LOCAL_FILES)
    }

    override val entryPointName: Property<String>
        get() = jsConfig.entryPointName as Property<String>

    val targetFileName: Property<String>
        get() = jsConfig.targetFileName as Property<String>

    val obfuscated: Property<Boolean>
        get() = jsConfig.obfuscated as Property<Boolean>

    val strict: Property<Boolean>
        get() = jsConfig.strict as Property<Boolean>

    val sourceMap: Property<Boolean>
        get() = jsConfig.sourceMap as Property<Boolean>

    val sourceFilePolicy: Property<SourceFilePolicy>
        get() = jsConfig.sourceFilePolicy as Property<SourceFilePolicy>
}

@Suppress("UNCHECKED_CAST")
open class LibfdxPlatformWasmExtension @Inject constructor(
    objects: ObjectFactory,
    project: Project,
    private val wasmConfig: TeaVMWasmGCConfiguration
) : LibfdxPlatformWebExtension(objects, project, wasmConfig) {
    init {
        outputDir.convention(project.layout.buildDirectory.dir("dist/web-wasm"))
        relativePathInOutputDir.convention("webapp")
        targetFileName.convention("app.wasm")
        optimization.convention(OptimizationLevel.AGGRESSIVE)
        debugInformation.convention(false)
        fastGlobalAnalysis.convention(false)
        outOfProcess.convention(false)
        processMemory.convention(512)
        obfuscated.convention(true)
        strict.convention(false)
        copyRuntime.convention(true)
        modularRuntime.convention(false)
        sourceMap.convention(false)
        sourceFilePolicy.convention(SourceFilePolicy.LINK_LOCAL_FILES)
    }

    val targetFileName: Property<String>
        get() = wasmConfig.targetFileName as Property<String>

    val obfuscated: Property<Boolean>
        get() = wasmConfig.obfuscated as Property<Boolean>

    val strict: Property<Boolean>
        get() = wasmConfig.strict as Property<Boolean>

    val copyRuntime: Property<Boolean>
        get() = wasmConfig.copyRuntime as Property<Boolean>

    val modularRuntime: Property<Boolean>
        get() = wasmConfig.modularRuntime as Property<Boolean>

    val sourceMap: Property<Boolean>
        get() = wasmConfig.sourceMap as Property<Boolean>

    val sourceFilePolicy: Property<SourceFilePolicy>
        get() = wasmConfig.sourceFilePolicy as Property<SourceFilePolicy>
}

@Suppress("UNCHECKED_CAST")
open class LibfdxPlatformDesktopNativeExtension @Inject constructor(
    objects: ObjectFactory,
    project: Project,
    private val cConfig: TeaVMCConfiguration
) : LibfdxPlatformTargetExtension(cConfig) {
    val targetFileName: Property<String> = objects.property(String::class.java).convention("app")
    val buildType: Property<String> = objects.property(String::class.java).convention("Debug")
    val releasePath: DirectoryProperty = objects.directoryProperty()
        .convention(outputDir.map { it.dir("c/release") })

    init {
        outputDir.convention(project.layout.buildDirectory.dir("dist/desktop-native"))
        relativePathInOutputDir.convention("c/src")
        targetFileName.convention("app")
        optimization.convention(OptimizationLevel.AGGRESSIVE)
        debugInformation.convention(false)
        fastGlobalAnalysis.convention(false)
        outOfProcess.convention(false)
        processMemory.convention(512)
        minHeapSize.convention(4)
        maxHeapSize.convention(128)
        heapDump.convention(false)
        shortFileNames.convention(true)
        obfuscated.convention(true)
        buildType.convention("Debug")
    }

    val minHeapSize: Property<Int>
        get() = cConfig.minHeapSize as Property<Int>

    val maxHeapSize: Property<Int>
        get() = cConfig.maxHeapSize as Property<Int>

    val heapDump: Property<Boolean>
        get() = cConfig.heapDump as Property<Boolean>

    val shortFileNames: Property<Boolean>
        get() = cConfig.shortFileNames as Property<Boolean>

    val obfuscated: Property<Boolean>
        get() = cConfig.obfuscated as Property<Boolean>

    internal fun generatedSourcesDir(): Provider<Directory> {
        return outputSubDir()
    }
}

internal enum class LibfdxPlatformTarget {
    JS,
    WASM,
    DESKTOP_NATIVE
}
