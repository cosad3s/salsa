package com.cosades.salsa.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.hc.core5.http.Header;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class HttpReponsePojo {
    String body;
    int code;
    Header[] headers;
}
