package com.cosades.salsa.utils;

import com.cosades.salsa.configuration.AuraConfiguration;
import com.cosades.salsa.enumeration.SalesforceAuraHttpResponseBodyActionsStateEnum;
import com.cosades.salsa.exception.SalesforceAuraClientCSRFException;
import com.cosades.salsa.exception.SalesforceAuraClientNoAccessException;
import com.cosades.salsa.exception.SalesforceAuraClientNotSyncException;
import com.cosades.salsa.pojo.SalesforceAuraHttpResponseBodyPojo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public abstract class AuraHttpUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuraHttpUtils.class);

    public static SalesforceAuraHttpResponseBodyPojo parseHttpResponseBody(final String responseBody) throws SalesforceAuraClientNotSyncException, SalesforceAuraClientCSRFException {
        try {
            ObjectMapper om = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            SalesforceAuraHttpResponseBodyPojo response = om.readValue(responseBody, SalesforceAuraHttpResponseBodyPojo.class);
            // New case for out-of-sync ("warning coos")
            checkOutOfSyncClient(response, responseBody);
            return response;
        } catch (JsonProcessingException e) {
            LOGGER.debug("[x] Error on parsing HTTP body {}", responseBody);
            // Legacy case for out-of-sync
            checkOutOfSyncClientLegacy(responseBody);
            checkCSRFClient(responseBody);
            checkAuraNoAccess(responseBody);
            return null;
        }
    }

    /**
     * Check if the returned body matches a Salesforce Aura environment
     * @param body: Salesforce HTTP response body
     * @return
     */
    public static boolean isSalesforceAura(String body) {
        if (StringUtils.isNotBlank(body)) {
            return AuraConfiguration.getAuraRegex().matcher(body).find();
        } else {
            return false;
        }
    }

    /**
     * Check if the HTTP body warns about unknown (or disabled) column on entity
     * @param body: Salesforce HTTP response body
     * @return the unknown column
     */
    public static String checkUnknownColumnFromResults(final String body) {
        final String regex =
                ".*No such column '(.+)' on .*|" +
                        ".*Didn't understand relationship '(.+)' in field.*|" +
                        ".*No such relation '(.+)' on .*";
        final Pattern regexPattern = Pattern.compile(regex);
        Matcher matcher = regexPattern.matcher(body);
        if (matcher.find()) {
            String m1 = matcher.group(1);
            String m2 = matcher.group(2);
            String m3 = matcher.group(3);

            if (StringUtils.isNotBlank(m1)) {
                LOGGER.trace("[xx] Identified unknown field {} (No such column) ", m1);
                return m1;
            }
            if (StringUtils.isNotBlank(m2)) {
                LOGGER.trace("[xx] Identified unknown field {} (Didn't understand relationship)", m3);
                return m2;
            }
            if (StringUtils.isNotBlank(m3)) {
                LOGGER.trace("[xx] Identified unknown field {} (No such relation)", m3);
                return m3;
            }
        }
        return null;
    }

    public static String checkUnsupportedRecordResults(final String body) {
        final String regex =
                ".*Object (.*) is not supported.*|" +
                ".*sObject type '(.*)' is not supported.*|" +
                ".*query string parameter contained object api names that do not correspond to the api names of any of the requested record ids. The requested object api names were: \\[(.*)\\], while the.*";
        final Pattern regexPattern = Pattern.compile(regex);
        Matcher matcher = regexPattern.matcher(body);
        if (matcher.find()) {

            String m1 = matcher.group(1);
            String m2 = matcher.group(2);
            String m3 = matcher.group(3);

            if (StringUtils.isNotBlank(m1)) {
                LOGGER.trace("[xx] Identified unsupported record {} from currently tested descriptor (Object not supported).", m1);
                return m1;
            }
            if (StringUtils.isNotBlank(m2)) {
                LOGGER.trace("[xx] Identified unsupported record {} from currently tested descriptor (sObject type not supported).", m2);
                return m2;
            }
            if (StringUtils.isNotBlank(m3)) {
                LOGGER.trace("[xx] Identified unsupported record {} from currently tested descriptor (do not correspond to the api names).", m3);
                return m3;
            }
        }
        return null;
    }

    public static boolean checkInvalidValueForField(final String body) {
        final String regex =
                ".*INVALID_OR_NULL_FOR_RESTRICTED_PICKLIST.*|" +
                ".*FIELD_INTEGRITY_EXCEPTION.*|" +
                ".*INVALID_TYPE_ON_FIELD_IN_RECORD.*|" +
                ".*Value for field '(.*)'  is not .*|" +
                ".*STRING_TOO_LONG.*";
        final Pattern regexPattern = Pattern.compile(regex);
        Matcher matcher = regexPattern.matcher(body);
        if (matcher.find()) {
            LOGGER.trace("[xx] Invalid value for record field.");
            return true;
        }
        return false;
    }

    public static boolean checkUnsupportedUpdate(final String body) {
        final String regex =
                ".*CANNOT_INSERT_UPDATE_ACTIVATE_ENTITY.*|" +
                ".*INSUFFICIENT_ACCESS_OR_READONLY.*";
        final Pattern regexPattern = Pattern.compile(regex);
        Matcher matcher = regexPattern.matcher(body);
        if (matcher.find()) {
            LOGGER.trace("[xx] The target object cannot be updated.");
            return true;
        }
        return false;
    }

    public static boolean checkSecuredField(final String body) {
        final String regex = ".*Unable to create/update fields.*";
        final Pattern regexPattern = Pattern.compile(regex);
        Matcher matcher = regexPattern.matcher(body);
        if (matcher.find()) {
            LOGGER.trace("[xx] Identified secured field.");
            return true;
        }
        return false;
    }


    /**
     * Detect if the server is out of sync with client (legacy). The new FWUUID is returned if it is true.
     * @param body: Salesforce HTTP response body
     */
    private static void checkOutOfSyncClientLegacy(final String body) throws SalesforceAuraClientNotSyncException {
        final String regex = ".*Framework has been updated. Expected: (.+) Actual.*";
        final Pattern regexPattern = Pattern.compile(regex);
        final Matcher matcher = regexPattern.matcher(body);
        if (matcher.find()) {
            throw new SalesforceAuraClientNotSyncException(matcher.group(1));
        }
    }

    /**
     * Detect if the server is out of sync with client. The new FWUUID is returned from context if it is true.
     * @param body: Salesforce HTTP response body
     */
    private static void checkOutOfSyncClient(final SalesforceAuraHttpResponseBodyPojo response, final String body) throws SalesforceAuraClientNotSyncException {
        if (!Arrays.stream(response.getActions()).filter(a -> SalesforceAuraHttpResponseBodyActionsStateEnum.warning.equals(a.getState())).collect(Collectors.toSet()).isEmpty()) {
            final String regex = ".*This page has changes since the last refresh. To get the latest updates, save your work and finish your conversations before refreshing the page..*";
            final Pattern regexPattern = Pattern.compile(regex);
            final Matcher matcher = regexPattern.matcher(body);
            if (matcher.find()) {
                throw new SalesforceAuraClientNotSyncException(response.getContext().getFwuid());
            }
        }
    }

    /**
     * Not a real CSRF problem: maybe due to site app name
     * @param body
     * @throws SalesforceAuraClientCSRFException
     */
    private static void checkCSRFClient(final String body) throws SalesforceAuraClientCSRFException {
        final String regex = ".*invalid_csrf.*";
        final Pattern regexPattern = Pattern.compile(regex);
        Matcher matcher = regexPattern.matcher(body);
        if (matcher.find()) {
            throw new SalesforceAuraClientCSRFException();
        }
    }

    /**
     * Site app name cannot be used on this target
     * @param body
     */
    private static void checkAuraNoAccess(final String body) throws SalesforceAuraClientNoAccessException {
        final String regex = ".*markup://aura:noAccess.*";
        final Pattern regexPattern = Pattern.compile(regex);
        Matcher matcher = regexPattern.matcher(body);
        if (matcher.find()) {
            throw new SalesforceAuraClientNoAccessException();
        }
    }
}
