package pl.dock.widget.ui

import android.app.Activity
import android.view.View
import android.view.WindowManager

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
            // NAPRAWA (run 39): same bity systemUiVisibility znikaly na
            // ekranie z EditText mimo apply() w onCreate i onWindowFocusChanged
            // (zrzut obdterminal.png run 39: pasek z godzina nadal widoczny).
            // FLAG_FULLSCREEN to flaga WindowManager - trzyma pasek schowany
            // na poziomie okna i nikt jej nie zrzuca przy fokusie pola tekstowego.
            activity.window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
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
