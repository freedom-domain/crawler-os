package com.collect.worker.crawler;

import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CrawlerTlsConfigurationTest {

    @Test
    void buildHttpClient_shouldKeepCertificateAndHostnameValidationEnabledByDefault() throws Exception {
        OkHttpClient verifiedClient = CrawlerEngine.buildHttpClient(false, null,
                CrawlerEngine.PoolSettings.defaults());
        SSLSession unverifiedSession = mock(SSLSession.class);
        when(unverifiedSession.getPeerCertificates())
                .thenThrow(new SSLPeerUnverifiedException("Peer certificate is unverified"));

        assertFalse(verifiedClient.hostnameVerifier().verify("example.com", unverifiedSession));
    }

    @Test
    void buildHttpClient_shouldSkipCertificateAndHostnameValidationWhenConfigured() throws Exception {
        OkHttpClient verifiedClient = CrawlerEngine.buildHttpClient(false, null,
                CrawlerEngine.PoolSettings.defaults());
        OkHttpClient unverifiedClient = CrawlerEngine.buildHttpClient(true, null,
                CrawlerEngine.PoolSettings.defaults());
        SSLSession unverifiedSession = mock(SSLSession.class);
        when(unverifiedSession.getPeerCertificates())
                .thenThrow(new SSLPeerUnverifiedException("Peer certificate is unverified"));

        assertTrue(unverifiedClient.hostnameVerifier().verify("example.com", unverifiedSession));
        assertNotSame(verifiedClient.sslSocketFactory(), unverifiedClient.sslSocketFactory());
    }

    @Test
    void buildHttpClient_shouldDefaultToHttp2() {
        OkHttpClient client = CrawlerEngine.buildHttpClient(false, null,
                CrawlerEngine.PoolSettings.defaults());

        assertEquals(List.of(Protocol.HTTP_2, Protocol.HTTP_1_1), client.protocols());
    }

    @Test
    void resolveHttpProtocols_shouldPreferHttp2AndAllowHttp11Fallback() {
        assertEquals(List.of(Protocol.HTTP_2, Protocol.HTTP_1_1),
                CrawlerEngine.resolveHttpProtocols("h2"));

        OkHttpClient client = CrawlerEngine.buildHttpClient(false, null,
                CrawlerEngine.PoolSettings.defaults());
        assertEquals(List.of(Protocol.HTTP_2, Protocol.HTTP_1_1), client.protocols());
        assertTrue(client.networkInterceptors().isEmpty());
    }

    @Test
    void resolveHttpProtocols_shouldRejectUnsupportedProtocol() {
        assertThrows(IllegalArgumentException.class,
                () -> CrawlerEngine.resolveHttpProtocols("unsupported"));
    }
}
