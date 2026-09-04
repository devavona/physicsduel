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
}
