package com.aadil.tclremote.net

import android.annotation.SuppressLint
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.math.BigInteger
import java.net.InetSocketAddress
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.util.Date
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import javax.security.auth.x500.X500Principal

/**
 * The phone's identity towards the TV: an RSA key + self-signed certificate that
 * lives in the Android Keystore. The TV remembers this certificate after pairing.
 */
object ClientIdentity {
    private const val ALIAS = "tcl_remote_client_v2"
    private const val DAY_MS = 24L * 60 * 60 * 1000

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun ensureKey() {
        if (keyStore().containsAlias(ALIAS)) return
        val now = System.currentTimeMillis()
        val spec = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY or
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setKeySize(2048)
            .setDigests(
                KeyProperties.DIGEST_NONE,
                KeyProperties.DIGEST_SHA1,
                KeyProperties.DIGEST_SHA256,
                KeyProperties.DIGEST_SHA384,
                KeyProperties.DIGEST_SHA512
            )
            .setSignaturePaddings(
                KeyProperties.SIGNATURE_PADDING_RSA_PKCS1,
                KeyProperties.SIGNATURE_PADDING_RSA_PSS
            )
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
            .setCertificateSubject(X500Principal("CN=tcl-remote"))
            .setCertificateSerialNumber(BigInteger.valueOf(now))
            .setCertificateNotBefore(Date(now - DAY_MS))
            .setCertificateNotAfter(Date(now + 20L * 365 * DAY_MS))
            .build()
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore")
            .apply { initialize(spec) }
            .generateKeyPair()
    }

    fun publicKey(): RSAPublicKey {
        ensureKey()
        val cert = keyStore().getCertificate(ALIAS) as X509Certificate
        return cert.publicKey as RSAPublicKey
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
        ensureKey()
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(keyStore(), null)
        val trustAll = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(kmf.keyManagers, arrayOf<TrustManager>(trustAll), null)
        val socket = ctx.socketFactory.createSocket() as SSLSocket
        // The TV's remote service speaks TLS 1.2, which signs with plain PKCS#1.
        socket.enabledProtocols = arrayOf("TLSv1.2")
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
