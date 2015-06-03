package utils

import javax.net.ssl.X509TrustManager
import java.security.cert.CertificateException
import java.security.cert.X509Certificate

class ClientSideTrustManager implements X509TrustManager {
    @Override
    void checkClientTrusted(X509Certificate[] x509Certificates, String authType) throws CertificateException {
        //trust all for now
        println "Implied trust of authType=${authType} client certs ${x509Certificates}"
        //TODO be stricter
    }

    @Override
    void checkServerTrusted(X509Certificate[] x509Certificates, String authType) throws CertificateException {
        //TODO not used yet
    }

    @Override
    X509Certificate[] getAcceptedIssuers() {
        //TODO update this when stricter checking is in place
        return new X509Certificate[0]
    }
}
