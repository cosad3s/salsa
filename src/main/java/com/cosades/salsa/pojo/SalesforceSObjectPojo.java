package com.cosades.salsa.pojo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.text.ParsePosition;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Date;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@NoArgsConstructor
@AllArgsConstructor
public class SalesforceSObjectPojo {

    @Getter
    @Setter
    Set<SalesforceSObjectFieldPojo> fields = new HashSet<>();

    private String type;

    /**
     * Get object type. It is a String because it can be a custom object that cannot be resolved.
     * @return custom object name, standard object name or Java class name
     */
    public String getSObjectType() {

        // Save to not "break" future tests & enhance performances
        if (StringUtils.isBlank(this.type)) {
            // For "root" object
            SalesforceSObjectFieldPojo typeField = this.getField("sobjectType");
            if (typeField != null) {
                this.type = typeField.getValue().toString();
                return this.type;
                // For "attribute" objects (set in fields)
            } else {
                // Search for "value" fieldname
                SalesforceSObjectFieldPojo valueField = this.getField("value");
                if (valueField != null) {
                    Object value = valueField.getValue();
                    if (value == null) {
                        // Default type
                        this.type = String.class.getName();
                        return this.type;
                    } else {

                        // Value is an object
                        if (value instanceof SalesforceSObjectPojo) {
                            this.type = ((SalesforceSObjectPojo) value).getSObjectType();
                            return this.type;
                        }

                        // Field type resolution from String
                        if (value instanceof String) {
                            String valueStr = value.toString();

                            // Check boolean (should never happen)
                            if (valueStr.equalsIgnoreCase("true") || valueStr.equalsIgnoreCase("false")) {
                                this.type = Boolean.class.getName();
                                return this.type;
                            }

                            // Check date
                            DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE;
                            TemporalAccessor r = formatter.parseUnresolved(valueStr, new ParsePosition(0));
                            if (r != null) {
                                this.type = Date.class.getName();
                                return this.type;
                            } else {
                                // Check Integer
                                try {
                                    Integer.parseInt(valueStr);
                                    this.type = Integer.class.getName();
                                    return this.type;
                                } catch (NumberFormatException e2) {
                                    // Check Float
                                    try {
                                        Float.parseFloat(valueStr);
                                        this.type = Integer.class.getName();
                                        return this.type;
                                    } catch (NumberFormatException e3) {
                                        // Default type
                                        this.type = String.class.getName();
                                        return this.type;
                                    }
                                }
                            }

                        // Other cases
                        } else {
                            this.type = value.getClass().getName();
                            return this.type;
                        }
                    }
                } else {
                    // Default value
                    this.type = String.class.getName();
                    return this.type;
                }
            }
        } else {
            return this.type;
        }
    }

    public String getId() {
        SalesforceSObjectFieldPojo idField = this.getField("Id");
        // "root" object
        if (idField != null) {
            if (idField.getValue() instanceof String) {
                return idField.getValue().toString();
            }
            if (idField.getValue() instanceof SalesforceSObjectPojo) {
                return ((SalesforceSObjectPojo)(idField.getValue())).getId();
            }
        }

        // object can be an attribute
        SalesforceSObjectFieldPojo valueField = this.getField("value");
        if (valueField != null) {
            if (valueField.getValue() instanceof SalesforceSObjectPojo) {
                return ((SalesforceSObjectPojo)(valueField.getValue())).getId();
            }
            if (valueField.getValue() instanceof String) {
                return valueField.getValue().toString();
            }
        }

        return "";
    }

    @Override
    public String toString() {
        return "["+ this.getSObjectType() + "]{" + this.getFields() + "}";
    }

    public SalesforceSObjectFieldPojo getField(String fieldName) {
        Optional<SalesforceSObjectFieldPojo> field = fields.stream().filter(f -> fieldName.equalsIgnoreCase(f.getName())).findFirst();
        return field.orElse(null);
    }

    public boolean haveField(String value) {
        return this.getField(value) != null;
    }
}
