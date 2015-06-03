package utils

import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.TrustManager
import java.security.KeyFactory
import java.security.KeyStore
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec

class SSLContextShim {
    public static final String SSL_CONTEXT_PROTO = 'TLSv1'
    public static final String PRIVATE_KEY_ALGO = 'RSA'
    public static final String CERT_FACTORY_TYPE = 'X.509'
    private SSLContext jdkSSLContext
    private final boolean serverMode

    private SSLContextShim(SSLContext jdkSSLContext, boolean serverMode) {
        this.serverMode = serverMode
        this.jdkSSLContext = jdkSSLContext
    }

    public static SSLContextShim createServerContext(File cert, File key) {
        KeyFactory keyFactory = KeyFactory.getInstance(PRIVATE_KEY_ALGO);
        KeyManagerFactory keyMgrFactory = KeyManagerFactory.getInstance(KeyManagerFactory.defaultAlgorithm)
        KeyStore ks = KeyStore.getInstance("JKS");
        CertificateFactory cf = CertificateFactory.getInstance(CERT_FACTORY_TYPE);

        def keySpec = new PKCS8EncodedKeySpec(key.bytes)
        def privateKey = keyFactory.generatePrivate(keySpec)

        // TODO: load keystore from file when available
        ks.load(null, null)
        // TODO: ... and this all becomes conditionally not needed then
        Certificate publicCert = cert.withInputStream {
                cf.generateCertificate(it)
            }
        ks.setKeyEntry('ours', privateKey, [] as char[], [publicCert] as Certificate[])
        keyMgrFactory.init(ks)

        SSLContext jdkSSLContext = SSLContext.getInstance(SSL_CONTEXT_PROTO)
        jdkSSLContext.init(keyMgrFactory.keyManagers, null, null)

        return new SSLContextShim(jdkSSLContext, true)
    }

    static SSLContextShim createClientContext() {
        SSLContext jdkSSLContext = SSLContext.getInstance(SSL_CONTEXT_PROTO)
        jdkSSLContext.init(null, [new ClientSideTrustManager()] as TrustManager[], null)
        return new SSLContextShim(jdkSSLContext, false)
    }

    SSLEngine createEngine() {
        def engine = jdkSSLContext.createSSLEngine()
        engine.setUseClientMode(!serverMode)
        return engine
    }
}
