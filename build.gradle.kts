import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

plugins {
    kotlin("jvm") version "2.2.21"
    application
}

group = "com.komkat"
version = "1.0.0"

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("com.komkat.xml2image.XmlResourceConverterGuiKt")
}

val packageInputDir = layout.buildDirectory.dir("jpackage/input")
val packageOutputDir = layout.buildDirectory.dir("jpackage/output")
val packageRuntimeDir = layout.buildDirectory.dir("jpackage/runtime")
val litePackageRoot = layout.buildDirectory.dir("lite-package/xml2image")
val windowsPackageRoot = layout.buildDirectory.dir("lite-package/windows")
val liteDistributionDir = layout.buildDirectory.dir("distributions")
val macIconFile = layout.projectDirectory.file("src/main/resources/macos/AppIcon.icns")
val appIconPng = layout.projectDirectory.file("assets/logo.png")
val generatedWindowsIcon = layout.buildDirectory.file("generated-icons/xml2image.ico")

tasks.jar {
    archiveBaseName.set("xml2image")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith(".jar") }
            .map { zipTree(it) }
    })
}

val prepareJpackage by tasks.registering(Copy::class) {
    dependsOn(tasks.jar)
    from(tasks.jar.flatMap { it.archiveFile })
    from(configurations.runtimeClasspath)
    into(packageInputDir)
}

