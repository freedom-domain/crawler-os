package com.collect.worker.crawler;

import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.Interceptor;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import java.net.ProtocolException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
    void resolveHttpProtocols_shouldPreferHttp2AndRequireHttp2AtRuntime() throws Exception {
        assertEquals(List.of(Protocol.HTTP_2, Protocol.HTTP_1_1),
                CrawlerEngine.resolveHttpProtocols("h2"));

        OkHttpClient client = CrawlerEngine.buildHttpClient(false, null,
                CrawlerEngine.PoolSettings.defaults());
        Interceptor.Chain chain = mock(Interceptor.Chain.class);
        Request request = new Request.Builder().url("https://example.com").build();
        Response response = new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(ResponseBody.create(new byte[0], null))
                .build();
        when(chain.request()).thenReturn(request);
        when(chain.proceed(request)).thenReturn(response);

        assertThrows(ProtocolException.class,
                () -> client.networkInterceptors().get(0).intercept(chain));
        verify(chain).proceed(request);
    }

    @Test
    void resolveHttpProtocols_shouldRejectUnsupportedProtocol() {
        assertThrows(IllegalArgumentException.class,
                () -> CrawlerEngine.resolveHttpProtocols("unsupported"));
    }
}
