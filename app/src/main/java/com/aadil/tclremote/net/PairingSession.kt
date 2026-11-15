package com.aadil.tclremote.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.math.BigInteger
import java.security.MessageDigest
import java.security.interfaces.RSAPublicKey
import javax.net.ssl.SSLSocket

/**
 * One-time pairing with the TV on port 6467.
 *
 * Usage: start() makes the TV show a 6-character code, then finish(code) proves
 * the person typed the code the TV is showing.
 */
class PairingSession(
    private val host: String,
    private val clientName: String
) {
    private var socket: SSLSocket? = null
    private var serverKey: RSAPublicKey? = null

    suspend fun start() = withContext(Dispatchers.IO) {
        val s = ClientIdentity.openSocket(host, PAIRING_PORT)
        socket = s
        serverKey = s.session.peerCertificates[0].publicKey as RSAPublicKey
        val out = s.outputStream
        val input = s.inputStream

        // 1. Introduce ourselves.
        send(out) {
            message(10) {
                string(1, "atvremote")
                string(2, clientName)
            }
        }
        expect(input, 11)

        // 2. Offer to type a 4-byte hexadecimal code.
        send(out) {
            message(20) {
                message(1) {
                    varint(1, ENCODING_HEX)
                    varint(2, 4)
                }
                varint(3, ROLE_INPUT)
            }
        }
        expect(input, 20)

        // 3. Confirm the choice. The TV now shows the code.
        send(out) {
            message(30) {
                message(1) {
                    varint(1, ENCODING_HEX)
                    varint(2, 4)
                }
                varint(2, ROLE_INPUT)
            }
        }
        expect(input, 31)
    }

    /** Returns true when the TV accepted the code. */
    suspend fun finish(code: String): Boolean = withContext(Dispatchers.IO) {
        val s = socket ?: throw IOException("Pairing hasn't started")
        val server = serverKey ?: throw IOException("Pairing hasn't started")
        val clean = code.trim().uppercase()
        if (!Regex("[0-9A-F]{6}").matches(clean)) return@withContext false

        val codeBytes = ByteArray(3) { clean.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        val client = ClientIdentity.publicKey()

        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(unsigned(client.modulus))
        digest.update(unsigned(client.publicExponent))
        digest.update(unsigned(server.modulus))
        digest.update(unsigned(server.publicExponent))
        digest.update(codeBytes, 1, codeBytes.size - 1)
        val hash = digest.digest()

        // The first byte of the code is a checksum of the rest, so a mistyped
        // code is caught here without bothering the TV.
        if (hash[0] != codeBytes[0]) return@withContext false

        send(s.outputStream) {
            message(40) { bytes(1, hash) }
        }
        expect(s.inputStream, 41)
        true
    }

    fun close() {
        runCatching { socket?.close() }
        socket = null
    }

    private fun unsigned(n: BigInteger): ByteArray {
        val b = n.toByteArray()
        return if (b.size > 1 && b[0] == 0.toByte()) b.copyOfRange(1, b.size) else b
    }

    private fun send(out: OutputStream, build: PbWriter.() -> Unit) {
        val body = PbWriter()
            .varint(1, 2)      // protocol version
            .varint(2, 200)    // status OK
            .apply(build)
            .toByteArray()
        Framing.write(out, body)
    }

    private fun expect(input: InputStream, field: Int): PbMessage {
        val msg = PbMessage.parse(Framing.read(input))
        val status = msg.varint(2) ?: 200L
        if (status != 200L) throw IOException("The TV refused pairing (status $status)")
        if (!msg.has(field)) throw IOException("Unexpected reply from the TV")
        return msg
    }

    private companion object {
        const val PAIRING_PORT = 6467
        const val ENCODING_HEX = 3L
        const val ROLE_INPUT = 1L
    }
}
