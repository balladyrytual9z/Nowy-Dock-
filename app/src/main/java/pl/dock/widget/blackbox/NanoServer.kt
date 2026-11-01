package pl.dock.widget.blackbox

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets

/**
 * NanoServer – minimal HTTP server for safe Black Box CSV export.
 *
 * Runs on the car's Wi-Fi (default port 8080). Read-only:
 *   GET /        – status page (HTML)
 *   GET /csv     – full CSV export of the black box
 *   GET /health  – tiny JSON status
 *
 * Export goes through AppDatabase SQL cursors (WAL-safe),
 * never by copying the raw database files.
 */
class NanoServer(
    private val context: Context,
    private val db: AppDatabase,
    private val recorder: BlackBoxRecorder,
    private val port: Int = DEFAULT_PORT
) {

    companion object {
        const val DEFAULT_PORT = 8080
        private const val TAG = "NanoServer"
    }

    private var serverSocket: ServerSocket? = null
    @Volatile private var running = false

    fun start() {
        if (running) return
        running = true
        Thread({
            try {
                val ss = ServerSocket(port)
                serverSocket = ss
                Log.i(TAG, "NanoServer on port $port")
                while (running && !ss.isClosed) {
                    val client = ss.accept()
                    Thread { handle(client) }.also {
                        it.isDaemon = true
                        it.name = "nano-http"
                        it.start()
                    }
                }
            } catch (e: Exception) {
                if (running) Log.w(TAG, "NanoServer stopped: ${e.message}")
            }
        }, "nano-server").also {
            it.isDaemon = true
            it.start()
        }
    }

    fun stop() {
        running = false
        try { serverSocket?.close() } catch (ignored: Exception) {}
        serverSocket = null
    }

    private fun handle(client: Socket) {
        try {
            client.soTimeout = 5_000
            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
            val line = reader.readLine() ?: return
            val path = line.split(' ').getOrNull(1) ?: "/"

            when {
                path.startsWith("/csv") -> respond(
                    client, "200 OK", "text/csv; charset=utf-8",
                    db.exportCsv().toByteArray(StandardCharsets.UTF_8)
                )
                path.startsWith("/health") -> respond(
                    client, "200 OK", "application/json; charset=utf-8",
                    healthJson().toByteArray(StandardCharsets.UTF_8)
                )
                else -> respond(
                    client, "200 OK", "text/html; charset=utf-8",
                    indexHtml().toByteArray(StandardCharsets.UTF_8)
                )
            }
        } catch (ignored: Exception) {
        } finally {
            try { client.close() } catch (ignored: Exception) {}
        }
    }

    private fun respond(client: Socket, status: String, contentType: String, body: ByteArray) {
        val out: OutputStream = client.getOutputStream()
        out.write("HTTP/1.1 $status\r\n".toByteArray())
        out.write("Content-Type: $contentType\r\n".toByteArray())
        out.write("Content-Length: ${body.size}\r\n".toByteArray())
        out.write("Connection: close\r\n\r\n".toByteArray())
        out.write(body)
        out.flush()
    }

    private fun healthJson(): String =
        "{\"frames\":${recorder.size()}," +
            "\"totalFlushed\":${recorder.totalFlushed}," +
            "\"rows\":${db.count()}}"

    private fun indexHtml(): String = """
        <!DOCTYPE html>
        <html><head><meta charset="utf-8"><title>Dock Black Box</title></head>
        <body style="font-family:sans-serif;background:#111;color:#eee;padding:24px">
        <h2>Dock 2.0 &middot; Black Box</h2>
        <p>Ramki w buforze: <b>${recorder.size()}</b> / ${BlackBoxRecorder.CAPACITY}</p>
        <p>Zapisane zdarzenia: <b>${db.count()}</b></p>
        <p><a style="color:#0a84ff" href="/csv">Pobierz CSV</a></p>
        </body></html>
    """.trimIndent()
}
