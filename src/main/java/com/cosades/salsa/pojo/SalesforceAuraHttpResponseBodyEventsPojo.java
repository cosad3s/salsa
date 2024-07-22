package com.cosades.salsa.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SalesforceAuraHttpResponseBodyEventsPojo {
    private String descriptor;
    private SalesforceAuraHttpResponseBodyEventsAttributesDto attributes;
}
