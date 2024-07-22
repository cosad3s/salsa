package com.cosades.salsa.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SalesforceAuraHttpResponseBodyContextPojo {
    private String context;
    private String app;
    private String fwuid;
    private String contextPath;
    private SalesforceAuraHttpResponseBodyContextGlobalValueProviderPojo[] globalValueProviders;
}
