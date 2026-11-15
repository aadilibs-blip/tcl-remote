package com.aadil.tclremote.net

import android.annotation.SuppressLint
import android.content.Context
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.ByteArrayInputStream
import java.io.File
import java.math.BigInteger
import java.net.InetSocketAddress
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * The phone's identity towards the TV: an RSA key + self-signed certificate kept in
 * the app's private storage. The TV remembers this certificate after pairing.
 *
 * The key lives in app storage rather than the Android Keystore because some phones
 * can't use Keystore keys for TLS client signatures.
 */
object ClientIdentity {
    private const val DAY_MS = 24L * 60 * 60 * 1000
    private val PASSWORD = "tcl-remote".toCharArray()

    private lateinit var keyFile: File
    private lateinit var certFile: File
    private var privateKey: PrivateKey? = null
    private var certificate: X509Certificate? = null

    fun init(context: Context) {
        keyFile = File(context.filesDir, "client_key.p8")
        certFile = File(context.filesDir, "client_cert.der")
    }

    @Synchronized
    private fun load() {
        if (privateKey != null && certificate != null) return

        if (keyFile.exists() && certFile.exists()) {
            try {
                val key = KeyFactory.getInstance("RSA")
                    .generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes()))
                val cert = CertificateFactory.getInstance("X.509")
                    .generateCertificate(ByteArrayInputStream(certFile.readBytes())) as X509Certificate
                privateKey = key
                certificate = cert
                return
            } catch (e: Exception) {
                keyFile.delete()
                certFile.delete()
            }
        }

        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val now = System.currentTimeMillis()
        val name = X500Name("CN=tcl-remote")
        val builder = JcaX509v3CertificateBuilder(
            name,
            BigInteger.valueOf(now),
            Date(now - DAY_MS),
            Date(now + 20L * 365 * DAY_MS),
            name,
            pair.public
        )
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(pair.private)
        val cert = JcaX509CertificateConverter().getCertificate(builder.build(signer))

        keyFile.writeBytes(pair.private.encoded)
        certFile.writeBytes(cert.encoded)
        privateKey = pair.private
        certificate = cert
    }

    fun publicKey(): RSAPublicKey {
        load()
        return certificate!!.publicKey as RSAPublicKey
    }

    /**
     * Opens a TLS connection to the TV presenting our certificate.
     *
     * The TV uses a self-signed certificate, so normal chain validation can't work.
     * Pairing is what establishes trust: the pairing code is a hash over both
     * public keys, so a TV that isn't the one showing the code fails the check.
     */
    @SuppressLint("CustomX509TrustManager", "TrustAllX509TrustManager")
    fun openSocket(host: String, port: Int): SSLSocket {
        load()
        val store = KeyStore.getInstance(KeyStore.getDefaultType())
        store.load(null, null)
        store.setKeyEntry("client", privateKey, PASSWORD, arrayOf(certificate))

        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(store, PASSWORD)

        val trustAll = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(kmf.keyManagers, arrayOf<TrustManager>(trustAll), null)
        val socket = ctx.socketFactory.createSocket() as SSLSocket
        try {
            socket.connect(InetSocketAddress(host, port), 8_000)
            socket.soTimeout = 0
            socket.startHandshake()
        } catch (e: Exception) {
            runCatching { socket.close() }
            throw e
        }
        return socket
    }
}
