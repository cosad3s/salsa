package com.cosades.salsa.utils;

import com.cosades.salsa.pojo.SOAPRequestBodyPojo;

public abstract class SOAPRequestFactory {
    public static String createQueryRequest(final String sessionId, final String sObjectType, final String[] sObjectFields) {
        return new SOAPRequestBodyPojo(sessionId, buildQueryRequestBody(sObjectType, sObjectFields)).build();
    }

    // TODO to use
    public static String createUpdateRequest(final String sessionId, final String sObjectType, final String recordId, final String fieldName, final String fieldValue) {
        return new SOAPRequestBodyPojo(sessionId, buildUpdateRequestBody(sObjectType, recordId, fieldName,fieldValue)).build();
    }

    /**
     * Select all objects (limitation to 10 items for global performance)
     * @param sObjectType
     * @param sObjectFields
     * @return
     */
    private static String buildQueryRequestBody(final String sObjectType, final String[] sObjectFields) {
        return "<urn:query><urn:queryString>SELECT " + String.join(",", sObjectFields) + " FROM " + sObjectType + " LIMIT 10</urn:queryString></urn:query>";
    }

    private static String buildUpdateRequestBody(final String sObjectType, final String recordId, final String fieldName, final String fieldValue) {
        return "<urn:update>" +
                "<urn:sObjects xmlns:urn1=\"urn:sobject.partner.soap.sforce.com\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:type=\"" + sObjectType + "\">" +
                "<urn1:Id>" + recordId + "</urn1:Id>" +
                "<urn1:"+ fieldName +">" + fieldValue + "</urn1:" + fieldName + ">" +
                "</urn:sObjects>" +
                "</urn:update>";
    }
}
