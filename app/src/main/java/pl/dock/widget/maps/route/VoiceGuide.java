package pl.dock.widget.maps.route;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;

import java.util.Locale;

/**
 * Komendy glosowe nawigacji przez systemowe TTS. Audio focus
 * AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK (jak AudioAlertManager) - muzyka
 * Przycisza sie na czas komendy i wraca, zamiast znikac.
 *
 * Brak TTS na urzadzeniu nie wywala nawigacji: speak() po prostu milczy.
 */
public final class VoiceGuide implements TextToSpeech.OnInitListener {

    private final Context ctx;
    private final AudioManager am;
    private final Handler h = new Handler(Looper.getMainLooper());
    private TextToSpeech tts;
    private volatile boolean ready;
    private String pending;
    private AudioFocusRequest focusRequest;

    private final Runnable abandon = new Runnable() {
        @Override
        public void run() {
            abandonFocus();
        }
    };

    public VoiceGuide(Context c) {
        ctx = c.getApplicationContext();
        am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        try {
            tts = new TextToSpeech(ctx, this);
        } catch (Throwable t) {
            tts = null;
        }
    }

    @Override
    public void onInit(int status) {
        try {
            ready = status == TextToSpeech.SUCCESS && tts != null;
            if (ready) {
                tts.setLanguage(new Locale("pl", "PL"));
                if (pending != null) {
                    String p = pending;
                    pending = null;
                    speakInternal(p);
                }
            }
        } catch (Throwable ignored) {
            ready = false;
        }
    }

    /** Mowi tekst. Gdy TTS jeszcze wstaje, tekst wisi w kolejce i wypadnie po init. */
    public void speak(String s) {
        if (s == null || s.length() == 0) return;
        if (!ready) {
            pending = s;
            return;
        }
        speakInternal(s);
    }

    private void speakInternal(String s) {
        try {
            requestFocus();
            tts.speak(s, TextToSpeech.QUEUE_ADD, null);
            h.removeCallbacks(abandon);
            h.postDelayed(abandon, 6000L);
        } catch (Throwable ignored) {
        }
    }

    private void requestFocus() {
        try {
            if (focusRequest == null) {
                focusRequest = new AudioFocusRequest.Builder(
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                                .build())
                        .build();
            }
            am.requestAudioFocus(focusRequest);
        } catch (Throwable ignored) {
        }
    }

    private void abandonFocus() {
        try {
            if (focusRequest != null) am.abandonAudioFocusRequest(focusRequest);
        } catch (Throwable ignored) {
        }
    }

    public void shutdown() {
        try {
            h.removeCallbacks(abandon);
            abandonFocus();
            if (tts != null) tts.shutdown();
        } catch (Throwable ignored) {
        }
        ready = false;
    }
}
