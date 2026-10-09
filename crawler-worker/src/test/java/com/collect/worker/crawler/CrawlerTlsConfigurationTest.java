package com.collect.worker.crawler;

import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
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
}
