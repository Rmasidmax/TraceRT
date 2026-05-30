package com.example.sync

import android.util.Base64
import android.util.Log
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

object SmtpClient {
    private const val TAG = "SmtpClient"

    fun sendEmail(
        host: String,
        port: Int,
        username: String,
        password: String,
        fromEmail: String,
        toEmails: List<String>,
        subject: String,
        bodyText: String,
        csvFileName: String,
        csvContent: String
    ): Boolean {
        if (toEmails.isEmpty()) {
            Log.e(TAG, "No recipients provided")
            return false
        }
        var rawSocket: Socket? = null
        var socket: Socket? = null
        var reader: BufferedReader? = null
        var writer: BufferedWriter? = null

        try {
            Log.d(TAG, "Connecting plain/ssl to $host:$port...")
            rawSocket = Socket()
            rawSocket.connect(InetSocketAddress(host, port), 10000)
            rawSocket.soTimeout = 10000

            var activeSocket = rawSocket

            if (port == 465) {
                // Direct SSL SMTPS
                Log.d(TAG, "Upgrading connection to direct SSL (SMTPS 465)...")
                val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
                val sslSocket = factory.createSocket(rawSocket, host, port, true) as SSLSocket
                sslSocket.startHandshake()
                activeSocket = sslSocket
            }

            socket = activeSocket
            var activeReader = BufferedReader(InputStreamReader(socket.getInputStream(), "UTF-8"))
            var activeWriter = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), "UTF-8"))

            fun localSendLine(line: String) {
                Log.d(TAG, "C: $line")
                activeWriter.write(line + "\r\n")
                activeWriter.flush()
            }

            fun localReadResponse(): String {
                val response = activeReader.readLine() ?: throw Exception("Server closed stream")
                Log.d(TAG, "S: $response")
                return response
            }

            // Read greeting (usually 220)
            var resp = localReadResponse()
            if (!resp.startsWith("220")) throw Exception("Invalid greeting: $resp")

            // Send EHLO
            localSendLine("EHLO localhost")
            while (true) {
                resp = localReadResponse()
                if (resp.length >= 4 && resp[3] == ' ') break
            }

            // If STARTTLS is needed (port 587 or similar, non-465)
            if (port != 465) {
                Log.d(TAG, "Sending STARTTLS...")
                localSendLine("STARTTLS")
                resp = localReadResponse()
                if (resp.startsWith("220")) {
                    Log.d(TAG, "STARTTLS accepted. SSL handshaking...")
                    val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
                    val sslSocket = factory.createSocket(socket, host, port, true) as SSLSocket
                    sslSocket.startHandshake()
                    
                    socket = sslSocket
                    activeReader = BufferedReader(InputStreamReader(socket.getInputStream(), "UTF-8"))
                    activeWriter = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), "UTF-8"))
                    
                    // EHLO again over SSL
                    localSendLine("EHLO localhost")
                    while (true) {
                        resp = localReadResponse()
                        if (resp.length >= 4 && resp[3] == ' ') break
                    }
                } else {
                    Log.w(TAG, "Server did not accept STARTTLS, attempting plain auth...")
                }
            }

            // Authentication
            Log.d(TAG, "Authenticating...")
            localSendLine("AUTH LOGIN")
            resp = localReadResponse()
            if (!resp.startsWith("334")) throw Exception("AUTH LOGIN rejected: $resp")

            // Send Base64 Username
            val b64User = Base64.encodeToString(username.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            localSendLine(b64User)
            resp = localReadResponse()
            if (!resp.startsWith("334")) throw Exception("Username rejected: $resp")

            // Send Base64 Password
            val b64Pass = Base64.encodeToString(password.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            localSendLine(b64Pass)
            resp = localReadResponse()
            if (!resp.startsWith("235")) throw Exception("Authentication failed: $resp")

            // Set envelope sender
            localSendLine("MAIL FROM:<$fromEmail>")
            resp = localReadResponse()
            if (!resp.startsWith("250")) throw Exception("MAIL FROM rejected: $resp")

            // Set envelope recipients
            for (recipient in toEmails) {
                localSendLine("RCPT TO:<$recipient>")
                resp = localReadResponse()
                if (!resp.startsWith("250") && !resp.startsWith("251")) {
                    throw Exception("RCPT TO rejected for $recipient: $resp")
                }
            }

            // Send DATA command
            localSendLine("DATA")
            resp = localReadResponse()
            if (!resp.startsWith("354")) throw Exception("DATA command rejected: $resp")

            // Compile mime structure for attachment
            val boundary = "====Boundary_Trace_RT_4815162342===="
            val toJoined = toEmails.joinToString(", ")

            // Header fields
            val msgHeaders = StringBuilder()
            msgHeaders.append("From: $fromEmail\r\n")
            msgHeaders.append("To: $toJoined\r\n")
            msgHeaders.append("Subject: $subject\r\n")
            msgHeaders.append("MIME-Version: 1.0\r\n")
            msgHeaders.append("Content-Type: multipart/mixed; boundary=\"$boundary\"\r\n")
            msgHeaders.append("\r\n")

            // Body text part
            val msgBody = StringBuilder()
            msgBody.append("--$boundary\r\n")
            msgBody.append("Content-Type: text/plain; charset=utf-8\r\n")
            msgBody.append("Content-Transfer-Encoding: 7bit\r\n")
            msgBody.append("\r\n")
            msgBody.append(bodyText).append("\r\n")
            msgBody.append("\r\n")

            // Attachment part
            val csvB64 = Base64.encodeToString(csvContent.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            val msgAttachment = StringBuilder()
            msgAttachment.append("--$boundary\r\n")
            msgAttachment.append("Content-Type: text/csv; name=\"$csvFileName\"\r\n")
            msgAttachment.append("Content-Transfer-Encoding: base64\r\n")
            msgAttachment.append("Content-Disposition: attachment; filename=\"$csvFileName\"\r\n")
            msgAttachment.append("\r\n")
            msgAttachment.append(csvB64).append("\r\n")
            msgAttachment.append("\r\n")

            // End boundary
            val msgEnd = "--$boundary--\r\n.\r\n"

            // Send actual blocks
            activeWriter.write(msgHeaders.toString())
            activeWriter.write(msgBody.toString())
            activeWriter.write(msgAttachment.toString())
            activeWriter.write(msgEnd)
            activeWriter.flush()

            resp = localReadResponse()
            if (!resp.startsWith("250")) throw Exception("Data delivery acknowledgment rejected: $resp")

            // Quit cleanly
            localSendLine("QUIT")
            try { localReadResponse() } catch (e: Exception) {}

            Log.i(TAG, "Email successfully sent to all recipients via SMTP!")
            
            // Assign outer references to ensure clean closing in finally block
            reader = activeReader
            writer = activeWriter
            return true
        } catch (e: Exception) {
            Log.e(TAG, "SMTP direct send action failed", e)
            return false
        } finally {
            try { writer?.close() } catch (e: Exception) {}
            try { reader?.close() } catch (e: Exception) {}
            try { socket?.close() } catch (e: Exception) {}
            try { rawSocket?.close() } catch (e: Exception) {}
        }
    }
}
