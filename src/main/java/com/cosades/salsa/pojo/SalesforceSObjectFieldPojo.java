package com.cosades.salsa.pojo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SalesforceSObjectFieldPojo<T> {
    String name;
    T value;

    @JsonIgnore
    public String getType() {
        if (value == null) {
            return String.class.getName();
        }
        if (value instanceof SalesforceSObjectPojo) {
            return ((SalesforceSObjectPojo) value).getSObjectType();
        } else {
            return value.getClass().getName();
        }
    }

    @JsonIgnore
    public Object getRealValue() {
        if (value instanceof SalesforceSObjectPojo) {
            if (((SalesforceSObjectPojo) value).haveField("value")) {
                return ((SalesforceSObjectPojo) value).getField("value").getRealValue();
            }
        }
        return value;
    }

    @Override
    public String toString() {
        return "["+ this.getName() + "=" + this.getValue() + "]";
    }
}
