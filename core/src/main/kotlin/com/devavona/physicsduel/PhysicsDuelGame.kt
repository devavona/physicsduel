package com.devavona.physicsduel

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20

/**
 * Entry point shared by every platform backend (currently just Android).
 *
 * Phase 1 of the foundation build: this only proves the render loop boots and
 * runs on-device (clears the screen each frame). No physics world, ECS, input,
 * or scene management yet - those are later phases layered on top of this class
 * without touching the platform launchers.
 */
class PhysicsDuelGame : ApplicationAdapter() {

    override fun render() {
        Gdx.gl.glClearColor(0.043f, 0.071f, 0.126f, 1f) // deep space navy
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
    }
}
