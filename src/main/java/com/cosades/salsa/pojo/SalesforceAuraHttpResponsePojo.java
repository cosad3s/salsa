package com.cosades.salsa.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SalesforceAuraHttpResponsePojo {
    private SalesforceAuraHttpResponseBodyActionsPojo[] actions;
    private SalesforceAuraHttpResponseBodyContextPojo context;
    private SalesforceAuraHttpResponseBodyEventsPojo[] events;
    private HttpResponsePojo httpResponse;
}
