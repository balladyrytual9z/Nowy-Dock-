package pl.dock.widget.maps.bookmark;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Wspolna baza modulu map: zakladki (od wersji 2 z folderami i kolorami)
 * + pamiec wyszukiwania (offline search).
 *
 * Jedna klasa = jeden plik bazy (dockmaps.db) i jeden helper - dwa osobne
 * SQLiteOpenHelper na ten sam plik to klasyczna pulapka (onCreate drugiego
 * nigdy by sie nie odpalil). WAL jak w blackbox/AppDatabase - czytanie
 * nie blokuje zapisu.
 */
public final class MapsDb extends SQLiteOpenHelper {

    private static MapsDb instance;

    public static synchronized MapsDb get(Context c) {
        if (instance == null) {
            instance = new MapsDb(c.getApplicationContext());
        }
        return instance;
    }

    private MapsDb(Context c) {
        super(c, "dockmaps.db", null, 2);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS bookmarks ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "name TEXT, "
                + "lat REAL, "
                + "lon REAL, "
                + "added_at INTEGER, "
                + "folder TEXT, "
                + "color INTEGER)");
        db.execSQL("CREATE TABLE IF NOT EXISTS place_cache ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "name TEXT, "
                + "lat REAL, "
                + "lon REAL, "
                + "at INTEGER)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        // v1 -> v2: foldery i kolory zakladek (ALTER w try/catch - kolumna
        // moze juz istniec, gdy tabletska baza byla tworzona recznie)
        if (oldV < 2) {
            try {
                db.execSQL("ALTER TABLE bookmarks ADD COLUMN folder TEXT");
            } catch (Throwable ignored) {
            }
            try {
                db.execSQL("ALTER TABLE bookmarks ADD COLUMN color INTEGER");
            } catch (Throwable ignored) {
            }
        }
    }

    // ---------------- zakladki ----------------

    /** Dodaje zakladke bez folderu - kolor z palety wg liczby istniejacych. */
    public synchronized void addBookmark(String name, double lat, double lon) {
        int color = Bookmark.colorAt(listBookmarks().size());
        addBookmark(name, lat, lon, "", color);
    }

    public synchronized void addBookmark(String name, double lat, double lon,
                                         String folder, int color) {
        ContentValues v = new ContentValues();
        v.put("name", name);
        v.put("lat", lat);
        v.put("lon", lon);
        v.put("added_at", System.currentTimeMillis());
        v.put("folder", folder == null ? "" : folder);
        v.put("color", color);
        getWritableDatabase().insert("bookmarks", null, v);
    }

    public synchronized List<Bookmark> listBookmarks() {
        List<Bookmark> out = new ArrayList<Bookmark>();
        Cursor c = null;
        try {
            c = getReadableDatabase().query("bookmarks",
                    new String[]{"id", "name", "lat", "lon", "added_at", "folder", "color"},
                    null, null, null, null, "folder COLLATE NOCASE ASC, added_at DESC");
            while (c.moveToNext()) {
                out.add(new Bookmark(c.getLong(0), c.getString(1),
                        c.getDouble(2), c.getDouble(3), c.getLong(4),
                        c.getString(5), c.getInt(6)));
            }
        } catch (Throwable ignored) {
        } finally {
            if (c != null) c.close();
        }
        return out;
    }

    public synchronized List<Bookmark> searchBookmarks(String q, int limit) {
        List<Bookmark> out = new ArrayList<Bookmark>();
        if (q == null || q.length() == 0) return out;
        Cursor c = null;
        try {
            c = getReadableDatabase().query("bookmarks",
                    new String[]{"id", "name", "lat", "lon", "added_at", "folder", "color"},
                    "name LIKE ?", new String[]{"%" + q + "%"},
                    null, null, "added_at DESC", String.valueOf(limit));
            while (c.moveToNext()) {
                out.add(new Bookmark(c.getLong(0), c.getString(1),
                        c.getDouble(2), c.getDouble(3), c.getLong(4),
                        c.getString(5), c.getInt(6)));
            }
        } catch (Throwable ignored) {
        } finally {
            if (c != null) c.close();
        }
        return out;
    }

    /** Przenosi zakladke do folderu (pusty string = bez folderu). */
    public synchronized void setBookmarkFolder(long id, String folder) {
        ContentValues v = new ContentValues();
        v.put("folder", folder == null ? "" : folder);
        getWritableDatabase().update("bookmarks", v, "id = ?",
                new String[]{String.valueOf(id)});
    }

    public synchronized void deleteBookmark(long id) {
        getWritableDatabase().delete("bookmarks", "id = ?",
                new String[]{String.valueOf(id)});
    }

    // ---------------- pamiec miejsc (wyszukiwanie offline) ----------------

    /** Zapamietuje wynik wyszukiwania - po to, by offline dało się znalezc
     *  miejsca, ktore kiedys wyszukano (jak "ostatnio szukane" w Organic Maps). */
    public synchronized void cachePlace(String name, double lat, double lon) {
        if (name == null || name.length() == 0) return;
        ContentValues v = new ContentValues();
        v.put("name", name);
        v.put("lat", lat);
        v.put("lon", lon);
        v.put("at", System.currentTimeMillis());
        getWritableDatabase().insert("place_cache", null, v);
    }

    public synchronized List<Bookmark> searchPlaces(String q, int limit) {
        List<Bookmark> out = new ArrayList<Bookmark>();
        if (q == null || q.length() == 0) return out;
        Cursor c = null;
        try {
            c = getReadableDatabase().query("place_cache",
                    new String[]{"id", "name", "lat", "lon", "at"},
                    "name LIKE ?", new String[]{"%" + q + "%"},
                    null, null, "at DESC", String.valueOf(limit));
            while (c.moveToNext()) {
                out.add(new Bookmark(c.getLong(0), c.getString(1),
                        c.getDouble(2), c.getDouble(3), c.getLong(4)));
            }
        } catch (Throwable ignored) {
        } finally {
            if (c != null) c.close();
        }
        return out;
    }
}
