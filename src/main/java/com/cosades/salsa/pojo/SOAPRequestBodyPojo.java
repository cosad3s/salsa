package com.cosades.salsa.pojo;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class SOAPRequestBodyPojo {
    private final String DEFAULT_SCHEMA = "urn:enterprise.soap.sforce.com";

    private String sessionId;
    private String request;

    public String build(){
        return this.build(this.buildHeader(), this.buildBody());
    }

    private String build(final String header, final String body) {
        return "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:urn=\"" + DEFAULT_SCHEMA + "\">" +
                header +
                body +
                "</soapenv:Envelope>";
    }

    private String buildHeader() {
        return "<soapenv:Header><urn:SessionHeader><urn:sessionId>" + this.sessionId + "</urn:sessionId></urn:SessionHeader></soapenv:Header>";
    }

    private String buildBody() {
        return "<soapenv:Body>" + this.request + "</soapenv:Body>";
    }

}
