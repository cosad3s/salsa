package com.cosades.salsa.configuration;

import com.cosades.salsa.pojo.SalesforceSObjectFieldPojo;
import com.cosades.salsa.pojo.SalesforceSObjectPojo;
import com.nawforce.runforce.System.SObject;
import org.apache.commons.lang3.StringUtils;
import org.objenesis.Objenesis;
import org.objenesis.ObjenesisStd;
import org.objenesis.instantiator.ObjectInstantiator;
import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public abstract class SalesforceSObjectsConfiguration {
    private static final Logger logger = LoggerFactory.getLogger(SalesforceSObjectsConfiguration.class);
    private static Set<SalesforceSObjectPojo> sObjects;
    private static final String SOBJECTNAMES_WORDLIST_FILENAME = "salesforce-aura-objects.txt";
    public static final String SOBJECTS_PACKAGE_NAME = "com.nawforce.runforce.SObjects";

    public static void init() {
        if (sObjects == null) {
            logger.debug("[x] Building sObjects classes and fields through reflection ...");
            Set<Class<? extends SObject>> sobjectClasses =
                    findAllClassesFromPackage();
            sObjects = buildFromClasses(sobjectClasses);
            logger.info("[*] Found {} object types from introspection.", sObjects.size());
            logger.debug("[x] Found following object types from introspection: {}", sObjects);
        }
    }

    public static SalesforceSObjectPojo getSObject(final String sObjectType) {
        if (StringUtils.isBlank(sObjectType)) {
            logger.error("[!] Cannot find sObject class from unspecified type.");
        }
        if (sObjects == null) {
            init();
        }
        logger.debug("[x] Search for sObject class from type {}", sObjectType);
        Optional<SalesforceSObjectPojo> object = sObjects.stream().filter(sObject -> sObjectType.equalsIgnoreCase(sObject.getSObjectType())).findFirst();
        if (object.isPresent()) {
            logger.debug("[x] Found sObject class from type {}", sObjectType);
            return object.get();
        }
        logger.debug("[x] sObject class not found from type {}", sObjectType);
        return null;
    }

    public static Set<SalesforceSObjectPojo> getSObjects() {
        if (sObjects == null) {
            init();
        }
        return sObjects;
    }

    private static Set<Class<? extends SObject>> findAllClassesFromPackage() {
        Reflections reflections = new Reflections(SalesforceSObjectsConfiguration.SOBJECTS_PACKAGE_NAME);
        Set<Class<? extends SObject>> classes = reflections.getSubTypesOf(SObject.class);
        return classes;
    }

    private static Set<SalesforceSObjectPojo> buildFromClasses(final Set<Class<? extends SObject>> sobjectClasses) {
        Set<SalesforceSObjectPojo> objects = new HashSet<>();
        for (Class<? extends SObject> sObjectClass : sobjectClasses) {
            SalesforceSObjectPojo objectPojo = buildFromClass(sObjectClass, sobjectClasses, false);
            objects.add(objectPojo);
        }
        return objects;
    }

    private static SalesforceSObjectPojo buildFromClass(final Class<? extends SObject> sObjectClass,
                                                final Set<Class<? extends SObject>> sobjectClasses,
                                                final boolean isAttribute) {

        SalesforceSObjectPojo objectPojo = new SalesforceSObjectPojo();
        Field[] fields = sObjectClass.getDeclaredFields();
        for (Field f : fields) {
            String fieldName = f.getName();

            boolean localIsArray = f.getType().isArray();
            Class fieldClass;
            if (!localIsArray) {
                fieldClass = f.getType();
            } else {
                fieldClass = f.getType().getComponentType();
            }

            if (!"Fields".equalsIgnoreCase(f.getName())) {
                if (sobjectClasses.contains(fieldClass)) {

                    if (localIsArray) {
                        SalesforceSObjectFieldPojo<SalesforceSObjectPojo[]> newField = new SalesforceSObjectFieldPojo<>();

                        newField.setName(fieldName);
                        SalesforceSObjectPojo[] values = new SalesforceSObjectPojo[1];
                        SalesforceSObjectPojo value;
                        if (!isAttribute) {
                            value = buildFromClass(fieldClass, sobjectClasses, true);
                            values[0] = value;
                        }
                        newField.setValue(values);
                        objectPojo.getFields().add(newField);
                    } else {
                        SalesforceSObjectFieldPojo<SalesforceSObjectPojo> newField = new SalesforceSObjectFieldPojo<>();
                        newField.setName(fieldName);
                        // To avoid recursive and infinite loop: limit to first attribute for subclass resolving
                        if (!isAttribute) {
                            newField.setValue(buildFromClass(fieldClass, sobjectClasses, true));
                            objectPojo.getFields().add(newField);
                        } else {
                            newField.setValue(new SalesforceSObjectPojo());
                            objectPojo.getFields().add(newField);
                        }
                    }
                } else {

                    if ("SObjectType".equalsIgnoreCase(fieldName)) {
                        SalesforceSObjectFieldPojo<String> newField = new SalesforceSObjectFieldPojo<>();
                        newField.setName(fieldName);
                        String className = sObjectClass.getSimpleName();
                        newField.setValue(className);
                        objectPojo.getFields().add(newField);
                    } else {
                        SalesforceSObjectFieldPojo<Object> newField = new SalesforceSObjectFieldPojo<>();
                        newField.setName(fieldName);

                        Objenesis objenesis = new ObjenesisStd();
                        ObjectInstantiator<?> thingyInstantiator = objenesis.getInstantiatorOf(fieldClass);
                        Object o = thingyInstantiator.newInstance();

                        newField.setValue(o);
                        objectPojo.getFields().add(newField);
                    }
                }
            }
        }

        return objectPojo;
    }

    public static List<String> getWildSObjectNames() {
        List<String> objectNames = AuraConfiguration.readSimpleFile(SOBJECTNAMES_WORDLIST_FILENAME);
        logger.info("[*] Found {} object names from local wordlist.", objectNames.size());
        return objectNames;
    }
}
