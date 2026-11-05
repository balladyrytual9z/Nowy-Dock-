package pl.dock.widget.maps;

import android.graphics.Canvas;
import android.graphics.Paint;
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
 * Samo rysowanie jest 1:1 z oryginalu (kolo dokladnosci + strzalka kierunku) -
 * zmieniamy tylko zrodlo pozycji, nie wyglad.
 */
public class SmoothLocationOverlay extends MyLocationNewOverlay {

    private final Location fix = new Location("dock-smooth");
    private final GeoPoint gp = new GeoPoint(0d, 0d);
    private final Point px = new Point();
    private boolean has;
    private boolean arrow;
    private Runnable onUserTouch;

    public SmoothLocationOverlay(MapView mapView) {
        super(mapView);
        setDrawAccuracyEnabled(true);
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
            float rot = fix.getBearing();
            if (rot >= 360f) rot -= 360f;
            canvas.save();
            canvas.rotate(rot, px.x, px.y);
            canvas.drawBitmap(mDirectionArrowBitmap,
                    px.x - mDirectionArrowCenterX, px.y - mDirectionArrowCenterY, mPaint);
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
