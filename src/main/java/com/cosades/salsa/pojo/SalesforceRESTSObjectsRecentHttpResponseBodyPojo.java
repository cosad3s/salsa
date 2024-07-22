package com.cosades.salsa.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SalesforceRESTSObjectsRecentHttpResponseBodyPojo {
    List<Map<String,Object>> recentItems;
}
