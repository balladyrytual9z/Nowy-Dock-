package pl.dock.widget.maps.bookmark;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Slady na dysku: pliki GPX w filesDir/tracks (pamiec aplikacji - zadnych
 * uprawnien, przetrwa aktualizacje APK).
 */
public final class TrackFiles {

    private TrackFiles() {
    }

    public static File dir(Context c) {
        File d = new File(c.getFilesDir(), "tracks");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    /** Zapisuje slad; null gdy sie nie udalo. */
    public static File save(Context c, String name, String gpx) {
        try {
            File f = new File(dir(c), safe(name) + ".gpx");
            FileOutputStream os = new FileOutputStream(f);
            os.write(gpx.getBytes("UTF-8"));
            os.close();
            return f;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Lista plikow sladow, najnowszy pierwszy. */
    public static List<File> list(Context c) {
        List<File> out = new ArrayList<File>();
        File[] files = dir(c).listFiles();
        if (files == null) return out;
        for (int i = 0; i < files.length; i++) {
            if (files[i].isFile() && files[i].getName().endsWith(".gpx")) out.add(files[i]);
        }
        Collections.sort(out, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return Long.valueOf(b.lastModified()).compareTo(Long.valueOf(a.lastModified()));
            }
        });
        return out;
    }

    public static String read(Context c, File f) {
        try {
            InputStream is = new FileInputStream(f);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            is.close();
            return new String(bos.toByteArray(), "UTF-8");
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean delete(File f) {
        try {
            return f.delete();
        } catch (Throwable t) {
            return false;
        }
    }

    private static String safe(String s) {
        return s.replaceAll("[^A-Za-z0-9_-]", "_");
    }
}
