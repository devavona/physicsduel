plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    // api, not implementation: the android module depends on :core and needs
    // com.badlogic.gdx.* types (e.g. Application) on its own compile classpath
    // to extend AndroidApplication.
    api(libs.gdx)

    // implementation is correct here: Box2D types (World, Body, etc.) are only
    // used internally by the game classes, never exposed to android.
    implementation(libs.gdx.box2d)

    // Pure Java/JVM, no native dependencies - unlike gdx/gdx-box2d, this needs
    // no entry in android's natives-copy task at all.
    implementation(libs.ashley)

    // Test-only from here down - see the comments on these entries in
    // gradle/libs.versions.toml. None of this reaches the Android APK.
    testImplementation(libs.junit)
    testImplementation(libs.gdx.backend.headless)

    // Desktop natives, so tests can run a real HeadlessApplication and step a
    // real Box2D World on this dev machine's JVM (Linux/Windows/macOS) - same
    // natives-<classifier> GAV-string pattern android/build.gradle.kts
    // already uses for its own natives-<abi> jars, since version catalog
    // entries can't carry a classifier. Both are required: gdx-platform's
    // natives-desktop is LibGDX's own base native library (what
    // HeadlessApplication's constructor loads, independent of Box2D) -
    // without it, HeadlessApplication itself fails to start with
    // "Couldn't load shared library 'gdx64.dll'" before any test code runs.
    // gdx-box2d-platform's natives-desktop is separately needed for Box2D
    // specifically (World, Body, etc.).
    testImplementation("com.badlogicgames.gdx:gdx-platform:${libs.versions.gdx.get()}:natives-desktop")
    testImplementation("com.badlogicgames.gdx:gdx-box2d-platform:${libs.versions.box2d.get()}:natives-desktop")
}
