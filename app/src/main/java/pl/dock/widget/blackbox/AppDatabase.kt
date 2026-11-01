package pl.dock.widget.blackbox

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

/**
 * Black Box storage – framework SQLite with Write-Ahead Logging.
 *
 * WAL is enabled at the database level (setWriteAheadLoggingEnabled in
 * onConfigure), so readers (CSV export, NanoServer) are never blocked by
 * the massive insert transactions of [BlackBoxRecorder] – the classic
 * ANR killer on slow NAND (MediaTek MT8163).
 *
 * NOTE ON BACKUP: the database files must never be copied raw while WAL
 * is active (the -wal sidecar would corrupt the copy). Exports therefore
 * go through SQL SELECT cursors ([exportCsv]), which is the safe,
 * SQLite-side "online backup" equivalent.
 */
class AppDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, NAME, null, VERSION) {

    companion object {
        private const val TAG = "AppDatabase"
        private const val NAME = "dock_blackbox.db"
        private const val VERSION = 1
        private const val TABLE = "snapshots"
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        // Write-Ahead Logging: readers don't block writers and vice versa
        db.setWriteAheadLoggingEnabled(true)
        try {
            db.execSQL("PRAGMA busy_timeout = 2000;")
        } catch (ignored: Exception) {
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE $TABLE (" +
                "ts INTEGER NOT NULL," +
                "volt REAL, rpm INTEGER, vss INTEGER," +
                "load REAL, tps REAL, mapv REAL," +
                "ect REAL, iat REAL, stft REAL, ltft REAL," +
                "event TEXT)"
        )
        db.execSQL("CREATE INDEX idx_snap_ts ON $TABLE(ts)")
        db.execSQL("CREATE INDEX idx_snap_event ON $TABLE(event)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldV: Int, newV: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE")
        onCreate(db)
    }

    /**
     * One massive transaction – the whole Black Box buffer lands
     * in the database atomically.
     */
    fun insertSnapshots(frames: List<TelemetrySnapshot>) {
        if (frames.isEmpty()) return
        val db = writableDatabase
        db.beginTransactionNonExclusive()   // WAL: readers keep working
        try {
            val st = db.compileStatement(
                "INSERT INTO $TABLE (ts,volt,rpm,vss,load,tps,mapv,ect,iat,stft,ltft,event) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)"
            )
            for (f in frames) {
                st.bindLong(1, f.timestampMs)
                bindOrNull(st, 2, f.voltage)
                bindLongOrNull(st, 3, f.rpm)
                bindLongOrNull(st, 4, f.speedKmh)
                bindOrNull(st, 5, f.loadPct)
                bindOrNull(st, 6, f.tpsPct)
                bindOrNull(st, 7, f.mapKpa)
                bindOrNull(st, 8, f.ectC)
                bindOrNull(st, 9, f.iatC)
                bindOrNull(st, 10, f.stftPct)
                bindOrNull(st, 11, f.ltftPct)
                st.bindString(12, f.event)
                st.executeInsert()
                st.clearBindings()
            }
            db.setTransactionSuccessful()
            Log.i(TAG, "Black Box: ${frames.size} frames persisted")
        } finally {
            db.endTransaction()
        }
    }

    /**
     * CSV export through a SELECT cursor – safe with WAL (no raw file copy).
     */
    fun exportCsv(maxRows: Int = 50_000): String {
        val sb = StringBuilder(TelemetrySnapshot.HEADER.length + 128 * 1024)
        sb.append(TelemetrySnapshot.HEADER).append('\n')
        val db = readableDatabase
        db.acquireReference()
        try {
            db.query(
                TABLE, null, null, null, null, null,
                "ts ASC", maxRows.toString()
            ).use { c ->
                while (c.moveToNext()) {
                    for (i in 0 until c.columnCount) {
                        when (c.getType(i)) {
                            android.database.Cursor.FIELD_TYPE_NULL -> {}
                            android.database.Cursor.FIELD_TYPE_INTEGER ->
                                sb.append(c.getLong(i))
                            android.database.Cursor.FIELD_TYPE_FLOAT ->
                                sb.append(c.getFloat(i))
                            else -> sb.append(c.getString(i).replace(',', ';'))
                        }
                        sb.append(',')
                    }
                    sb.setLength(sb.length - 1)   // drop trailing comma
                    sb.append('\n')
                }
            }
        } finally {
            db.releaseReference()
        }
        return sb.toString()
    }

    fun count(): Long =
        readableDatabase.compileStatement("SELECT COUNT(*) FROM $TABLE")
            .simpleQueryForLong()

    private fun bindOrNull(st: android.database.sqlite.SQLiteStatement, i: Int, v: Float) {
        if (v.isNaN()) st.bindNull(i) else st.bindDouble(i, v.toDouble())
    }

    private fun bindLongOrNull(st: android.database.sqlite.SQLiteStatement, i: Int, v: Int) {
        if (v < 0) st.bindNull(i) else st.bindLong(i, v.toLong())
    }
}
