package com.cosades.salsa.exception;

import lombok.Data;

@Data
public class SalesforceAuraClientNotSyncException extends Exception {
    private String fwuid;
    public SalesforceAuraClientNotSyncException(final String fwuid) {
        this.fwuid = fwuid;
    }
}
