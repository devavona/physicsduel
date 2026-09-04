plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    // api, not implementation: the android module depends on :core and needs
    // com.badlogic.gdx.* types (e.g. Application) on its own compile classpath
    // to extend AndroidApplication.
    api(libs.gdx)
}
