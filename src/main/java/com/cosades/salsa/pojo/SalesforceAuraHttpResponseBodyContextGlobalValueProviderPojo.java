package com.cosades.salsa.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SalesforceAuraHttpResponseBodyContextGlobalValueProviderPojo {
    private String type;
    private Map<String, Object> values;
}
