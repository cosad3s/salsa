package com.cosades.salsa.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SalesforceRESTSObjectsListHttpResponseBodyPojo {
    String encoding;
    Integer maxBatchSize;
    SalesforceRESTSObjectsHttpResponseBodySobjectsItemPojo[] sobjects;
}
