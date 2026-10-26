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
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.math.BigInteger
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket

class DirectScreenMirrorServer(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    private var httpServerSocket: ServerSocket? = null
    private var httpsServerSocket: SSLServerSocket? = null
    private var httpJob: Job? = null
    private var httpsJob: Job? = null

    private var currentHttpPort: Int = 8080
    private var currentHttpsPort: Int = 8443
    private var cachedCertPem: ByteArray = ByteArray(0)

    private val _isServerRunning = MutableStateFlow(false)
    val isServerRunning: StateFlow<Boolean> = _isServerRunning.asStateFlow()

    private val _serverIpAddress = MutableStateFlow("127.0.0.1")
    val serverIpAddress: StateFlow<String> = _serverIpAddress.asStateFlow()

    private val _httpsPortFlow = MutableStateFlow(8443)
    val httpsPort: StateFlow<Int> = _httpsPortFlow.asStateFlow()

    private val _isHttpsAvailable = MutableStateFlow(false)
    val isHttpsAvailable: StateFlow<Boolean> = _isHttpsAvailable.asStateFlow()

    private val _currentCapturedFrame = MutableStateFlow<Bitmap?>(null)
    val currentCapturedFrame: StateFlow<Bitmap?> = _currentCapturedFrame.asStateFlow()

    private val _connectedClientStatus = MutableStateFlow("Waiting for PC connection...")
    val connectedClientStatus: StateFlow<String> = _connectedClientStatus.asStateFlow()

    fun startServer(httpPort: Int = 8080, httpsPort: Int = 8443) {
        stopServer()
        currentHttpPort = httpPort
        currentHttpsPort = httpsPort
        _httpsPortFlow.value = httpsPort

        val ip = getLocalIpAddress()
        _serverIpAddress.value = ip

        // Start HTTPS server with dynamically generated IP-SAN self-signed certificate
        httpsJob = scope.launch {
            try {
                val sslContext = createSelfSignedSslContext(ip)
                val sslSocket = sslContext.serverSocketFactory.createServerSocket(httpsPort) as SSLServerSocket
                httpsServerSocket = sslSocket
                _isHttpsAvailable.value = true
                _isServerRunning.value = true
                _connectedClientStatus.value =
                    "Active: https://$ip:$httpsPort (Live Screen Share) | http://$ip:$httpPort (Setup)"

                while (_isServerRunning.value) {
                    val clientSocket = sslSocket.accept() ?: break
                    handleClient(clientSocket)
                }
            } catch (_: Exception) {
                _isHttpsAvailable.value = false
            }
        }

        // Start HTTP bootstrap & fallback server
        httpJob = scope.launch {
            try {
                httpServerSocket = ServerSocket(httpPort)
                _isServerRunning.value = true
                if (!_isHttpsAvailable.value) {
                    _connectedClientStatus.value =
                        "Active: https://$ip:$httpsPort (Live Screen Share) | http://$ip:$httpPort (Setup)"
                }

                while (_isServerRunning.value) {
                    val clientSocket = httpServerSocket?.accept() ?: break
                    handleClient(clientSocket)
                }
            } catch (_: Exception) {
                if (!_isHttpsAvailable.value) {
                    _connectedClientStatus.value = "Server stopped or port busy."
                    _isServerRunning.value = false
                }
            }
        }
    }

    private fun handleClient(socket: Socket) {
        scope.launch {
            try {
                socket.soTimeout = 10_000
                val input = BufferedReader(InputStreamReader(socket.getInputStream()))
                val output = socket.getOutputStream()

                val requestLine = input.readLine() ?: return@launch

                when {
                    requestLine.startsWith("GET /cert.crt") -> {
                        val headers = mutableMapOf(
                            "Content-Disposition" to "attachment; filename=\"codeflow-lan-ca.crt\""
                        )
                        sendHttpResponse(
                            output = output,
                            status = "200 OK",
                            contentType = "application/x-x509-ca-cert",
                            body = cachedCertPem,
                            extraHeaders = headers
                        )
                    }
                    requestLine.startsWith("GET / ") || requestLine.startsWith("GET /index.html") -> {
                        val html = getHtmlPage(
                            ip = _serverIpAddress.value,
                            httpPort = currentHttpPort,
                            httpsPort = currentHttpsPort
                        )
                        sendHttpResponse(output, "200 OK", "text/html; charset=utf-8", html.toByteArray())
                    }
                    requestLine.startsWith("POST /upload_frame") -> {
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

                            val dataUrl = String(buffer, 0, read)
                            if (dataUrl.contains(",")) {
                                val base64Data = dataUrl.substringAfter(",")
                                val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                if (bitmap != null) {
                                    _currentCapturedFrame.value = bitmap
                                    _connectedClientStatus.value =
                                        "PC Screen Live Mirror Active (${bitmap.width}x${bitmap.height} px)"
                                }
                            }
                        }
                        sendHttpResponse(output, "200 OK", "text/plain", "OK".toByteArray())
                    }
                    requestLine.startsWith("OPTIONS ") -> {
                        sendHttpResponse(output, "204 No Content", "text/plain", ByteArray(0))
                    }
                    else -> {
                        sendHttpResponse(output, "404 Not Found", "text/plain", "Not Found".toByteArray())
                    }
                }
            } catch (_: Exception) {
            } finally {
                runCatching { socket.close() }
            }
        }
    }

    private fun sendHttpResponse(
        output: OutputStream,
        status: String,
        contentType: String,
        body: ByteArray,
        extraHeaders: Map<String, String> = emptyMap()
    ) {
        val sb = StringBuilder()
        sb.append("HTTP/1.1 $status\r\n")
        sb.append("Content-Type: $contentType\r\n")
        sb.append("Content-Length: ${body.size}\r\n")
        sb.append("Access-Control-Allow-Origin: *\r\n")
        sb.append("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
        for ((k, v) in extraHeaders) {
            sb.append("$k: $v\r\n")
        }
        sb.append("Connection: close\r\n\r\n")
        output.write(sb.toString().toByteArray())
        output.write(body)
        output.flush()
    }

    fun stopServer() {
        _isServerRunning.value = false
        _isHttpsAvailable.value = false
        runCatching { httpServerSocket?.close() }
        runCatching { httpsServerSocket?.close() }
        httpServerSocket = null
        httpsServerSocket = null
        httpJob?.cancel()
        httpsJob?.cancel()
        httpJob = null
        httpsJob = null
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
        } catch (_: Exception) {
        }
        return "127.0.0.1"
    }

    /**
     * Generates an in-memory RSA-2048 keypair and an RFC 5280 X.509v3 certificate
     * containing a SubjectAlternativeName (SAN) for the phone's current LAN IPv4 address.
     */
    private fun createSelfSignedSslContext(ipAddress: String): SSLContext {
        val keyPairGen = KeyPairGenerator.getInstance("RSA")
        keyPairGen.initialize(2048, SecureRandom())
        val keyPair = keyPairGen.generateKeyPair()

        val certDer = generateSelfSignedX509Der(ipAddress, keyPair.public.encoded, keyPair.private)
        val pemBody = Base64.encodeToString(certDer, Base64.DEFAULT).trim()
        val pemString = "-----BEGIN CERTIFICATE-----\n$pemBody\n-----END CERTIFICATE-----\n"
        cachedCertPem = pemString.toByteArray(Charsets.US_ASCII)

        val certFactory = CertificateFactory.getInstance("X.509")
        val x509Cert = certFactory.generateCertificate(ByteArrayInputStream(certDer)) as X509Certificate

        val password = "changeit".toCharArray()
        val keyStore = KeyStore.getInstance("PKCS12")
        keyStore.load(null, null)
        keyStore.setKeyEntry("codeflow_lan", keyPair.private, password, arrayOf(x509Cert))

        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(keyStore, password)

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(kmf.keyManagers, null, SecureRandom())
        return sslContext
    }

    private fun generateSelfSignedX509Der(
        ipAddress: String,
        subjectPublicKeyInfoDer: ByteArray,
        privateKey: java.security.PrivateKey
    ): ByteArray {
        val now = System.currentTimeMillis()
        val notBefore = Date(now - 86_400_000L)
        val notAfter = Date(now + 365L * 86_400_000_000L / 1000L)

        // OIDs
        val oidSha256WithRsa = derOid(intArrayOf(1, 2, 840, 113549, 1, 1, 11))
        val oidCommonName = derOid(intArrayOf(2, 5, 4, 3))
        val oidOrgName = derOid(intArrayOf(2, 5, 4, 10))
        val oidBasicConstraints = derOid(intArrayOf(2, 5, 29, 19))
        val oidKeyUsage = derOid(intArrayOf(2, 5, 29, 15))
        val oidExtKeyUsage = derOid(intArrayOf(2, 5, 29, 37))
        val oidServerAuth = derOid(intArrayOf(1, 3, 6, 1, 5, 5, 7, 3, 1))
        val oidSubjectAltName = derOid(intArrayOf(2, 5, 29, 17))

        val sigAlgId = derSeq(oidSha256WithRsa, derNull())

        val rdnCn = derSet(derSeq(oidCommonName, derUtf8String(ipAddress)))
        val rdnOrg = derSet(derSeq(oidOrgName, derUtf8String("CodeFlow LAN Server")))
        val x500Name = derSeq(rdnOrg, rdnCn)

        val validity = derSeq(derUtcTime(notBefore), derUtcTime(notAfter))
        val versionV3 = derExplicit(0, derInteger(BigInteger.valueOf(2)))
        val serialNumber = derInteger(BigInteger(64, SecureRandom()).abs().add(BigInteger.ONE))

        // Extensions: BasicConstraints(CA:TRUE), KeyUsage, ExtendedKeyUsage(serverAuth), SAN(IP + localhost)
        val basicConstraintsExt = derSeq(
            oidBasicConstraints,
            derBooleanTrue(),
            derOctetString(derSeq(derBooleanTrue()))
        )

        // KeyUsage: digitalSignature (bit 0), keyEncipherment (bit 2), keyCertSign (bit 5) -> 0xA4 (2 unused bits)
        val keyUsageBitString = derTag(0x03, byteArrayOf(0x02, 0xA4.toByte()))
        val keyUsageExt = derSeq(
            oidKeyUsage,
            derBooleanTrue(),
            derOctetString(keyUsageBitString)
        )

        val extKeyUsageExt = derSeq(
            oidExtKeyUsage,
            derOctetString(derSeq(oidServerAuth))
        )

        val ipBytes = runCatching { InetAddress.getByName(ipAddress).address }
            .getOrDefault(byteArrayOf(127, 0, 0, 1))
        val sanIpEntry = derTag(0x87, ipBytes) // [7] iPAddress
        val sanDnsEntry = derTag(0x82, "localhost".toByteArray(Charsets.US_ASCII)) // [2] dNSName
        val sanExt = derSeq(
            oidSubjectAltName,
            derOctetString(derSeq(sanIpEntry, sanDnsEntry))
        )

        val extensions = derExplicit(3, derSeq(basicConstraintsExt, keyUsageExt, extKeyUsageExt, sanExt))

        val tbsCertificate = derSeq(
            versionV3,
            serialNumber,
            sigAlgId,
            x500Name,
            validity,
            x500Name,
            subjectPublicKeyInfoDer,
            extensions
        )

        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(privateKey)
        signer.update(tbsCertificate)
        val signatureBytes = signer.sign()

        return derSeq(
            tbsCertificate,
            sigAlgId,
            derBitString(signatureBytes)
        )
    }

    private fun derTag(tag: Int, content: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(tag)
        val len = content.size
        if (len < 128) {
            out.write(len)
        } else {
            val lenBytes = mutableListOf<Byte>()
            var temp = len
            while (temp > 0) {
                lenBytes.add(0, (temp and 0xFF).toByte())
                temp = temp ushr 8
            }
            out.write(0x80 or lenBytes.size)
            for (b in lenBytes) out.write(b.toInt() and 0xFF)
        }
        out.write(content)
        return out.toByteArray()
    }

    private fun derSeq(vararg items: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        for (item in items) out.write(item)
        return derTag(0x30, out.toByteArray())
    }

    private fun derSet(vararg items: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        for (item in items) out.write(item)
        return derTag(0x31, out.toByteArray())
    }

    private fun derExplicit(tagNumber: Int, content: ByteArray): ByteArray =
        derTag(0xA0 or tagNumber, content)

    private fun derInteger(value: BigInteger): ByteArray =
        derTag(0x02, value.toByteArray())

    private fun derBitString(content: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(0x00)
        out.write(content)
        return derTag(0x03, out.toByteArray())
    }

    private fun derOctetString(content: ByteArray): ByteArray =
        derTag(0x04, content)

    private fun derBooleanTrue(): ByteArray =
        byteArrayOf(0x01, 0x01, 0xFF.toByte())

    private fun derNull(): ByteArray =
        byteArrayOf(0x05, 0x00)

    private fun derUtf8String(value: String): ByteArray =
        derTag(0x0C, value.toByteArray(Charsets.UTF_8))

    private fun derUtcTime(date: Date): ByteArray {
        val sdf = SimpleDateFormat("yyMMddHHmmss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return derTag(0x17, sdf.format(date).toByteArray(Charsets.US_ASCII))
    }

    private fun derOid(components: IntArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(components[0] * 40 + components[1])
        for (i in 2 until components.size) {
            var v = components[i]
            val bytes = mutableListOf<Byte>()
            bytes.add((v and 0x7F).toByte())
            v = v ushr 7
            while (v > 0) {
                bytes.add(0, ((v and 0x7F) or 0x80).toByte())
                v = v ushr 7
            }
            for (b in bytes) out.write(b.toInt() and 0xFF)
        }
        return derTag(0x06, out.toByteArray())
    }

    private fun getHtmlPage(ip: String, httpPort: Int, httpsPort: Int): String {
        val httpsUrl = "https://$ip:$httpsPort"
        val httpOrigin = "http://$ip:$httpPort"
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>CodeFlow PC Screen Streamer</title>
                <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #0b0f19; color: #f1f5f9; text-align: center; padding: 20px 15px; margin: 0; }
                    .card { max-width: 560px; margin: 16px auto; background: #1e293b; padding: 24px; border-radius: 16px; border: 1px solid #334155; box-shadow: 0 10px 25px rgba(0,0,0,0.4); }
                    h2 { color: #38bdf8; margin-top: 0; font-size: 20px; }
                    p { color: #94a3b8; font-size: 13.5px; line-height: 1.5; }
                    .badge { display: inline-block; padding: 4px 10px; border-radius: 999px; font-size: 11.5px; font-weight: 700; margin-bottom: 12px; }
                    .badge-ok { background: rgba(34,197,94,0.18); color: #4ade80; border: 1px solid rgba(34,197,94,0.4); }
                    .badge-warn { background: rgba(245,158,11,0.18); color: #fbbf24; border: 1px solid rgba(245,158,11,0.4); }
                    .btn { background: #0284c7; color: white; border: none; padding: 12px 22px; font-size: 14.5px; font-weight: bold; border-radius: 10px; cursor: pointer; transition: 0.2s; margin: 6px 4px; display: inline-block; text-decoration: none; }
                    .btn:hover { background: #0369a1; }
                    .btn-green { background: #16a34a; }
                    .btn-green:hover { background: #15803d; }
                    .btn-subtle { background: #334155; color: #e2e8f0; font-size: 12.5px; padding: 8px 14px; }
                    .setup-box { text-align: left; background: #0f172a; border: 1px solid #334155; border-radius: 12px; padding: 14px; margin-top: 14px; font-size: 12.5px; color: #cbd5e1; }
                    .setup-box code { background: #1e293b; padding: 2px 6px; border-radius: 4px; color: #38bdf8; font-family: monospace; word-break: break-all; }
                    #status { margin-top: 16px; font-weight: bold; font-size: 13.5px; color: #38bdf8; word-break: break-word; }
                    .drop-zone { border: 2px dashed #0284c7; border-radius: 12px; padding: 18px; margin-top: 16px; background: rgba(2,132,199,0.08); cursor: pointer; }
                    .drop-zone:hover { background: rgba(2,132,199,0.15); }
                    input[type="file"] { display: none; }
                </style>
            </head>
            <body>
                <div class="card">
                    <h2>CodeFlow PC Screen Streamer</h2>
                    <div id="contextBadge" class="badge badge-warn">Checking browser security context...</div>
                    <p style="margin-top:0;">Stream your PC desktop or VS Code window live to your phone.</p>

                    <div id="mediaShareBox">
                        <button id="startBtn" class="btn">Share PC Screen Live (getDisplayMedia)</button>
                        <button id="stopBtn" class="btn btn-subtle" style="display:none;">Stop Live Stream</button>
                    </div>

                    <div id="insecureHelpBox" class="setup-box" style="display:none;">
                        <div style="font-weight:bold; color:#fbbf24; margin-bottom:6px;">
                            Why is live capture blocked on plain HTTP?
                        </div>
                        <div style="color:#94a3b8; margin-bottom:10px;">
                            Browsers require a <b>Secure Context</b> for <code>navigator.mediaDevices.getDisplayMedia()</code>. Plain HTTP over a LAN IP (<code>$httpOrigin</code>) is blocked by default. Choose either option below to unlock live streaming:
                        </div>
                        <div style="margin-bottom:10px;">
                            <b>Option 1 (Recommended — Works in All Browsers):</b><br>
                            Use the phone's built-in HTTPS server on port <b>$httpsPort</b>. On first visit, click <i>Advanced &rarr; Proceed to $ip</i> (or install the phone's cert):<br>
                            <div style="margin-top:8px;">
                                <a class="btn btn-green" href="$httpsUrl">Open HTTPS Streamer ($httpsUrl)</a>
                                <a class="btn btn-subtle" href="/cert.crt">Download LAN Certificate (.crt)</a>
                            </div>
                        </div>
                        <div>
                            <b>Option 2 (Chrome / Edge / Brave Flag on Plain HTTP):</b><br>
                            Open <code>chrome://flags/#unsafely-treat-insecure-origin-as-secure</code>, add <code>$httpOrigin</code>, set to <b>Enabled</b>, and relaunch your browser.
                            <div style="margin-top:6px;">
                                <button id="copyFlagBtn" class="btn btn-subtle">Copy Chrome Flag Origin ($httpOrigin)</button>
                            </div>
                        </div>
                    </div>

                    <div class="drop-zone" id="dropZone">
                        <p style="margin:0; font-weight:bold; color:#cbd5e1;">Fallback Snapshot: Press Ctrl+V to paste screenshot</p>
                        <p style="margin:4px 0 0 0; font-size:12px; color:#64748b;">Works on plain HTTP without setup (sends a static snapshot, not live video) • or Drag &amp; Drop / click to pick image</p>
                        <input type="file" id="fileInput" accept="image/*">
                    </div>

                    <div id="status">Ready to connect...</div>
                </div>

                <video id="video" autoplay playsinline muted style="display:none;"></video>
                <canvas id="canvas" style="display:none;"></canvas>

                <script>
                    const startBtn = document.getElementById('startBtn');
                    const stopBtn = document.getElementById('stopBtn');
                    const contextBadge = document.getElementById('contextBadge');
                    const insecureHelpBox = document.getElementById('insecureHelpBox');
                    const copyFlagBtn = document.getElementById('copyFlagBtn');
                    const status = document.getElementById('status');
                    const video = document.getElementById('video');
                    const canvas = document.getElementById('canvas');
                    const dropZone = document.getElementById('dropZone');
                    const fileInput = document.getElementById('fileInput');
                    let streamInterval = null;
                    let activeStream = null;

                    function uploadFrameData(dataUrl) {
                        fetch('/upload_frame', { method: 'POST', body: dataUrl })
                            .then(() => { status.innerText = "Live frame received by phone (" + new Date().toLocaleTimeString() + ")"; })
                            .catch(err => { status.innerText = "Stream error: " + err.message; });
                    }

                    const canUseDisplayMedia = Boolean(
                        window.isSecureContext &&
                        navigator.mediaDevices &&
                        typeof navigator.mediaDevices.getDisplayMedia === 'function'
                    );

                    if (canUseDisplayMedia) {
                        contextBadge.className = 'badge badge-ok';
                        contextBadge.innerText = 'Secure Context Active (' + window.location.protocol.replace(':','').toUpperCase() + ') — Live Screen Share Ready';
                        insecureHelpBox.style.display = 'none';
                    } else {
                        contextBadge.className = 'badge badge-warn';
                        contextBadge.innerText = 'Plain HTTP LAN Origin — Secure Context Required for Live Capture';
                        insecureHelpBox.style.display = 'block';
                    }

                    if (copyFlagBtn) {
                        copyFlagBtn.onclick = () => {
                            navigator.clipboard?.writeText('$httpOrigin');
                            status.innerText = "Copied $httpOrigin — paste it into chrome://flags/#unsafely-treat-insecure-origin-as-secure";
                        };
                    }

                    startBtn.onclick = async () => {
                        if (!navigator.mediaDevices || !navigator.mediaDevices.getDisplayMedia) {
                            insecureHelpBox.style.display = 'block';
                            status.innerText = "getDisplayMedia() requires HTTPS ($httpsUrl) or enabling the Chrome flag below.";
                            return;
                        }
                        try {
                            const stream = await navigator.mediaDevices.getDisplayMedia({
                                video: { cursor: "always" },
                                audio: false
                            });
                            activeStream = stream;
                            video.srcObject = stream;
                            status.innerText = "Screen sharing active! Streaming live to phone...";
                            startBtn.style.display = 'none';
                            stopBtn.style.display = 'inline-block';

                            const ctx = canvas.getContext('2d');
                            if (streamInterval) clearInterval(streamInterval);
                            streamInterval = setInterval(() => {
                                if (video.videoWidth > 0) {
                                    canvas.width = 800;
                                    canvas.height = Math.round((800 / video.videoWidth) * video.videoHeight);
                                    ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
                                    const dataUrl = canvas.toDataURL('image/jpeg', 0.55);
                                    uploadFrameData(dataUrl);
                                }
                            }, 350);

                            const tracks = stream.getVideoTracks();
                            if (tracks.length > 0) {
                                tracks[0].onended = () => stopCapture();
                            }
                        } catch (err) {
                            status.innerText = "Screen share error: " + err.message;
                        }
                    };

                    function stopCapture() {
                        if (streamInterval) {
                            clearInterval(streamInterval);
                            streamInterval = null;
                        }
                        if (activeStream) {
                            activeStream.getTracks().forEach(t => t.stop());
                            activeStream = null;
                        }
                        startBtn.style.display = 'inline-block';
                        stopBtn.style.display = 'none';
                        status.innerText = "Live screen sharing stopped.";
                    }

                    stopBtn.onclick = () => stopCapture();

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
