package com.devavona.physicsduel

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.backends.headless.HeadlessApplication
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration
import com.badlogic.gdx.physics.box2d.Box2D

/**
 * Test-only bootstrap for code that touches LibGDX statics ([com.badlogic.gdx.Gdx.app],
 * [com.badlogic.gdx.Gdx.files]) or Box2D's native library - which is every
 * JUnit test here, since there's no real Android/desktop app starting one
 * for us. Idempotent and safe to call from every test class's setup: the
 * first call does the real work, every call after that is a no-op.
 *
 * `updatesPerSecond = -1` in the headless config is deliberate - it stops
 * [HeadlessApplication] from spinning up its own render-loop thread, which
 * would otherwise keep the test JVM alive after the tests finish. Tests
 * only need the static Gdx.app/Gdx.files context this sets up, never an
 * actual frame loop.
 */
object GdxTestBootstrap {
    private var started = false

    @Synchronized
    fun ensureRunning() {
        if (started) return
        val config = HeadlessApplicationConfiguration().apply {
            updatesPerSecond = -1
        }
        HeadlessApplication(object : ApplicationAdapter() {}, config)
        Box2D.init() // loads the natives-desktop .so, same call PlayScreen makes in production
        started = true
    }
}
