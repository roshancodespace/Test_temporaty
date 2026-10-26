package com.example.automation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket

class DirectScreenMirrorServer(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null

    private val _isServerRunning = MutableStateFlow(false)
    val isServerRunning: StateFlow<Boolean> = _isServerRunning.asStateFlow()

    private val _serverIpAddress = MutableStateFlow("127.0.0.1")
    val serverIpAddress: StateFlow<String> = _serverIpAddress.asStateFlow()

    private val _currentCapturedFrame = MutableStateFlow<Bitmap?>(null)
    val currentCapturedFrame: StateFlow<Bitmap?> = _currentCapturedFrame.asStateFlow()

    private val _connectedClientStatus = MutableStateFlow("Waiting for PC connection...")
    val connectedClientStatus: StateFlow<String> = _connectedClientStatus.asStateFlow()

    fun startServer(port: Int = 8080) {
        stopServer()
        _serverIpAddress.value = getLocalIpAddress()

        serverJob = scope.launch {
            try {
                serverSocket = ServerSocket(port)
                _isServerRunning.value = true
                _connectedClientStatus.value = "Server active at http://${_serverIpAddress.value}:$port. Open link on PC to share screen!"

                while (_isServerRunning.value) {
                    val clientSocket = serverSocket?.accept() ?: break
                    handleClient(clientSocket)
                }
            } catch (e: Exception) {
                _connectedClientStatus.value = "Server stopped or port busy."
                _isServerRunning.value = false
            }
        }
    }

    private fun handleClient(socket: Socket) {
        scope.launch {
            try {
                val input = BufferedReader(InputStreamReader(socket.getInputStream()))
                val output = socket.getOutputStream()

                val requestLine = input.readLine() ?: return@launch

                if (requestLine.startsWith("GET / ")) {
                    val html = getHtmlPage()
                    sendHttpResponse(output, "200 OK", "text/html", html.toByteArray())
                } else if (requestLine.startsWith("POST /upload_frame")) {
                    var contentLength = 0
                    var line: String?
                    while (input.readLine().also { line = it } != null && line!!.isNotBlank()) {
                        if (line!!.lowercase().startsWith("content-length:")) {
                            contentLength = line!!.substringAfter(":").trim().toIntOrNull() ?: 0
                        }
                    }

                    if (contentLength > 0) {
                        val buffer = CharArray(contentLength)
                        var read = 0
                        while (read < contentLength) {
                            val r = input.read(buffer, read, contentLength - read)
                            if (r == -1) break
                            read += r
                        }

                        val dataUrl = String(buffer)
                        if (dataUrl.contains(",")) {
                            val base64Data = dataUrl.substringAfter(",")
                            val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            if (bitmap != null) {
                                _currentCapturedFrame.value = bitmap
                                _connectedClientStatus.value = "PC Screen Live Mirroring Active (${bitmap.width}x${bitmap.height} px)"
                            }
                        }
                    }
                    sendHttpResponse(output, "200 OK", "text/plain", "OK".toByteArray())
                } else {
                    sendHttpResponse(output, "404 Not Found", "text/plain", "Not Found".toByteArray())
                }
            } catch (_: Exception) {
            } finally {
                runCatching { socket.close() }
            }
        }
    }

    private fun sendHttpResponse(output: OutputStream, status: String, contentType: String, body: ByteArray) {
        val header = "HTTP/1.1 $status\r\n" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: ${body.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n"
        output.write(header.toByteArray())
        output.write(body)
        output.flush()
    }

    fun stopServer() {
        _isServerRunning.value = false
        runCatching { serverSocket?.close() }
        serverSocket = null
        serverJob?.cancel()
        serverJob = null
        _connectedClientStatus.value = "Server stopped."
    }

    private fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (_: Exception) {}
        return "127.0.0.1"
    }

    private fun getHtmlPage(): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <title>CodeFlow Direct PC Screen Mirror</title>
                <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #090d16; color: #f8fafc; text-align: center; padding: 40px 20px; }
                    .card { max-width: 500px; margin: 0 auto; background: #1e293b; padding: 30px; border-radius: 16px; border: 1px solid #334155; box-shadow: 0 10px 25px rgba(0,0,0,0.5); }
                    h2 { color: #38bdf8; margin-bottom: 8px; }
                    p { color: #94a3b8; font-size: 14px; line-height: 1.5; }
                    button { background: #0284c7; color: white; border: none; padding: 14px 28px; font-size: 16px; font-weight: bold; border-radius: 10px; cursor: pointer; transition: 0.2s; margin-top: 20px; }
                    button:hover { background: #0369a1; }
                    #status { margin-top: 20px; font-weight: bold; font-size: 14px; color: #4ade80; }
                </style>
            </head>
            <body>
                <div class="card">
                    <h2>CodeFlow PC Screen Mirror</h2>
                    <p>Stream your PC monitor or VS Code window directly to your Android phone without cameras.</p>
                    <button id="startBtn">Share PC Screen Now</button>
                    <div id="status">Ready to connect...</div>
                </div>
                <video id="video" autoplay style="display:none;"></video>
                <canvas id="canvas" style="display:none;"></canvas>

                <script>
                    const startBtn = document.getElementById('startBtn');
                    const status = document.getElementById('status');
                    const video = document.getElementById('video');
                    const canvas = document.getElementById('canvas');

                    startBtn.onclick = async () => {
                        try {
                            const stream = await navigator.mediaDevices.getDisplayMedia({ video: { cursor: "always" }, audio: false });
                            video.srcObject = stream;
                            status.innerText = "Screen sharing active! Streaming PC desktop live to Android phone...";
                            startBtn.style.display = 'none';

                            const ctx = canvas.getContext('2d');
                            setInterval(() => {
                                if (video.videoWidth > 0) {
                                    canvas.width = 800;
                                    canvas.height = Math.round((800 / video.videoWidth) * video.videoHeight);
                                    ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
                                    const dataUrl = canvas.toDataURL('image/jpeg', 0.55);
                                    fetch('/upload_frame', { method: 'POST', body: dataUrl }).catch(() => {});
                                }
                            }, 350);
                        } catch (err) {
                            status.innerText = "Screen share cancelled or error: " + err.message;
                        }
                    };
                </script>
            </body>
            </html>
        """.trimIndent()
    }
}
