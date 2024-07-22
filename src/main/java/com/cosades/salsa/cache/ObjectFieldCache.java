package com.cosades.salsa.cache;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.Set;

@Data
@NoArgsConstructor
public class ObjectFieldCache {
    private Set<String> fields = new HashSet<>();

    public Set<String> addFields(final Set<String> newFields) {
        fields.addAll(newFields);
        return fields;
    }
}
