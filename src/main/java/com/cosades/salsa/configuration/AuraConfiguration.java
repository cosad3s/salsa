package com.cosades.salsa.configuration;

import com.cosades.salsa.pojo.SalesforceAuraMessagePojo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

public abstract class AuraConfiguration {
    private static final Logger logger = LoggerFactory.getLogger(AuraConfiguration.class);
    private static final List<String> AURA_URIS;
    private static final LinkedList<String> AURA_APPS;
    private static final Properties AURA_MISC_PROPERTIES;

    static {
        String AURA_URIS_FILENAME = "salesforce-aura-uris.txt";
        AURA_URIS = readSimpleFile(AURA_URIS_FILENAME);
        String AURA_APPS_FILENAME = "salesforce-aura-apps.txt";
        AURA_APPS = new LinkedList<>(readSimpleFile(AURA_APPS_FILENAME));
        String AURA_MISC_FILENAME = "salesforce-aura-misc.properties";
        AURA_MISC_PROPERTIES = loadConfig(AURA_MISC_FILENAME);
    }

    public static List<String> getAuraUris() {
        return AURA_URIS;
    }

    public static LinkedList<String> getAuraApps() {
        return AURA_APPS;
    }

    public static Pattern getAuraRegex() {
        return Pattern.compile(AURA_MISC_PROPERTIES.getProperty("regex"),Pattern.MULTILINE);
    }

    public static SalesforceAuraMessagePojo[] getActionMessageFor(final String actionNameFromFilename) {
        String filename = "salesforce-aura-actions-"+actionNameFromFilename+".json";
        try (InputStream input = AuraConfiguration.class.getClassLoader().getResourceAsStream(filename)) {
            if (input == null) {
                logger.error("[!] Cannot find the filename {}", filename);
                System.exit(-1);
            }

            ObjectMapper objectMapper = new ObjectMapper();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(input))) {
                return objectMapper.readValue(reader, SalesforceAuraMessagePojo[].class);
            }

        } catch (IOException e) {
            logger.error("[!] Error at reading the file {}", filename, e);
            System.exit(-1);
        }
        return null;
    }

    private static Properties loadConfig(String filename) {
        Properties properties = new Properties();
        try (InputStream input = AuraConfiguration.class.getClassLoader().getResourceAsStream(filename)) {
            if (input == null) {
                logger.error("[!] Cannot find the filename {}", filename);
                return null;
            }
            properties.load(input);

        } catch (IOException ex) {
            logger.error("[!] Error at reading properties filename {}", filename, ex);
            return null;
        }

        return properties;
    }

    public static List<String> readSimpleFile(final String filename) {
        List<String> lines = new ArrayList<>();
        try (InputStream input = AuraConfiguration.class.getClassLoader().getResourceAsStream(filename)) {
            if (input == null) {
                logger.error("[!] Cannot find the filename {}", filename);
                System.exit(-1);
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(input))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
            }

        } catch (IOException e) {
            logger.error("[!] Error at reading text filename {}", filename, e);
            System.exit(-1);
        }

        return lines;
    }
}
