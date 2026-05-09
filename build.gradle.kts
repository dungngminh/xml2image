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
val liteDistributionDir = layout.buildDirectory.dir("distributions")
val macIconFile = layout.projectDirectory.file("src/main/resources/macos/AppIcon.icns")

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
    dependsOn(tasks.jar)

    into(litePackageRoot)

    from(tasks.jar.flatMap { it.archiveFile }) {
        into("lib")
        rename { "xml2image.jar" }
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

        root.resolve("README.txt").writeText(
            """
            XML2Image

            Requirements:
            - Java 21 or newer must be installed and available on PATH.

            Run:
            - Linux/macOS: bin/xml2image
            - Windows: bin\xml2image.bat
            """.trimIndent() + "\n",
        )
    }
}

tasks.register<org.gradle.api.tasks.bundling.Zip>("packageLiteZip") {
    group = "distribution"
    description = "Build a lightweight ZIP distribution for Windows and other platforms."
    dependsOn(prepareLitePackage)
    archiveFileName.set("xml2image-lite-${project.version}.zip")
    destinationDirectory.set(liteDistributionDir)
    from(litePackageRoot.map { it.asFile.parentFile }) {
        include("xml2image/**")
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
