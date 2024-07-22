package com.cosades.salsa.utils;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.cosades.salsa.pojo.SalesforceSObjectPojo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;

public abstract class DumpUtils {
    private static final Logger logger = LoggerFactory.getLogger(DumpUtils.class);
    private static ObjectMapper om = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public static void dump(final SalesforceSObjectPojo object, final String targetFolder) throws IOException {
        String objectId = object.getId();
        String targetFile = targetFolder + "/" + object.getSObjectType() + "/" + objectId + ".json";

        logger.info("[*] Will dump merged object {} to {}", objectId, targetFile);
        File output = new File(targetFile);
        if (!output.getParentFile().exists()) {
            output.getParentFile().mkdirs();
        }
        om.writeValue(new File(targetFile), object);
    }

    public static void dump(final List<SalesforceSObjectPojo> objects, final String targetFolder) throws IOException {
        for (SalesforceSObjectPojo o : objects) {
            dump(o, targetFolder);
        }
    }
}
