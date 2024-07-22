package com.cosades.salsa.pojo;

import com.cosades.salsa.utils.HttpUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.StdDateFormat;
import lombok.Data;
import org.apache.commons.text.StringSubstitutor;

import java.util.HashMap;
import java.util.Map;

@Data
public class SalesforceAuraHttpRequestBodyPojo {

    /**
     * Context: app name
     */
    private String appName;
    /**
     * Context: app name
     */
    private String mode;
    /**
     * Context: fwuid
     */
    private String fwuid;
    /**
     * Context: current action descriptor name
     */
    private String descriptorName;
    /**
     * Context: current action descriptor parameters
     */
    private Map<String, Object> params;
    /**
     * Credentials for authenticated request
     */
    private SalesforceAuraCredentialsPojo credentials;

    public SalesforceAuraHttpRequestBodyPojo(final String descriptorName, final Map<String, Object> params, final SalesforceAuraCredentialsPojo credentials) {
        this.descriptorName = descriptorName;
        this.params = params;
        this.credentials = credentials;
    }


    @Override
    public String toString() {
        return "message=" + this.getMessage(descriptorName, params) +
                "&aura.token=" + HttpUtils.urlEncode(credentials.getToken()) +
                "&aura.context=" + this.getAuraContext();
    }

    private String getMessage(final String actionDescriptorName, final Map<String, Object> params) {

        ObjectMapper mapper = new ObjectMapper();
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.setDateFormat(new StdDateFormat().withColonInTimeZone(true));

        Map<String, String> valuesMap = new HashMap<>();
        valuesMap.put("descriptor", actionDescriptorName);
        try {
            valuesMap.put("params", mapper.writeValueAsString(params));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        StringSubstitutor sub = new StringSubstitutor(valuesMap);
        String baseMessageJson = "{\"actions\": [{\"id\": \"123\", \"descriptor\": \"${descriptor}\", \"callingDescriptor\": \"UNKNOWN\", \"params\": ${params}}]}";
        String resolvedString = sub.replace(baseMessageJson);

        return HttpUtils.urlEncode(resolvedString);
    }

    private String getAuraContext() {
        Map<String, String> valuesMap = new HashMap<>();
        valuesMap.put("mode", this.mode);
        valuesMap.put("appName", this.appName);
        valuesMap.put("fwuid", this.fwuid);

        StringSubstitutor sub = new StringSubstitutor(valuesMap);
        String baseContextJson = "{\"mode\": \"${mode}\", \"fwuid\": \"${fwuid}\", \"app\": \"${appName}\", \"loaded\": {\"APPLICATION@markup://${appName}\": \"${appName}\"}, \"dn\": [], \"globals\": {}, \"uad\": false}";
        String resolvedString = sub.replace(baseContextJson);

        return HttpUtils.urlEncode(resolvedString);
    }
}
