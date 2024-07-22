package com.cosades.salsa.utils;

import com.cosades.salsa.pojo.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class RESTHTTPUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(RESTHTTPUtils.class);

    public static SalesforceRESTSObjectsListHttpResponseBodyPojo parseHttpResponseSObjectsListBody(final String body) {
        if (StringUtils.isNotBlank(body)) {
            try {
                ObjectMapper om = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                return om.readValue(body, SalesforceRESTSObjectsListHttpResponseBodyPojo.class);
            } catch (JsonProcessingException | IllegalArgumentException e) {
                LOGGER.debug("[x] Error on parsing HTTP body {}", body);
            }
        }
        return null;
    }

    public static SalesforceRESTSObjectsRecentHttpResponseBodyPojo parseHttpResponseSObjectsRecentBody(final String body) {
        if (StringUtils.isNotBlank(body)) {
            try {
                ObjectMapper om = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                return om.readValue(body, SalesforceRESTSObjectsRecentHttpResponseBodyPojo.class);
            } catch (JsonProcessingException | IllegalArgumentException e) {
                LOGGER.debug("[x] Error on parsing HTTP body {}", body);

            }
        }
        return null;
    }

    public static SalesforceSObjectPojo parseHttpResponseSObjectBody(final String body, final String type) {
        if (StringUtils.isNotBlank(body)) {
            try {
                ObjectMapper om = new ObjectMapper();
                JsonNode rootNode = om.readTree(body);
                return parseSobjectFromJson(rootNode, type);
            } catch (JsonProcessingException | IllegalArgumentException e) {
                LOGGER.debug("[x] Error on parsing HTTP body {}", body);
            }
        }
        return null;
    }

    public static Set<String> parseHttpResponseSObjectDescribeBody(final String type, final String body) {
        if (StringUtils.isNotBlank(body)) {
            try {
                ObjectMapper om = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                SalesforceRESTSObjectsDescriptionHttpResponseBodyPojo obj = om.readValue(body, SalesforceRESTSObjectsDescriptionHttpResponseBodyPojo.class);
                return Arrays.stream(obj.getFields()).map(f -> type + "." + f.getName()).collect(Collectors.toSet());
            } catch (JsonProcessingException | IllegalArgumentException e) {
                LOGGER.debug("[x] Error on parsing HTTP body {}", body);
            }
        }
        return new HashSet<>();
    }

    public static Set<SalesforceSObjectPojo> parseHttpResponseQueryBody(final String body, final String type) {
        Set<SalesforceSObjectPojo> sObjects = new HashSet<>();
        if (StringUtils.isNotBlank(body)) {
            try {
                ObjectMapper om = new ObjectMapper();
                JsonNode rootNode = om.readTree(body);
                JsonNode records = rootNode.get("records");
                if (!(records instanceof ArrayNode arrayRecords)) {
                    LOGGER.debug("[x] No records found for type {} from Query REST API.", type);
                } else {
                    for (JsonNode o : arrayRecords) {
                        sObjects.add(parseSobjectFromJson(o, type));
                    }
                }
            } catch (JsonProcessingException | IllegalArgumentException e) {
                LOGGER.debug("[x] Error on parsing HTTP body {}", body);
            }
        }
        return sObjects;
    }

    private static SalesforceSObjectPojo parseSobjectFromJson(final JsonNode json, final String type) {
        if (json instanceof ObjectNode objectNode) {
            Set<SalesforceSObjectFieldPojo> fields = new HashSet<>();

            // Find fields: iterate on all keys where values are String
            objectNode.fields().forEachRemaining(entry -> {
                String fieldKey = entry.getKey();
                JsonNode value = entry.getValue();
                if (value.isTextual()) {
                    String fieldValue = value.asText();

                    SalesforceSObjectFieldPojo field = new SalesforceSObjectFieldPojo(fieldKey, fieldValue);
                    fields.add(field);
                }
            });

            fields.add(new SalesforceSObjectFieldPojo("sobjectType",type));

            SalesforceSObjectPojo sobject = new SalesforceSObjectPojo();
            sobject.setFields(fields);

            return sobject;
        } else {
            LOGGER.trace("[xx] Cannot find the sobject due to unparseable JSON (multiple root causes, including permissions settings): {}", json);
            return null;
        }
    }
}
