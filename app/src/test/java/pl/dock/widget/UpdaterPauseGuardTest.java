package pl.dock.widget;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;

import org.junit.Test;

/**
 * Alarm regresyjny mechanizmu pauzy nakladki Night Shift przy instalacji APK
 * (JVM, bez Androida; pilnuje ZRODLA tak jak ConnectivityPanelGuardTest).
 *
 * Niezmienniki (AGENTS.md, "Okna instalatora odcinaja dotyk pod nakladkami"):
 * 1. pauza ZAWSZE przed otwarciem systemowego okna, nigdy po startActivity
 *    (okno otwarte pod nakladka = martwe przyciski, flaga "obscured");
 * 2. pauza TYLKO wtedy, gdy okno na pewno sie otworzy. Sesja PackageInstaller
 *    pauzuje dopiero w swoim receiverze (STATUS_PENDING_USER_ACTION albo
 *    awaryjny ACTION_VIEW po porazce) - NIGDY na zapas przed commit().
 *    Cicha sesja (czesc radiow OEM: sukces bez okna) nie moze zostawiac
 *    nakladki zdjetej - zadna aktywnosc nie zanika, wiec zaden onResume
 *    nie przywroci filtra.
 */
public class UpdaterPauseGuardTest {

    @Test
    public void pauzaNieJestZdejmowanaNaZapasPrzedSesja() throws Exception {
        String src = readSource();
        assertFalse("NightShift.pause przed installWithSession/commitem psuje cicha"
                        + " instalacje OEM: nakladka zostaje zdjeta bez okna i zaden"
                        + " onResume jej nie przywraca (patrz AGENTS.md)",
                src.contains("NightShift.pause(c);\n        if (installWithSession"));
        assertTrue("install(): najpierw sesja PackageInstaller (pauza zlecona"
                        + " w receiverze), dopiero potem fallback ACTION_VIEW z pauza",
                src.contains("if (installWithSession(c, apk)) return;        // 2) PackageInstaller\n        NightShift.pause(c);"));
    }

    @Test
    public void pauzaBezposrednioPrzedOknemPotwierdzeniaSesji() throws Exception {
        String src = readSource();
        assertTrue("Receiver STATUS_PENDING_USER_ACTION musi pauzowac PRZED"
                        + " ctx.startActivity(pend) - okno otwarte pod nakladka"
                        + " odcina dotyk (tapjacking)",
                src.contains("NightShift.pause(ctx);\n                        try {\n                            ctx.startActivity(pend);"));
    }

    @Test
    public void pauzaPrzedAwaryjnymActionViewWReceiverze() throws Exception {
        String src = readSource();
        assertTrue("Porazka sesji ratowana ACTION_VIEW tez otwiera okno - pauza"
                        + " musi byc bezposrednio przed installViaView(ctx, apk)",
                src.contains("NightShift.pause(ctx);\n                        installViaView(ctx, apk);"));
    }

    @Test
    public void pauzaPrzedOknemUprawnienIKlasycznymInstalatorem() throws Exception {
        String src = readSource();
        assertTrue("Brak pauzy przed otwarciem ustawien \"nieznane zrodla\"",
                src.contains("NightShift.pause(c);\n                c.startActivity(i);"));
        assertTrue("Brak pauzy przed installViaView(c, apk) w install()",
                src.contains("NightShift.pause(c);                           // 3) pauza przed oknem\n        installViaView(c, apk);"));
    }

    @Test
    public void receiverPauzujeDokladnieDwaRazy() throws Exception {
        String src = readSource();
        int n = src.split("NightShift\\.pause\\(ctx\\);", -1).length - 1;
        assertTrue("Receiver sesji ma pauzowac dokladnie 2x (PENDING_USER_ACTION"
                        + " + porazka z ACTION_VIEW), znaleziono " + n,
                n == 2);
    }

    private static String readSource() throws Exception {
        String rel = "src/main/java/pl/dock/widget/Updater.java";
        File f = new File(rel);
        if (!f.isFile()) f = new File("app/" + rel);
        if (!f.isFile()) throw new IllegalStateException("Nie znalazlem Updater.java"
                + " (cwd=" + new File(".").getAbsolutePath() + ")");
        return new String(Files.readAllBytes(f.toPath()), Charset.forName("UTF-8"));
    }
}
