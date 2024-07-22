package com.cosades.salsa.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SalesforceAuraHttpResponseBodyEventsAttributesDto {
    private HashMap<String, String> values;
}
