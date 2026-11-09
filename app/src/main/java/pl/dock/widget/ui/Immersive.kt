package pl.dock.widget.ui

import android.app.Activity
import android.view.View

/**
 * NAPRAWA (2026-11-09, zgloszenie kierowcy): ekrany OBD dziedzicza po golym
 * Activity (nie po BaseActivity launchera), wiec pasek statusu Androida
 * wyskakiwal na gorze. Wymog: pasek domyslnie SCHOWANY, dostepny tylko
 * "do wyciagniecia" gestem od krawedzi = tryb IMMERSIVE_STICKY.
 *
 * Android 9 zrzuca flagi przy kazdej utracie/przywruceniu fokusu okna
 * (dialogi, klawiatura, przelaczanie aplikacji), dlatego kazdy ekran
 * wywoluje apply() w onCreate ORAZ w onWindowFocusChanged(hasFocus=true).
 */
object Immersive {

    @Suppress("DEPRECATION")
    fun apply(activity: Activity) {
        try {
            activity.window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                )
        } catch (ignored: Exception) {
            // DecorView jeszcze nie istnieje / dziwactwo dostawcy - olewamy
        }
    }
}
