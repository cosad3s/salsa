package com.cosades.salsa.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SalesforceAuraMessagePojo {
    String descriptor;
    Map<String, Object> params;

    public void setParamsValue(String paramName, Object paramValue) {
        params.put(paramName, paramValue);
    }
}
