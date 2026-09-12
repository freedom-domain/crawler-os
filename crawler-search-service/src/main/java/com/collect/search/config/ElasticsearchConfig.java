package com.collect.search.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientHttpClient;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class ElasticsearchConfig {

    @Value("${spring.data.elasticsearch.uris}")
    private String uris;

    @Value("${spring.data.elasticsearch.username:}")
    private String username;

    @Value("${spring.data.elasticsearch.password:}")
    private String password;

    @Bean
    public RestClient restClient() {
        HttpHost[] hosts = new HttpHost[uris.split(",").length];
        String[] uriList = uris.split(",");
        for (int i = 0; i < uriList.length; i++) {
            String uri = uriList[i].trim();
            boolean https = uri.startsWith("https");
            String hostPort = uri.replace("https://", "").replace("http://", "");
            String[] parts = hostPort.split(":");
            hosts[i] = new HttpHost(parts[0], Integer.parseInt(parts[1]), https ? "https" : "http");
        }

        RestClientBuilder builder = RestClient.builder(hosts);
        builder.setRequestConfigCallback(config -> config
                .setConnectTimeout((int) Duration.ofSeconds(10).toMillis())
                .setSocketTimeout((int) Duration.ofSeconds(30).toMillis()));

        if (username != null && !username.isBlank()) {
            BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
            credsProvider.setCredentials(
                    new AuthScope(hosts[0].getHostName(), hosts[0].getPort()),
                    new UsernamePasswordCredentials(username, password));
            builder.setHttpClientConfigCallback(httpClientBuilder ->
                    httpClientBuilder.setDefaultCredentialsProvider(credsProvider));
        }

        return builder.build();
    }

    @Bean
    public ElasticsearchClient elasticsearchClient(RestClient restClient) {
        ElasticsearchTransport transport = new RestClientHttpClient(restClient, new JacksonJsonpMapper());
        return new ElasticsearchClient(transport);
    }
}