val prepareJpackageRuntime by tasks.registering(Exec::class) {
    group = "distribution"
    description = "Build a stripped Java runtime for the packaged app."
    doFirst {
        delete(packageRuntimeDir)
    }
    commandLine(
        "jlink",
        "--add-modules", "java.base,java.desktop",
        "--strip-debug",
        "--no-man-pages",
        "--no-header-files",
        "--compress", "zip-9",
        "--output", packageRuntimeDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("packageDmg") {
    group = "distribution"
    description = "Build a macOS DMG installer with jpackage."
    dependsOn(prepareJpackage, prepareJpackageRuntime)
    onlyIf {
        val isMac = System.getProperty("os.name").lowercase().contains("mac")
        if (!isMac) {
            logger.warn("DMG packaging is only available on macOS.")
            false
        } else {
            true
        }
    }

    doFirst {
        packageOutputDir.get().asFile.mkdirs()
    }

    commandLine(
        "jpackage",
        "--type", "dmg",
        "--name", "XML Resource Converter",
        "--app-version", project.version.toString(),
        "--vendor", "Komkat",
        "--input", packageInputDir.get().asFile.absolutePath,
        "--main-jar", tasks.jar.get().archiveFileName.get(),
        "--main-class", application.mainClass.get(),
        "--runtime-image", packageRuntimeDir.get().asFile.absolutePath,
        "--icon", macIconFile.asFile.absolutePath,
        "--dest", packageOutputDir.get().asFile.absolutePath,
        "--java-options", "-Dapple.laf.useScreenMenuBar=true",
    )
}

val prepareLitePackage by tasks.registering(Sync::class) {
    group = "distribution"
    description = "Prepare a lightweight cross-platform app folder that requires Java 21+."
    dependsOn(tasks.jar, "generateWindowsIcon")

    into(litePackageRoot)

    from(tasks.jar.flatMap { it.archiveFile }) {
        into("lib")
        rename { "xml2image.jar" }
    }

    from(appIconPng) {
        into("share/icons")
        rename { "xml2image.png" }
    }

    from(generatedWindowsIcon) {
        into("share/icons")
        rename { "xml2image.ico" }
    }

    doLast {
        val root = litePackageRoot.get().asFile
        val binDir = root.resolve("bin")
        binDir.mkdirs()

        val unixLauncher = binDir.resolve("xml2image")
        unixLauncher.writeText(
            """
            #!/usr/bin/env sh
            set -eu

            app_dir="${'$'}(CDPATH= cd -- "${'$'}(dirname -- "${'$'}0")/.." && pwd)"
            exec java -jar "${'$'}app_dir/lib/xml2image.jar" "${'$'}@"
            """.trimIndent() + "\n",
        )
        unixLauncher.setExecutable(true)

        binDir.resolve("xml2image.bat").writeText(
            """
            @echo off
            set "APP_DIR=%~dp0.."
            java -jar "%APP_DIR%\lib\xml2image.jar" %*
            """.trimIndent().replace("\n", "\r\n") + "\r\n",
        )

        root.resolve("xml2image.desktop").writeText(
            """
            [Desktop Entry]
            Type=Application
            Name=XML2Image
            Comment=Convert Android VectorDrawable XML files to PNG, JPG, or WebP
            Exec=bin/xml2image
            Icon=share/icons/xml2image.png
            Terminal=false
            Categories=Graphics;Utility;
            """.trimIndent() + "\n",
        )

        root.resolve("README.txt").writeText(
            """
            XML2Image

            Requirements:
            - Java 21 or newer must be installed and available on PATH.

            Run:
            - Linux/macOS: bin/xml2image
            - Windows: bin\xml2image.bat

            Icons:
            - Linux: share/icons/xml2image.png and xml2image.desktop
            - Windows: share\icons\xml2image.ico
            """.trimIndent() + "\n",
        )
    }
}


tasks.register("generateWindowsIcon") {
    group = "distribution"
    description = "Generate a Windows ICO from assets/logo.png for lightweight packages."
    inputs.file(appIconPng)
    outputs.file(generatedWindowsIcon)

    doLast {
        val input = appIconPng.asFile
        val output = generatedWindowsIcon.get().asFile
        output.parentFile.mkdirs()

        val image = javax.imageio.ImageIO.read(input)
        val resized = BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB)
        val graphics = resized.createGraphics()
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics.drawImage(image, 0, 0, 256, 256, null)
        graphics.dispose()

        val pngBytes = ByteArrayOutputStream().use { stream ->
            javax.imageio.ImageIO.write(resized, "png", stream)
            stream.toByteArray()
        }

        DataOutputStream(output.outputStream().buffered()).use { out ->
            fun writeWord(value: Int) {
                out.writeByte(value and 0xff)
                out.writeByte((value ushr 8) and 0xff)
            }

            fun writeDword(value: Int) {
                out.writeByte(value and 0xff)
                out.writeByte((value ushr 8) and 0xff)
                out.writeByte((value ushr 16) and 0xff)
                out.writeByte((value ushr 24) and 0xff)
            }

            writeWord(0)
            writeWord(1)
            writeWord(1)
            out.writeByte(0)
            out.writeByte(0)
            out.writeByte(0)
            out.writeByte(0)
            writeWord(1)
            writeWord(32)
            writeDword(pngBytes.size)
            writeDword(22)
            out.write(pngBytes)
        }
    }
}

tasks.register<Exec>("prepareWindowsAppImage") {
    group = "distribution"
    description = "Build a Windows app image with an .exe launcher and no bundled runtime."
    dependsOn(tasks.jar, "generateWindowsIcon")
    onlyIf {
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        if (!isWindows) logger.warn("Windows app image packaging is only available on Windows.")
        isWindows
    }

    doFirst {
        delete(windowsPackageRoot)
        windowsPackageRoot.get().asFile.mkdirs()
    }

    commandLine(
        "jpackage",
        "--type", "app-image",
        "--name", "XML2Image",
        "--app-version", project.version.toString(),
        "--vendor", "Komkat",
        "--input", tasks.jar.get().archiveFile.get().asFile.parentFile.absolutePath,
        "--main-jar", tasks.jar.get().archiveFileName.get(),
        "--main-class", application.mainClass.get(),
        "--icon", generatedWindowsIcon.get().asFile.absolutePath,
        "--dest", windowsPackageRoot.get().asFile.absolutePath,
    )
}

tasks.register<org.gradle.api.tasks.bundling.Zip>("packageLiteZip") {
    group = "distribution"
    description = "Build a lightweight ZIP distribution for Windows with an .exe launcher."
    dependsOn("prepareWindowsAppImage")
    archiveFileName.set("xml2image-windows-lite-${project.version}.zip")
    destinationDirectory.set(liteDistributionDir)
    from(windowsPackageRoot) {
        include("XML2Image/**")
    }
}

tasks.register<org.gradle.api.tasks.bundling.Tar>("packageLiteTar") {
    group = "distribution"
    description = "Build a lightweight tar.gz distribution for Linux."
    dependsOn(prepareLitePackage)
    archiveFileName.set("xml2image-lite-${project.version}.tar.gz")
    compression = org.gradle.api.tasks.bundling.Compression.GZIP
    destinationDirectory.set(liteDistributionDir)
    from(litePackageRoot.map { it.asFile.parentFile }) {
        include("xml2image/**")
    }
}

tasks.register("packageLite") {
    group = "distribution"
    description = "Build lightweight ZIP and tar.gz distributions."
    dependsOn("packageLiteZip", "packageLiteTar")
}
