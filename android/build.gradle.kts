plugins {
    alias(libs.plugins.android.application)
}

// Holds the platform-native (.so) artifacts pulled in below, so copyAndroidNatives
// can find them separately from the regular compile/runtime classpath.
val natives: Configuration by configurations.creating

val jniLibsDir = layout.buildDirectory.dir("generated/jniLibs").get().asFile

android {
    namespace = "com.devavona.physicsduel"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.devavona.physicsduel"
        minSdk = 36
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDir(jniLibsDir)
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/robovm/ios/robovm.xml",
                "META-INF/DEPENDENCIES.txt",
                "META-INF/DEPENDENCIES",
                "META-INF/dependencies.txt",
                "**/*.gwt.xml"
            )
            pickFirsts += setOf(
                "META-INF/LICENSE.txt",
                "META-INF/LICENSE",
                "META-INF/license.txt",
                "META-INF/LGPL2.1",
                "META-INF/NOTICE.txt",
                "META-INF/NOTICE",
                "META-INF/notice.txt"
            )
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.gdx.backend.android)

    val gdxVersion = libs.versions.gdx.get()
    val box2dVersion = libs.versions.box2d.get()
    natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-arm64-v8a")
    natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-armeabi-v7a")
    natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-x86")
    natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-x86_64")
    natives("com.badlogicgames.gdx:gdx-box2d-platform:$box2dVersion:natives-arm64-v8a")
    natives("com.badlogicgames.gdx:gdx-box2d-platform:$box2dVersion:natives-armeabi-v7a")
    natives("com.badlogicgames.gdx:gdx-box2d-platform:$box2dVersion:natives-x86")
    natives("com.badlogicgames.gdx:gdx-box2d-platform:$box2dVersion:natives-x86_64")
}

// LibGDX ships its native (.so) code inside classifier jars (natives-<abi>.jar) rather
// than as normal jniLibs, so AGP won't package them automatically. This task unzips the
// .so files for each ABI out of those jars into a build/ output dir that's registered
// above as an extra jniLibs source dir. Runs before AGP merges jniLibs into the APK,
// matched by task-name pattern so it survives AGP renaming build-variant task names.
tasks.register("copyAndroidNatives") {
    doFirst {
        listOf("armeabi-v7a", "arm64-v8a", "x86_64", "x86").forEach { abi ->
            File(jniLibsDir, abi).mkdirs()
        }

        natives.files.forEach { jar ->
            val abi = when {
                jar.name.endsWith("natives-armeabi-v7a.jar") -> "armeabi-v7a"
                jar.name.endsWith("natives-arm64-v8a.jar") -> "arm64-v8a"
                jar.name.endsWith("natives-x86_64.jar") -> "x86_64"
                jar.name.endsWith("natives-x86.jar") -> "x86"
                else -> null
            }
            if (abi != null) {
                copy {
                    from(zipTree(jar))
                    into(File(jniLibsDir, abi))
                    include("*.so")
                }
            }
        }
    }
}

tasks.matching { it.name.contains("merge") && it.name.contains("JniLibFolders") }.configureEach {
    dependsOn("copyAndroidNatives")
}
