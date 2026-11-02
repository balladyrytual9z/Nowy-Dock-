package pl.dock.widget;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Serwuje pobrany APK instalatorowi systemowemu (content://) bez AndroidX.
 * Instalator dostaje prawa odczytu przez FLAG_GRANT_READ_URI_PERMISSION.
 */
public class ApkProvider extends ContentProvider {

    private static final String NAME = "update.apk";

    @Override
    public boolean onCreate() {
        return true;
    }

    private File fileFor(Uri uri) {
        try {
            if (!NAME.equals(uri.getLastPathSegment())) return null;
            return new File(new File(getContext().getCacheDir(), "updates"), NAME);
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] args, String sortOrder) {
        File f = fileFor(uri);
        if (f == null || !f.exists()) return null;
        MatrixCursor c = new MatrixCursor(new String[]{
                OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        c.addRow(new Object[]{"CarPlay-Launcher-update.apk", f.length()});
        return c;
    }

    @Override
    public String getType(Uri uri) {
        return "application/vnd.android.package-archive";
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File f = fileFor(uri);
        if (f == null || !f.exists()) throw new FileNotFoundException(uri.toString());
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] args) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] args) {
        return 0;
    }
}
