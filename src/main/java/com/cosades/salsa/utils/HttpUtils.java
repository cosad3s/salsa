package com.cosades.salsa.utils;

import com.cosades.salsa.pojo.HttpReponsePojo;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.core5.http.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public abstract class HttpUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpUtils.class);

    public static String urlEncode(String value) {
        if (StringUtils.isNotBlank(value)) {
            return URLEncoder.encode(value, StandardCharsets.UTF_8);
        } else {
            return "";
        }
    }

    public static String urlDecode(String value) {
        if (StringUtils.isNotBlank(value)) {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } else {
            return "";
        }
    }

    /**
     * Find cookie set in HTTP response
     * @param httpResponse: HttpResponse object
     * @param cookieName: name of the cookie
     * @param strict: true for strict search, false for .*cookieName.* search
     * @return
     */
    public static String findCookieFromHttpResponse(final HttpReponsePojo httpResponse, final String cookieName, final boolean strict) {

        if (StringUtils.isBlank(cookieName)) {
            LOGGER.error("[!] No cookie value to search for.");
            return "";
        }

        // Build the regex
        String matchString = cookieName;
        if (!strict) {
            matchString = ".*" + matchString + ".*";
        }
        matchString = matchString + "=.*";

        List<Header> setCookieHeaders = findHeaders(httpResponse, "Set-Cookie");

        String finalMatchString = matchString;
        Optional<Header> cookieOpt = Objects.requireNonNull(setCookieHeaders).stream().filter(c -> c.getValue().matches(finalMatchString)).findFirst();
        if (cookieOpt.isPresent()) {
            String cookie = cookieOpt.get().getValue();
            return cookie.substring(cookie.indexOf("=") + 1, cookie.indexOf(";"));
        } else {
            LOGGER.debug("[x] No cookie value find for cookie name like [{}]", cookieName);
        }

        return "";
    }

    /**
     * Find HTTP headers from HTTP response object
     * @param httpResponse
     * @param headerName
     * @return list of matching HTTP headers
     */

    public static List<Header> findHeaders(final HttpReponsePojo httpResponse, final String headerName) {
        if (StringUtils.isBlank(headerName)) {
            LOGGER.error("[!] No header name value to search for.");
            return new ArrayList<>();
        }

        if (httpResponse != null && httpResponse.getHeaders() != null) {
            List<Header> h = Arrays.stream(httpResponse.getHeaders()).filter(header -> headerName.equalsIgnoreCase(header.getName())).toList();
            if (!h.isEmpty()) {
                return h;
            }
        }

        LOGGER.debug("[x] No HTTP header found for name [{}]", headerName);
        return new ArrayList<>();
    }
}
