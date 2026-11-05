package pl.dock.widget.maps;

import android.animation.ValueAnimator;
import android.content.Context;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import pl.dock.widget.Prefs;
import pl.dock.widget.ui.DockTheme;

/**
 * Pochylenie mapy ("3D") - wspolne dla kafelka na ekranie glownym i dla
 * pelnoekranowej Nawigacji, zeby oba widoki wygladaly identycznie.
 *
 * Jak to dziala: osmdroid to biblioteka 2D i nie ma kamery z pochyleniem, wiec
 * pochylamy sam WIDOK mapy (transformacja 3D sprzetowa - zero kosztu procesora):
 *   - obrot wokol dolnej krawedzi ({@link #ANGLE} stopni) - gora odjezdza od widza,
 *   - skala ({@link #scaleFor}) i przesuniecie ({@link #shiftFor}) tak dobrane,
 *     zeby POCHYLONA mapa nadal zakrywala caly ekran (zaden przeswit u gory)
 *     i zeby srodek mapy (czyli nasza pozycja) wypadl mniej wiecej tam, gdzie
 *     w nawigacjach - {@link #MARKER_ANCHOR} wysokosci ekranu.
 *
 * Pochyla sie tylko mapa (razem ze strzalka pozycji i trasa - leza na tej samej
 * plaszczyznie). Paski, plakietka predkosci i przyciski zostaja plaskie.
 *
 * Gest dotykowy dziala: system sam przelicza wspolrzedne przez odwrotnosc
 * transformacji widoku, wiec przesuwanie i zblizanie palcami dziala tez w 3D
 * (przy takim pochyleniu palec i mapa nie ida 1:1 - tak samo jak w innych
 * nawigacjach, inaczej niz w rzucie z gory po prostu nie da sie).
 *
 * Gdyby na urzadzeniu wyszlo, ze gora odchyla sie w zla strone, wystarczy
 * zmienic znak {@link #ANGLE} na przeciwny.
 */
public class MapTilt {

    /** Staly kat pochylenia [stopnie]. Wiekszy = mocniej "polozona" mapa. */
    public static final float ANGLE = 52f;

    /** Gdzie (jaka czesc wysokosci) ma wypasc srodek mapy, czyli nasza pozycja. */
    public static final float MARKER_ANCHOR = 0.58f;

    /** Zapas na zakrycie gory: przesuwamy o tyle razy mniej, ile wynosi MARGIN. */
    public static final float MARGIN = 0.97f;

    /** Wartosc awaryjna, gdyby kat byl nierealny (wtedy nie ma czego liczyc). */
    public static final float COVER = 1.03f;

    /** Odleglosc kamery = tyle razy wysokosc widoku (wieksza = slabsza perspektywa). */
    public static final float CAMERA_FACTOR = 8f;

    private MapTilt() {
    }

    /** Czy tryb 3D jest wlaczony (jedno ustawienie dla kafelka i Nawigacji). */
    public static boolean enabled(Context c) {
        return Prefs.map3d(c);
    }

    /** Odleglosc kamery w pikselach. */
    public static float cameraDist(float h) {
        return Math.max(1f, CAMERA_FACTOR * h);
    }

    /**
     * Skala i przesuniecie liczone z geometrii pochylonej mapy.
     *
     * Po obrocie wokol dolnej krawedzi wysokosc kurczy sie o cos(kata), a perspektywa
     * dodatkowo zmniejsza dal (dzielnik (d + z)). Ukladamy dwa warunki:
     *   - gorna krawedz mapy ma wypasc na 0 (albo wyzej), zeby nie bylo przeswitu,
     *   - srodek mapy (nasza pozycja) ma wypasc w {@link #MARKER_ANCHOR} ekranu.
     * Oba zaleza od skali, wiec rozwiazujemy to kilkoma iteracjami (zbiega szybko).
     * Przesuniecie bierzemy o {@link #MARGIN} mniejsze, dzieki czemu gora jest
     * ZAWSZE zakryta - nadwyzka chowa sie nad ekranem.
     */
    public static float scaleFor(float h) {
        double a = Math.toRadians(ANGLE);
        double cos = Math.cos(a), sin = Math.sin(a);
        double d = cameraDist(h);
        double k = 1d;
        for (int i = 0; i < 20; i++) {                 // punkt stalej zbiezny
            double perspTop = d / (d + k * h * sin);
            double perspMid = d / (d + 0.5d * k * h * sin);
            double diff = perspTop - 0.5d * perspMid;
            if (diff <= 0.0001d) return COVER;
            k = MARKER_ANCHOR / (cos * diff);
        }
        return (float) k;
    }

    /** Przesuniecie mapy w dol [px] - patrz {@link #scaleFor}. */
    public static float shiftFor(float h) {
        double a = Math.toRadians(ANGLE);
        double cos = Math.cos(a), sin = Math.sin(a);
        double d = cameraDist(h);
        double k = scaleFor(h);
        double perspTop = d / (d + k * h * sin);
        double shift = k * h * cos * perspTop - h;     // gorna krawedz dokladnie na 0
        return (float) (shift * MARGIN);
    }

    /** Wysokosc ekranu [px] -> stan mapy. */
    public static void apply(View v, boolean on, boolean animate) {
        if (v == null) return;
        float w = v.getWidth(), h = v.getHeight();
        if (w <= 0f || h <= 0f) return;                // jeszcze nie ma rozmiaru
        v.setPivotX(w / 2f);
        v.setPivotY(h);
        v.setCameraDistance(cameraDist(h));
        float rot = on ? ANGLE : 0f;
        float sc = on ? scaleFor(h) : 1f;
        float sh = on ? shiftFor(h) : 0f;
        if (!animate) {
            v.setRotationX(rot);
            v.setScaleX(sc);
            v.setScaleY(sc);
            v.setTranslationY(sh);
            return;
        }
        final float r0 = v.getRotationX(), s0 = v.getScaleX(), y0 = v.getTranslationY();
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(DockTheme.ANIM_STANDARD);
        a.setInterpolator(new DecelerateInterpolator());
        final View view = v;
        final float r1 = rot, s1 = sc, y1 = sh;
        a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator an) {
                float t = ((Float) an.getAnimatedValue()).floatValue();
                view.setRotationX(r0 + (r1 - r0) * t);
                float sc2 = s0 + (s1 - s0) * t;
                view.setScaleX(sc2);
                view.setScaleY(sc2);
                view.setTranslationY(y0 + (y1 - y0) * t);
            }
        });
        a.start();
    }
}
