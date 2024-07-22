package com.cosades.salsa.client;

import com.cosades.salsa.configuration.AuraConfiguration;
import com.cosades.salsa.exception.*;
import com.cosades.salsa.pojo.*;
import com.cosades.salsa.utils.AuraHttpUtils;
import com.cosades.salsa.utils.HttpUtils;
import com.cosades.salsa.utils.RESTHTTPUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.message.BasicHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class BaseClient {
    private static final Logger logger = LoggerFactory.getLogger(BaseClient.class);
    protected HttpClient httpClient;
    final int MAX_RETRIES = 10;
    protected SalesforceAuraCredentialsPojo credentials = new SalesforceAuraCredentialsPojo();
    protected String auraAppName = "";
    private String auraFwUid = "random";
    private final String auraContextMode = "PROD";
    protected String auraPath = "";
    protected LinkedList<String> auraAppNames = AuraConfiguration.getAuraApps();
    private final String DEFAULT_SOAP_PATH = "/services/Soap/c/60.0/";
    private final String DEFAULT_SOBJECTS_API_PATH = "/services/data/v60.0/sobjects";
    private final String DEFAULT_QUERY_API_PATH = "/services/data/v60.0/query/";

    public void login(final SalesforceAuraCredentialsPojo credentials) throws SalesforceAuraAuthenticationException {
        SalesforceAuraMessagePojo[] messagesToComplete = AuraConfiguration.getActionMessageFor("login");

        // For the moment: only one process for login
        SalesforceAuraMessagePojo messageToComplete = messagesToComplete[0];
        messageToComplete.setParamsValue("username",credentials.getUsername());
        messageToComplete.setParamsValue("password",credentials.getPassword());

        SalesforceAuraHttpRequestBodyPojo requestBodyPojo =
                new SalesforceAuraHttpRequestBodyPojo(
                        messageToComplete.getDescriptor(),
                        messageToComplete.getParams(),
                        credentials);

        SalesforceAuraHttpResponseBodyPojo salesforceAuraHttpResponseBody;
        try {
            salesforceAuraHttpResponseBody = this.sendAura(requestBodyPojo);
        } catch (SalesforceAuraClientBadRequestException | SalesforceAuraUnauthenticatedException e) {
            logger.error("[!] Unable to authenticate: invalid request or invalid credentials.");
            throw new SalesforceAuraAuthenticationException();
        } catch (SalesforceAuraInvalidParameters e) {
            logger.error("[!] Invalid request parameters.", e);
            throw new SalesforceAuraAuthenticationException();
        }

        // Next step: find SID cookie
        if (salesforceAuraHttpResponseBody.getEvents() == null || salesforceAuraHttpResponseBody.getEvents().length == 0) {
            logger.error("[!] Unable to authenticate: no event on authentication attempt.");
            throw new SalesforceAuraAuthenticationException();
        }

        if (salesforceAuraHttpResponseBody.getEvents()[0].getAttributes() == null) {
            logger.error("[!] Unable to authenticate: no event attributes on authentication attempt.");
            throw new SalesforceAuraAuthenticationException();
        }

        String redirectUrl = salesforceAuraHttpResponseBody.getEvents()[0].getAttributes().getValues().get("url");
        if (StringUtils.isBlank(redirectUrl)) {
            logger.error("[!] Unable to authenticate: invalid redirect URL [{}]", redirectUrl);
            throw new SalesforceAuraAuthenticationException();
        }

        HttpReponsePojo redirectHttpResponse;
        URI redirectURI = URI.create(redirectUrl);
        if (!redirectURI.getHost().equals(this.httpClient.getBaseUrl().getHost()) ||
                redirectURI.getPort() != this.httpClient.getBaseUrl().getPort()) {
            HttpClient temporaryRedirectClient = new HttpClient(this.httpClient, redirectUrl);
            redirectHttpResponse = temporaryRedirectClient.get(redirectURI.getRawPath()+"?"+redirectURI.getRawQuery());
        } else {
            redirectHttpResponse = this.httpClient.get(redirectURI.getRawPath()+"?"+redirectURI.getRawQuery());
        }

        String sid = HttpUtils.findCookieFromHttpResponse(redirectHttpResponse, "sid", true);

        if (StringUtils.isBlank(sid)) {
            logger.warn("[!] Authentication error: no SID cookie received. Will try without it.");
        } else {
            this.credentials.setSid(sid);
        }

        // Next step: find Salesforce Aura token
        HttpReponsePojo responseAuraToken = httpClient.get("/s/");
        String token = HttpUtils.findCookieFromHttpResponse(responseAuraToken, "Host-ERIC", false);
        if (StringUtils.isBlank(token)) {
            logger.error("[!] Unable to authenticate: empty Aura token.");
            throw new SalesforceAuraAuthenticationException();
        }

        // Aura token will be added to the cookie jar (same host)
        this.credentials.setToken(token);
        this.credentials.setUsername(credentials.getUsername());
        this.credentials.setPassword(credentials.getPassword());

        // Find app name (maybe not useful)
        List<Header> appNameHeader = HttpUtils.findHeaders(responseAuraToken, "link");
        if (!appNameHeader.isEmpty()) {
            String regex = ".*%40markup%3A%2F%2F([a-zA-Z0-9:%]*)%22%3A%22.*";
            Pattern p = Pattern.compile(regex);
            Matcher m = p.matcher(appNameHeader.get(0).getValue());
            boolean find = m.find();
            if (find) {
                this.auraAppName = HttpUtils.urlDecode(m.group(1));
                logger.info("[*] Found the app name: {}", this.auraAppName);
            } else {
                logger.warn("[!] Cannot find the Salesforce Aura app name. Will continue with a default one [{}]", this.auraAppName);
            }
        }

        logger.info("[*] Login success with credentials [{}]!", this.credentials);
    }

    public void updateCredentials(final SalesforceAuraCredentialsPojo credentials) {
        this.credentials.setToken(credentials.getToken());
        this.credentials.setUsername(credentials.getUsername());
        this.credentials.setPassword(credentials.getPassword());
        this.credentials.setSid(credentials.getSid());
    }

    protected SalesforceAuraHttpResponseBodyPojo sendAura(final SalesforceAuraHttpRequestBodyPojo requestBodyPojo) throws SalesforceAuraClientBadRequestException, SalesforceAuraUnauthenticatedException, SalesforceAuraInvalidParameters {
        requestBodyPojo.setFwuid(this.auraFwUid);
        requestBodyPojo.setAppName(this.auraAppName);
        requestBodyPojo.setMode(this.auraContextMode);
        String requestBody = requestBodyPojo.toString();


        int retries = 0;

        SalesforceAuraHttpResponseBodyPojo salesforceAuraHttpResponseBody;

        while(true) {

            if (retries == MAX_RETRIES) {
                logger.error("[x] Error on requesting Salesforce Aura target (connection lost?).");
                throw new SalesforceAuraClientBadRequestException();
            }
            if (retries > 0) {
                logger.error("[!] Retry ({}/{}) ...", retries, MAX_RETRIES);
            }
            retries++;

            HttpReponsePojo response = this.httpClient.post(this.auraPath, requestBody, ContentType.APPLICATION_FORM_URLENCODED);
            int code = response.getCode();
            if (code != 200) {
                logger.error("[!] Got an HTTP response code {}.", response.getCode());
                logger.debug("[x] Response body: {}", response.getBody());
                logger.debug("[x] Request body: {}", requestBody);

                // Special error code 0 is received following an internal problem in Apache HttpClient
                if (code == 0) {
                    this.httpClient = new HttpClient(this.httpClient);
                    continue;
                }

                if (code == 401) {
                    throw new SalesforceAuraUnauthenticatedException();
                }

                // HTTP 404 can occur in specific cases where the sent parameters are incorrect
                if (code == 404) {
                    throw new SalesforceAuraInvalidParameters();
                }
            }

            // Process the HTTP response
            try {
                salesforceAuraHttpResponseBody = AuraHttpUtils.parseHttpResponseBody(response.getBody());
                if (salesforceAuraHttpResponseBody != null) {
                    salesforceAuraHttpResponseBody.setRawBody(response.getBody());
                    return salesforceAuraHttpResponseBody;
                } else {
                    logger.error("[x] Unable to get a parseable HTTP response.");
                }
            } catch (SalesforceAuraClientNotSyncException e) {
                this.auraFwUid = e.getFwuid();
                logger.warn("[!] Client is out-of-sync. Will retry with new FWUID: {}", e.getFwuid());
                return this.sendAura(requestBodyPojo);
            } catch (SalesforceAuraClientCSRFException e2) {
                if (!this.auraAppNames.isEmpty()) {
                    this.auraAppName = this.auraAppNames.pop();
                    logger.warn("[!] Site app name is incorrect. Will retry with new app name: {}", this.auraAppName);
                    return this.sendAura(requestBodyPojo);
                } else {
                    logger.error("[x] Unable to call the target.");
                }
            }
        }
    }

    public HttpReponsePojo sendSOAP(final String requestBody) {
        Header soapHeader = new BasicHeader("SOAPAction","blank");
        return this.httpClient.post(DEFAULT_SOAP_PATH, requestBody, ContentType.TEXT_XML, soapHeader);
    }

    public SalesforceRESTSObjectsListHttpResponseBodyPojo sendRESTGetSObjectsList() {
        HttpReponsePojo response;
        if (StringUtils.isNotBlank(this.credentials.getSid())) {
            Header authHeader = new BasicHeader("Authorization","OAuth " + this.credentials.getSid());
            response = this.httpClient.get(DEFAULT_SOBJECTS_API_PATH, authHeader);
        } else {
            response = this.httpClient.get(DEFAULT_SOBJECTS_API_PATH);
        }

        return RESTHTTPUtils.parseHttpResponseSObjectsListBody(response.getBody());
    }

    public SalesforceSObjectPojo sendRESTGetSObject(final String type, final String id) {

        final String path = DEFAULT_SOBJECTS_API_PATH + "/" + type + "/" + id;

        HttpReponsePojo response;
        if (StringUtils.isNotBlank(this.credentials.getSid())) {
            Header authHeader = new BasicHeader("Authorization","OAuth " + this.credentials.getSid());
            response = this.httpClient.get(path, authHeader);
        } else {
            response = this.httpClient.get(path);
        }

        return RESTHTTPUtils.parseHttpResponseSObjectBody(response.getBody(), type);
    }

    public SalesforceRESTSObjectsRecentHttpResponseBodyPojo sendRESTGetSObjectsRecent(final String type) {

        final String path = DEFAULT_SOBJECTS_API_PATH + "/" + type + "/";

        HttpReponsePojo response;
        if (StringUtils.isNotBlank(this.credentials.getSid())) {
            Header authHeader = new BasicHeader("Authorization","OAuth " + this.credentials.getSid());
            response = this.httpClient.get(path, authHeader);
        } else {
            response = this.httpClient.get(path);
        }

        return RESTHTTPUtils.parseHttpResponseSObjectsRecentBody(response.getBody());
    }

    protected Set<String> sendRESTGetSObjectDescribeFields(final String type) {
        final String path = DEFAULT_SOBJECTS_API_PATH + "/" + type + "/describe";

        HttpReponsePojo response;
        if (StringUtils.isNotBlank(this.credentials.getSid())) {
            Header authHeader = new BasicHeader("Authorization","OAuth " + this.credentials.getSid());
            response = this.httpClient.get(path, authHeader);
        } else {
            response = this.httpClient.get(path);
        }

        return RESTHTTPUtils.parseHttpResponseSObjectDescribeBody(type, response.getBody());
    }

    protected Set<SalesforceSObjectPojo> sendRESTGetSObjectsFromQuery(final String type) {
        final String path = DEFAULT_QUERY_API_PATH + "?q=SELECT+FIELDS(ALL)+FROM+"+ type +"+LIMIT+10";

        HttpReponsePojo response;
        if (StringUtils.isNotBlank(this.credentials.getSid())) {
            Header authHeader = new BasicHeader("Authorization","OAuth " + this.credentials.getSid());
            response = this.httpClient.get(path, authHeader);
        } else {
            response = this.httpClient.get(path);
        }

        return RESTHTTPUtils.parseHttpResponseQueryBody(response.getBody(), type);
    }
}
