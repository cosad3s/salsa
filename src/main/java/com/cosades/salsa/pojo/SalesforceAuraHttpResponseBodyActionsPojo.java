package com.cosades.salsa.pojo;

import com.cosades.salsa.enumeration.SalesforceAuraHttpResponseBodyActionsStateEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SalesforceAuraHttpResponseBodyActionsPojo {
    private SalesforceAuraHttpResponseBodyActionsStateEnum state;
    private Map<String, Object> returnValue;
    private Object error[];
}
