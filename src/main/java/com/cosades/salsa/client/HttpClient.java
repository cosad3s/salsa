package com.cosades.salsa.client;

import com.cosades.salsa.exception.HttpClientBadUrlException;
import com.cosades.salsa.pojo.HttpReponsePojo;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.cookie.BasicClientCookie;
import org.apache.hc.client5.http.impl.io.BasicHttpClientConnectionManager;
import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
import org.apache.hc.client5.http.socket.PlainConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.config.Registry;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.ssl.TrustStrategy;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;

public class HttpClient {
    private static final Logger logger = LoggerFactory.getLogger(HttpClient.class);
    private String userAgent = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118.0.0.0 Safari/537.36";
    private CookieStore cookieStore = new BasicCookieStore();

    @Getter
    private URI baseUrl;
    private final RequestConfig requestConfig;

    public HttpClient(final String baseUrl,
                      final String userAgent,
                      final String proxyHost,
                      final int proxyPort) throws HttpClientBadUrlException {

        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            throw new HttpClientBadUrlException("Target URL is not HTTP/HTTPS scheme");
        }

        this.baseUrl = URI.create(baseUrl);
        // Set proxy
        if (StringUtils.isNotBlank(proxyHost) && proxyPort > 0 && proxyPort < 65535) {
            HttpHost proxy = new HttpHost(proxyHost, proxyPort);
            this.requestConfig = RequestConfig.custom()
                    .setProxy(proxy)
                    .build();
        } else {
            this.requestConfig = RequestConfig.custom()
                    .build();
        }
        // Set User-Agent
        if (StringUtils.isNotBlank(userAgent)) {
            this.userAgent = userAgent;
        }
    }

    /**
     * Recreate a new HTTPClient from another one
     * @param httpClient old HttpClient
     */
    public HttpClient(HttpClient httpClient) {
        this.baseUrl = httpClient.getBaseUrl();
        this.userAgent = httpClient.userAgent;
        this.requestConfig = httpClient.requestConfig;
        this.cookieStore = httpClient.cookieStore;
    }

    private CloseableHttpClient createHttpClient() {
        final TrustStrategy acceptingTrustStrategy = (cert, authType) -> true;
        final SSLContext sslContext;
        try {
            sslContext = SSLContexts.custom()
                    .loadTrustMaterial(null, acceptingTrustStrategy)
                    .build();
        } catch (NoSuchAlgorithmException | KeyManagementException | KeyStoreException e) {
            throw new RuntimeException(e);
        }
        final SSLConnectionSocketFactory sslsf =
                new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);
        final Registry<ConnectionSocketFactory> socketFactoryRegistry =
                RegistryBuilder.<ConnectionSocketFactory> create()
                        .register("https", sslsf)
                        .register("http", new PlainConnectionSocketFactory())
                        .build();

        final BasicHttpClientConnectionManager connectionManager =
                new BasicHttpClientConnectionManager(socketFactoryRegistry);
        connectionManager.setConnectionConfig(ConnectionConfig.custom()
                .setSocketTimeout(Timeout.ofSeconds(120))
                .setConnectTimeout(Timeout.ofSeconds(120))
                .build());

        return  HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultCookieStore(cookieStore)
                .build();
    }

    /**
     * Create new HttpClient based on another one, when the main host can be different
     * @param httpClient
     * @param redirectUrl
     */
    public HttpClient(HttpClient httpClient, String redirectUrl) {
        this(httpClient);
        this.baseUrl = URI.create(redirectUrl);
    }

    public HttpReponsePojo post(final String uri) {
        return this.post(uri, "", ContentType.APPLICATION_FORM_URLENCODED);
    }

    public HttpReponsePojo post(final String uri, final String requestJsonBody, final ContentType contentType, final Header ... headers) {

        URI finalUrl = URI.create(this.baseUrl.toString() + uri);

        HttpPost request = new HttpPost(finalUrl);
        final StringEntity requestEntity = new StringEntity(requestJsonBody);
        request.setEntity(requestEntity);
        request.setHeader("Accept", "*/*");
        request.setHeader("Accept-Language", "en");
        request.setHeader("Content-type", contentType.getMimeType());
        request.setHeader("User-Agent", this.userAgent);
        for (Header h : headers) {
            request.setHeader(h);
        }

        if (requestConfig != null) {
            request.setConfig(requestConfig);
        }

        HttpClientResponseHandler<HttpReponsePojo> responseHandler = response -> {
            HttpEntity entity = response.getEntity();

            return new HttpReponsePojo(EntityUtils.toString(entity), response.getCode(), response.getHeaders());
        };

        try (CloseableHttpClient httpClient = this.createHttpClient()){
            return httpClient.execute(request, responseHandler);
        } catch (IOException e) {
            logger.error("[!] Error on POST request to {}", uri, e);
            return new HttpReponsePojo();
        }
    }

    public HttpReponsePojo get(final String uri, final Header ... headers) {
        URI finalUrl = URI.create(this.baseUrl.toString() + uri);

        HttpGet request = new HttpGet(finalUrl);
        request.setHeader("Accept", "*/*");
        request.setHeader("Accept-Language", "en");
        request.setHeader("User-Agent", this.userAgent);
        for (Header h : headers) {
            request.setHeader(h);
        }

        if (requestConfig != null) {
            request.setConfig(requestConfig);
        }

        HttpClientResponseHandler<HttpReponsePojo> responseHandler = response -> {
            HttpEntity entity = response.getEntity();
            return new HttpReponsePojo(EntityUtils.toString(entity), response.getCode(), response.getHeaders());
        };

        try (CloseableHttpClient httpClient = this.createHttpClient()){
            return httpClient.execute(request, responseHandler);
        } catch (IOException e) {
            logger.error("[!] Error on GET request to {}", uri, e);
            return new HttpReponsePojo();
        }
    }

    public void updateCookie(final String name, final String value) {
        BasicClientCookie cookie = new BasicClientCookie(name, value);
        cookie.setDomain(this.baseUrl.getHost());
        cookie.setPath(this.baseUrl.getPath());
        this.cookieStore.getCookies().removeIf(c -> name.equals(c.getName()));
        this.cookieStore.addCookie(cookie);
    }
}
