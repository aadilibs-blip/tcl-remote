package com.aadil.tclremote.net

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

/**
 * Minimal protobuf encoder/decoder. The Android TV remote protocol only needs
 * varints and length-delimited fields, so this avoids a protobuf dependency.
 */
class PbWriter {
    private val out = ByteArrayOutputStream()

    fun varint(field: Int, value: Long): PbWriter {
        writeVarint(out, (field shl 3).toLong())
        writeVarint(out, value)
        return this
    }

    fun bytes(field: Int, value: ByteArray): PbWriter {
        writeVarint(out, ((field shl 3) or 2).toLong())
        writeVarint(out, value.size.toLong())
        out.write(value)
        return this
    }

    fun string(field: Int, value: String): PbWriter = bytes(field, value.toByteArray(Charsets.UTF_8))

    fun message(field: Int, build: PbWriter.() -> Unit): PbWriter =
        bytes(field, PbWriter().apply(build).toByteArray())

    fun toByteArray(): ByteArray = out.toByteArray()
}

internal fun writeVarint(out: OutputStream, value: Long) {
    var v = value
    while ((v and 0x7FL.inv()) != 0L) {
        out.write(((v and 0x7F) or 0x80).toInt())
        v = v ushr 7
    }
    out.write(v.toInt())
}

class PbMessage private constructor(private val fields: Map<Int, List<Any>>) {
    fun has(field: Int): Boolean = fields.containsKey(field)
    fun varint(field: Int): Long? = fields[field]?.firstOrNull() as? Long
    fun bytes(field: Int): ByteArray? = fields[field]?.firstOrNull() as? ByteArray
    fun message(field: Int): PbMessage? = bytes(field)?.let { parse(it) }

    companion object {
        fun parse(data: ByteArray): PbMessage {
            val fields = HashMap<Int, MutableList<Any>>()
            var pos = 0

            fun readVarint(): Long {
                var result = 0L
                var shift = 0
                while (true) {
                    require(pos < data.size) { "Truncated varint" }
                    val b = data[pos++].toInt() and 0xFF
                    result = result or ((b and 0x7F).toLong() shl shift)
                    if ((b and 0x80) == 0) return result
                    shift += 7
                }
            }

            while (pos < data.size) {
                val tag = readVarint()
                val field = (tag ushr 3).toInt()
                when ((tag and 7L).toInt()) {
                    0 -> fields.getOrPut(field) { mutableListOf() }.add(readVarint())
                    1 -> pos += 8
                    2 -> {
                        val len = readVarint().toInt()
                        require(len >= 0 && pos + len <= data.size) { "Truncated field" }
                        fields.getOrPut(field) { mutableListOf() }.add(data.copyOfRange(pos, pos + len))
                        pos += len
                    }
                    5 -> pos += 4
                    else -> throw IllegalArgumentException("Unsupported wire type")
                }
            }
            return PbMessage(fields)
        }
    }
}

/** Every message on both TV ports is prefixed with its length as a varint. */
object Framing {
    fun write(out: OutputStream, body: ByteArray) {
        val buf = ByteArrayOutputStream()
        writeVarint(buf, body.size.toLong())
        buf.write(body)
        out.write(buf.toByteArray())
        out.flush()
    }

    fun read(input: InputStream): ByteArray {
        var length = 0L
        var shift = 0
        while (true) {
            val b = input.read()
            if (b < 0) throw EOFException("The TV closed the connection")
            length = length or ((b and 0x7F).toLong() shl shift)
            if ((b and 0x80) == 0) break
            shift += 7
        }
        require(length in 0..1_000_000) { "Bad message length" }
        val buf = ByteArray(length.toInt())
        var off = 0
        while (off < buf.size) {
            val n = input.read(buf, off, buf.size - off)
            if (n < 0) throw EOFException("The TV closed the connection")
            off += n
        }
        return buf
    }
}
