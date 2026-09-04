package com.devavona.physicsduel;

import android.os.Bundle;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;

// Deliberately plain Java, not Kotlin: AGP 9's built-in Kotlin support lives at the
// module level and conflicts with the old org.jetbrains.kotlin.android plugin. Since
// this launcher is just a thin bootstrap (all real logic lives in :core, which is a
// plain Kotlin/JVM module with no Android involvement at all), keeping it Java sidesteps
// that conflict entirely rather than fighting AGP 9's new Kotlin wiring for one class.
public class AndroidLauncher extends AndroidApplication {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
        initialize(new PhysicsDuelGame(), config);
    }
}
