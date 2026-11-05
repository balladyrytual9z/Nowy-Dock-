package pl.dock.widget.maps;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.location.Location;
import android.view.MotionEvent;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.util.TileSystem;
import org.osmdroid.views.MapView;
import org.osmdroid.views.Projection;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

/**
 * Znacznik pozycji rysowany z WYGLADZONEJ pozycji (patrz {@link HeadingSmoother}).
 *
 * Po co wlasna klasa: {@link MyLocationNewOverlay} ma wlasny sluchacz GPS
 * (GpsMyLocationProvider: GPS + NETWORK, minTime = 0). Ten dostarczal znacznikowi
 * surowe fixy - w tym niedokladne pozycje z sieci - i to razem z 1 Hz GPS dawalo
 * "skaczaca" strzalke. Tutaj zrodlem pozycji jest WYLACZNIE MapState (GPS, 1 Hz),
 * a rysujemy to, co poda {@link #feed}: wartosci wygladzone w czasie.
 *
 * Rysowanie: kolo dokladnosci jak w oryginale, a strzalka kierunku rysowana
 * kodem (szara/jasna z niebieska obwodka) - bialy bitmap osmdroid ginal na
 * jasnej mapie w trybie dziennym.
 */
public class SmoothLocationOverlay extends MyLocationNewOverlay {

    // Kolory strzalki pozycji: w dzien szara z niebieska obwodka (biala ginala
    // na jasnej mapie), w nocy jasna z ta sama niebieska obwodka.
    private static final int ARROW_DAY = 0xFF8E8E93;
    private static final int ARROW_NIGHT = 0xFFE8E8ED;
    private static final int ARROW_RING = 0xFF0A84FF;

    private final Location fix = new Location("dock-smooth");
    private final GeoPoint gp = new GeoPoint(0d, 0d);
    private final Point px = new Point();
    private final Path arrowPath = new Path();
    private final Paint arrowFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arrowRing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private boolean has;
    private boolean arrow;
    private boolean night;
    private Runnable onUserTouch;

    public SmoothLocationOverlay(MapView mapView) {
        super(mapView);
        setDrawAccuracyEnabled(true);
        float d = 1f;
        try {
            d = mapView.getResources().getDisplayMetrics().density;
        } catch (Throwable ignored) {
        }
        density = d <= 0f ? 1f : d;
        arrowRing.setStyle(Paint.Style.STROKE);
        arrowRing.setStrokeJoin(Paint.Join.ROUND);
        arrowRing.setStrokeWidth(2f * density);
        arrowRing.setColor(ARROW_RING);
        arrowFill.setStyle(Paint.Style.FILL);
    }

    /** Motyw jak reszta launchera: dzien / noc (kolor strzalki pozycji). */
    public void setNight(boolean n) {
        night = n;
    }

    /**
     * Wstrzykuje wygladzona pozycje. Swiadomie NIE uzywamy
     * {@code onLocationChanged(location, provider)}: tamto odpala kolejke
     * "on first fix" i w trybie sledzenia wola animateTo na kazdej klatce.
     */
    public void feed(double lat, double lon, float bearingDeg, float accuracyM, boolean moving) {
        fix.setLatitude(lat);
        fix.setLongitude(lon);
        fix.setBearing(bearingDeg);
        fix.setAccuracy(Math.max(0f, accuracyM));
        arrow = moving;
        has = true;
    }

    /** Reakcja na dotkniecie mapy: uzytkownik przesuwa mape => wstrzymaj "Sledz". */
    public void setOnUserTouch(Runnable r) {
        onUserTouch = r;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event, MapView mapView) {
        if (event != null && event.getAction() == MotionEvent.ACTION_DOWN && onUserTouch != null) {
            onUserTouch.run();
        }
        return super.onTouchEvent(event, mapView);
    }

    @Override
    public void draw(Canvas canvas, Projection pj) {
        if (!has || canvas == null || pj == null) return;
        gp.setCoords(fix.getLatitude(), fix.getLongitude());
        pj.toPixels(gp, px);

        // kolo dokladnosci (jak w oryginale)
        if (mDrawAccuracyEnabled && fix.getAccuracy() > 0f) {
            final float radius = fix.getAccuracy() / (float) TileSystem.GroundResolution(
                    fix.getLatitude(), pj.getZoomLevel());
            mCirclePaint.setAlpha(50);
            mCirclePaint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(px.x, px.y, radius, mCirclePaint);
            mCirclePaint.setAlpha(150);
            mCirclePaint.setStyle(Paint.Style.STROKE);
            canvas.drawCircle(px.x, px.y, radius, mCirclePaint);
        }

        if (arrow) {
            // Jedziemy: strzalka kierunku. Canvas jest juz obrocony o orientacje mapy,
            // wiec przy heading-up (mapa obrocona o -kurs) strzalka celuje w gore.
            //
            // Rysujemy ja KODEM, a nie bialym bitmapem osmdroid: biala strzalka
            // ginala na jasnej mapie w dzien. Teraz: szara z niebieska obwodka
            // w dzien, jasna z ta sama obwodka w nocy - widoczna na obu tlach.
            float rot = fix.getBearing();
            if (rot >= 360f) rot -= 360f;
            float r = 13f * density;
            canvas.save();
            canvas.rotate(rot, px.x, px.y);
            arrowPath.reset();
            arrowPath.moveTo(px.x, px.y - r * 1.15f);
            arrowPath.lineTo(px.x - r * 0.72f, px.y + r * 0.92f);
            arrowPath.lineTo(px.x, px.y + r * 0.30f);
            arrowPath.lineTo(px.x + r * 0.72f, px.y + r * 0.92f);
            arrowPath.close();
            arrowFill.setColor(night ? ARROW_NIGHT : ARROW_DAY);
            canvas.drawPath(arrowPath, arrowFill);
            canvas.drawPath(arrowPath, arrowRing);
            canvas.restore();
        } else {
            // Stojmy: ikona osoby, obrocona tak, zeby zostala pionowo mimo obrotu mapy.
            canvas.save();
            canvas.rotate(-mMapView.getMapOrientation(), px.x, px.y);
            canvas.drawBitmap(mPersonBitmap,
                    px.x - mPersonHotspot.x, px.y - mPersonHotspot.y, mPaint);
            canvas.restore();
        }
    }
}
