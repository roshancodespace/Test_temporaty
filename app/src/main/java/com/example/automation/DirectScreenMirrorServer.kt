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
                _connectedClientStatus.value = "Server active at http://${_serverIpAddress.value}:$port. Open link on PC!"

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
                                _connectedClientStatus.value = "PC Screen Live Mirror Active (${bitmap.width}x${bitmap.height} px)"
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
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>CodeFlow PC Screen Streamer</title>
                <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #0b0f19; color: #f1f5f9; text-align: center; padding: 20px 15px; margin: 0; }
                    .card { max-width: 520px; margin: 20px auto; background: #1e293b; padding: 24px; border-radius: 16px; border: 1px solid #334155; box-shadow: 0 10px 25px rgba(0,0,0,0.4); }
                    h2 { color: #38bdf8; margin-top: 0; font-size: 20px; }
                    p { color: #94a3b8; font-size: 13.5px; line-height: 1.5; }
                    .btn { background: #0284c7; color: white; border: none; padding: 12px 24px; font-size: 15px; font-weight: bold; border-radius: 10px; cursor: pointer; transition: 0.2s; margin: 8px 4px; display: inline-block; }
                    .btn:hover { background: #0369a1; }
                    .btn-subtle { background: #334155; color: #cbd5e1; }
                    #status { margin-top: 16px; font-weight: bold; font-size: 13.5px; color: #38bdf8; word-break: break-word; }
                    .drop-zone { border: 2px dashed #0284c7; border-radius: 12px; padding: 20px; margin-top: 15px; background: rgba(2,132,199,0.08); cursor: pointer; }
                    .drop-zone:hover { background: rgba(2,132,199,0.15); }
                    input[type="file"] { display: none; }
                </style>
            </head>
            <body>
                <div class="card">
                    <h2>CodeFlow PC Screen Streamer</h2>
                    <p>Stream your PC desktop or VS Code window live to your phone.</p>
                    
                    <div id="mediaShareBox">
                        <button id="startBtn" class="btn">Share PC Screen Live</button>
                    </div>

                    <div class="drop-zone" id="dropZone">
                        <p style="margin:0; font-weight:bold; color:#cbd5e1;">Press Ctrl+V to paste screenshot</p>
                        <p style="margin:4px 0 0 0; font-size:12px; color:#64748b;">or Drag & Drop image file / click to pick file</p>
                        <input type="file" id="fileInput" accept="image/*">
                    </div>

                    <div id="status">Ready to connect...</div>
                </div>

                <video id="video" autoplay style="display:none;"></video>
                <canvas id="canvas" style="display:none;"></canvas>

                <script>
                    const startBtn = document.getElementById('startBtn');
                    const mediaShareBox = document.getElementById('mediaShareBox');
                    const status = document.getElementById('status');
                    const video = document.getElementById('video');
                    const canvas = document.getElementById('canvas');
                    const dropZone = document.getElementById('dropZone');
                    const fileInput = document.getElementById('fileInput');

                    function uploadFrameData(dataUrl) {
                        fetch('/upload_frame', { method: 'POST', body: dataUrl })
                            .then(() => { status.innerText = "Screen frame sent to phone!"; })
                            .catch(err => { status.innerText = "Stream error: " + err.message; });
                    }

                    // Check if MediaDevices API is available (HTTPS / Localhost)
                    if (navigator.mediaDevices && navigator.mediaDevices.getDisplayMedia) {
                        startBtn.onclick = async () => {
                            try {
                                const stream = await navigator.mediaDevices.getDisplayMedia({ video: { cursor: "always" }, audio: false });
                                video.srcObject = stream;
                                status.innerText = "Screen sharing active! Streaming live to phone...";
                                mediaShareBox.style.display = 'none';

                                const ctx = canvas.getContext('2d');
                                setInterval(() => {
                                    if (video.videoWidth > 0) {
                                        canvas.width = 800;
                                        canvas.height = Math.round((800 / video.videoWidth) * video.videoHeight);
                                        ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
                                        const dataUrl = canvas.toDataURL('image/jpeg', 0.55);
                                        uploadFrameData(dataUrl);
                                    }
                                }, 350);
                            } catch (err) {
                                status.innerText = "Screen share info: " + err.message;
                            }
                        };
                    } else {
                        mediaShareBox.innerHTML = "<p style='color:#f59e0b; font-size:12px; margin:0;'>Note: Web browsers restrict live video capture over non-HTTPS connections. Use <b>Ctrl+V</b> below to paste PC screenshots instantly!</p>";
                    }

                    // Clipboard Paste Handler (Win+Shift+S -> Ctrl+V)
                    window.addEventListener('paste', e => {
                        const items = (e.clipboardData || e.originalEvent.clipboardData).items;
                        for (let item of items) {
                            if (item.type.indexOf('image') !== -1) {
                                const file = item.getAsFile();
                                const reader = new FileReader();
                                reader.onload = evt => { uploadFrameData(evt.target.result); };
                                reader.readAsDataURL(file);
                                status.innerText = "Pasted screenshot uploading to phone...";
                                break;
                            }
                        }
                    });

                    // Drop Zone & File Pick Handler
                    dropZone.onclick = () => fileInput.click();
                    fileInput.onchange = e => {
                        const file = e.target.files[0];
                        if (file) {
                            const reader = new FileReader();
                            reader.onload = evt => uploadFrameData(evt.target.result);
                            reader.readAsDataURL(file);
                        }
                    };

                    dropZone.ondragover = e => e.preventDefault();
                    dropZone.ondrop = e => {
                        e.preventDefault();
                        const file = e.dataTransfer.files[0];
                        if (file && file.type.startsWith('image/')) {
                            const reader = new FileReader();
                            reader.onload = evt => uploadFrameData(evt.target.result);
                            reader.readAsDataURL(file);
                        }
                    };
                </script>
            </body>
            </html>
        """.trimIndent()
    }
}
