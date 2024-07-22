package com.cosades.salsa.utils;

import com.sforce.soap.partner.sobject.SObject;
import com.cosades.salsa.configuration.SalesforceSObjectsConfiguration;
import com.cosades.salsa.pojo.SalesforceSObjectFieldPojo;
import com.cosades.salsa.pojo.SalesforceSObjectPojo;
import org.objenesis.Objenesis;
import org.objenesis.ObjenesisStd;
import org.objenesis.instantiator.ObjectInstantiator;

import java.security.SecureRandom;
import java.util.*;

public abstract class SObjectUtils {

    public static SObject createLegacySObject(Map<String, Object> record) {
        SObject so = new SObject();

        if (record.containsKey("apiName")) {
            Map<String, Object> flatRecord = new HashMap<>();
            flatRecord.put("sobjectType", record.get("apiName"));
            Map<String, Object> fields = (Map<String, Object>) record.get("fields");
            for (String fieldName : fields.keySet()) {
                flatRecord.put(fieldName, fields.get(fieldName));
            }
            return createLegacySObject(flatRecord);
        }

        for (Map.Entry<String, Object> o : record.entrySet()) {
            if ("sobjectType".equalsIgnoreCase(o.getKey())) {
                so.setType(o.getValue().toString());
            } else {
                if (o.getValue() instanceof Map<?,?>) {
                    so.setField(o.getKey(), createLegacySObject((Map<String, Object>) o.getValue()));
                } else {
                    so.setField(o.getKey(), o.getValue());
                }
            }
        }
        return so;
    }

    public static SalesforceSObjectPojo createSObject(Map<String, Object> record) {
        SalesforceSObjectPojo so = new SalesforceSObjectPojo();

        if (record.containsKey("apiName")) {
            Map<String, Object> flatRecord = new HashMap<>();
            flatRecord.put("sobjectType", record.get("apiName"));
            Map<String, Object> fields = (Map<String, Object>) record.get("fields");
            for (String fieldName : fields.keySet()) {
                flatRecord.put(fieldName, fields.get(fieldName));
            }
            return createSObject(flatRecord);
        }

        for (Map.Entry<String, Object> o : record.entrySet()) {
            String fieldName = o.getKey();
            if (o.getValue() instanceof Map<?,?>) {
                SalesforceSObjectFieldPojo<SalesforceSObjectPojo> newField = new SalesforceSObjectFieldPojo<>();
                newField.setName(fieldName);
                newField.setValue(createSObject((Map<String, Object>) o.getValue()));
                so.getFields().add(newField);
            } else {
                // Unknown case
                if (o.getValue() == null) {
                    SalesforceSObjectFieldPojo<Object> newField = new SalesforceSObjectFieldPojo<>();
                    newField.setName(fieldName);
                    newField.setValue(null);
                    so.getFields().add(newField);
                } else {
                    // Always String
                    if ("displayValue".equalsIgnoreCase(fieldName)) {
                        SalesforceSObjectFieldPojo<String> newField = new SalesforceSObjectFieldPojo<>();
                        newField.setName(fieldName);
                        newField.setValue(o.getValue().toString());
                        so.getFields().add(newField);
                    // Keep initial object type
                    } else {
                        SalesforceSObjectFieldPojo<Object> newField = new SalesforceSObjectFieldPojo<>();
                        newField.setName(fieldName);
                        newField.setValue(o.getValue());
                        so.getFields().add(newField);
                    }
                }

            }
        }
        return so;
    }

    public static SalesforceSObjectPojo merge(Collection<SalesforceSObjectPojo> objects) {
        if (objects != null && !objects.isEmpty()) {
            SalesforceSObjectPojo so = new SalesforceSObjectPojo();
            for (SalesforceSObjectPojo obj : objects) {
                so.getFields().addAll(obj.getFields());
            }
            return so;
        } else {
            return null;
        }
    }

    public static SalesforceSObjectPojo buildEmptyObjectFromType(final String type) {
        SalesforceSObjectPojo objectPojo = new SalesforceSObjectPojo();
        SalesforceSObjectFieldPojo<String> newField = new SalesforceSObjectFieldPojo<>();
        newField.setName("sobjectType");
        newField.setValue(type);
        Set<SalesforceSObjectFieldPojo> fields = new HashSet<>();
        fields.add(newField);
        objectPojo.setFields(fields);
        return objectPojo;
    }

    public static Object generateFakeDataFromField(final SalesforceSObjectFieldPojo field) {
        String type = field.getType();

        Class<?> objectClass;
        try {
            Class.forName(SalesforceSObjectsConfiguration.SOBJECTS_PACKAGE_NAME + "." +type);

            SalesforceSObjectPojo o = new SalesforceSObjectPojo();
            SalesforceSObjectFieldPojo<String> idField = new SalesforceSObjectFieldPojo();
            idField.setName("Id");
            idField.setValue("1234567890ABCDE");
            Set<SalesforceSObjectFieldPojo> fields = new HashSet<>();
            fields.add(idField);
            o.setFields(fields);

            return o;
        } catch (ClassNotFoundException e1) {
            try {
                objectClass = Class.forName(type);
            } catch (ClassNotFoundException e2) {
                return null;
            }
        }

        Objenesis objenesis = new ObjenesisStd(); // or ObjenesisSerializer
        ObjectInstantiator<?> thingyInstantiator = objenesis.getInstantiatorOf(objectClass);
        Object o = thingyInstantiator.newInstance();

        // Specific values

        // For boolean: invert the value
        if (o instanceof Boolean) {
            return !Boolean.TRUE.equals(field.getRealValue());
        }

        // For date, it will be the current date
        if (o instanceof Date) {
            return new Date();
        }

        int i =  new SecureRandom().nextInt(90000) + 10000;

        // For String: generate a String "test-FIELDNAME-RANDOMINT(5)" (Ex: "test-FirstName-84351")
        if (o instanceof String) {
            return "test-" + field.getName() + i;
        }

        // For Integer: random integer of 5 digits
        if (o instanceof Integer) {
            return i;
        }

        return o;
    }
}
