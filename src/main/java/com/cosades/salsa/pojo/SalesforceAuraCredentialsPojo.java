package com.cosades.salsa.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SalesforceAuraCredentialsPojo {
    private String username = "";
    private String password = "";
    private String token = "";
    private String sid = "";

    public SalesforceAuraCredentialsPojo(final String username, final String password) {
        this.username = username;
        this.password = password;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SalesforceAuraCredentialsPojo that)) return false;
        return Objects.equals(getUsername(), that.getUsername()) && Objects.equals(getPassword(), that.getPassword());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getUsername(), getPassword());
    }

    @Override
    public String toString() {
        return "{" +
                "username='" + username + '\'' +
                '}';
    }
}
