package com.cosades.salsa;

import ch.qos.logback.classic.Level;
import com.cosades.salsa.client.SFClient;
import com.cosades.salsa.configuration.SalesforceSObjectsConfiguration;
import com.cosades.salsa.exception.*;
import com.cosades.salsa.pojo.SalesforceAuraCredentialsPojo;
import com.cosades.salsa.pojo.SalesforceSObjectPojo;
import com.cosades.salsa.utils.ArgumentsParserUtils;
import com.cosades.salsa.utils.DumpUtils;
import com.cosades.salsa.utils.SalesforceIdGenerator;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

public class Scanner {

    private static final Logger logger = LoggerFactory.getLogger(Scanner.class);

    public static void main(String[] args) throws IOException {
        String target = ArgumentsParserUtils.getArgument(args, "target");
        String username = ArgumentsParserUtils.getArgument(args, "username");
        String password = ArgumentsParserUtils.getArgument(args, "password");
        String sid = ArgumentsParserUtils.getArgument(args, "sid");
        String token = ArgumentsParserUtils.getArgument(args, "token");
        String proxy = ArgumentsParserUtils.getArgument(args, "proxy");
        String userAgent = ArgumentsParserUtils.getArgument(args, "ua");
        String forcedPath = ArgumentsParserUtils.getArgument(args, "path");
        String recordId = ArgumentsParserUtils.getArgument(args, "id");
        String bruteforce = ArgumentsParserUtils.getArgument(args, "bruteforce");
        String initialRecordTypes = ArgumentsParserUtils.getArgument(args, "types");
        String doTestUpdate = ArgumentsParserUtils.getArgument(args, "update");
        String doTestCreate = ArgumentsParserUtils.getArgument(args, "create");
        String doDump = ArgumentsParserUtils.getArgument(args, "dump");
        String output = ArgumentsParserUtils.getArgument(args, "output");
        String recordtypesfromintrospection = ArgumentsParserUtils.getArgument(args, "typesintrospection");
        String recordtypesfromapi = ArgumentsParserUtils.getArgument(args, "typesapi");
        String recordtypesfromwordlist = ArgumentsParserUtils.getArgument(args, "typeswordlist");
        String appName = ArgumentsParserUtils.getArgument(args, "app");
        String force = ArgumentsParserUtils.getArgument(args, "force");
        String onlyCustomTypes = ArgumentsParserUtils.getArgument(args, "custom");
        String debug = ArgumentsParserUtils.getArgument(args, "debug");
        String trace = ArgumentsParserUtils.getArgument(args, "trace");

        if (Boolean.parseBoolean(debug)) {
            ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("com.cosades");
            root.setLevel(Level.DEBUG);
        }
        if (Boolean.parseBoolean(trace)) {
            ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("com.cosades");
            root.setLevel(Level.TRACE);
        }

        if (StringUtils.isBlank(output)) {
            output = "./output" + new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss", Locale.US).format(new Date());
        }

        if (Boolean.parseBoolean(recordtypesfromintrospection)) {
            logger.info("[*] Launching Salesforce scanner... (warming up, please wait)");
            SalesforceSObjectsConfiguration.init();
        }

        SFClient client = null;
        try {
            client = new SFClient(
                    target,
                    proxy,
                    userAgent,
                    Boolean.parseBoolean(recordtypesfromintrospection),
                    Boolean.parseBoolean(recordtypesfromwordlist),
                    Boolean.parseBoolean(recordtypesfromapi),
                    Boolean.parseBoolean(onlyCustomTypes),
                    appName);
        } catch (HttpClientBadUrlException e) {
            logger.error("[!] Invalid parameters.", e);
            System.exit(-1);
        }

        logger.info("[*] Searching for Salesforce Aura instance on {} ...", target);
        String foundPath = "";
        if (StringUtils.isNotBlank(forcedPath)) {
            foundPath = client.detectAura(forcedPath);
        } else {
            foundPath = client.detectAura();
        }
        if (StringUtils.isNotBlank(foundPath)) {
            logger.warn("[!] Found Salesforce Aura instance on path: {}", foundPath);
        } else {
            logger.error("[!] Warning: Salesforce Aura endpoint not found!");
            if (!Boolean.parseBoolean(force)) {
                System.exit(-1);
            } else {
                logger.warn("[!] Will continue anyway (usage of --force detected).");
            }
        }

        // Authentification
        if (StringUtils.isNotBlank(username)) {
            logger.info("[*] Login for Salesforce Aura instance...");

            if (password == null) {
                password = "";
            }

            SalesforceAuraCredentialsPojo credentials = new SalesforceAuraCredentialsPojo(username, password);
            try {
                client.login(credentials);
            } catch (SalesforceAuraAuthenticationException e) {
                logger.error("[!] Unable to authenticate with provided credentials.");
            }
        } else {
            if (StringUtils.isNotBlank(sid) || StringUtils.isNotBlank(token)) {
                SalesforceAuraCredentialsPojo credentials = new SalesforceAuraCredentialsPojo();
                credentials.setSid(sid);
                credentials.setToken(token);
                logger.warn("[!] Will try with explicitly provided credentials {}", credentials);
                client.updateCredentials(credentials);
            } else {
                logger.warn("[!] Scan will continue as unauthenticated (guest) user ...");
            }
        }

        String[] recordTypesList = null;
        if (StringUtils.isNotBlank(initialRecordTypes)) {
            recordTypesList = initialRecordTypes.split(",");
        }

        List<SalesforceSObjectPojo> objects = new ArrayList<>();

        // Read objects
        if (!Boolean.parseBoolean(doTestCreate)) {

            // Get specific object(s)
            if (StringUtils.isNotBlank(recordId)) {

                Set<String> ids = new HashSet<>();
                ids.add(recordId);

                if (Boolean.parseBoolean(bruteforce)) {
                    try {
                        ids.addAll(SalesforceIdGenerator.generateIds(recordId, 10));
                    } catch (SalesforceInvalidIdException e) {
                        logger.error("[!] Invalid Salesforce record id for bruteforce operation.");
                        System.exit(-1);
                    }
                }

                for (String id : ids) {
                    logger.debug("[x] Try to get {} records for identifiers {}", recordTypesList, ids);
                    try {
                        SalesforceSObjectPojo o = client.getObject(id, recordTypesList);
                        if (o != null) {
                            objects.add(o);
                        }

                    } catch (SalesforceAuraMissingRecordIdException e) {
                        logger.error("[!] Cannot continue: missing recordId");
                        System.exit(-1);
                    } catch (SalesforceAuraInvalidParameters e) {
                        logger.error("[!] Parameters are invalid.");
                        System.exit(-1);
                    } catch (SalesforceAuraClientBadRequestException e) {
                        logger.error("[!] Error during API requests.");
                        System.exit(-1);
                    } catch (SalesforceAuraUnauthenticatedException e) {
                        logger.error("[!] Cannot continue: authentication is mandatory.");
                        System.exit(-1);
                    }
                }

                // Get multiple objects without identifier
            } else {
                try {
                    objects = client.getObjects(recordTypesList);

                } catch (SalesforceAuraClientBadRequestException e) {
                    logger.error("[!] Error during API requests.");
                    System.exit(-1);
                } catch (SalesforceAuraUnauthenticatedException e) {
                    logger.error("[!] Cannot continue: authentication is mandatory.");
                    System.exit(-1);
                }
            }

            if (Boolean.parseBoolean(doDump)) {
                DumpUtils.dump(objects, output);
            }

            // Test fields on read objects (from type(s) or id)
            if (Boolean.parseBoolean(doTestUpdate)) {
                if (objects.isEmpty()) {
                    if (StringUtils.isNotBlank(recordId) && recordTypesList != null) {
                        logger.warn("[!] Will test fields on arbitrary object {} of type {}", recordId, recordTypesList);
                        try {
                            client.writeToObjectFields(recordId, recordTypesList);
                        } catch (SalesforceAuraClientBadRequestException e) {
                            logger.error("[!] Error during API requests.");
                            System.exit(-1);
                        } catch (SalesforceAuraMissingRecordIdException e) {
                            logger.error("[!] Missing recordId for an object for field testing");
                        } catch (SalesforceAuraUnauthenticatedException e) {
                            logger.error("[!] Cannot continue: authentication is mandatory.");
                            System.exit(-1);
                        }
                    } else {
                        logger.error("[!] Missing input data for fields tests (recordId or recordType).");
                    }
                } else {
                    logger.warn("[!] Will test fields on known retrieves objects.");
                    for (SalesforceSObjectPojo object : objects) {
                        try {
                            client.writeToObjectFields(object);
                        } catch (SalesforceAuraClientBadRequestException e) {
                            logger.error("[!] Error during API requests.");
                            System.exit(-1);
                        } catch (SalesforceAuraMissingRecordIdException e) {
                            logger.error("[!] Missing recordId for an object for field testing");
                        } catch (SalesforceAuraUnauthenticatedException e) {
                            logger.error("[!] Cannot continue: authentication is mandatory.");
                            System.exit(-1);
                        }
                    }
                }
            }

        // Test create records
        } else {
            try {
                objects = client.createRecords(recordTypesList);

                if (Boolean.parseBoolean(doDump)) {
                    DumpUtils.dump(objects, output);
                }
            } catch (SalesforceAuraClientBadRequestException e) {
                logger.error("[!] Error during API requests.");
                System.exit(-1);
            } catch (SalesforceAuraUnauthenticatedException e) {
                logger.error("[!] Cannot continue: authentication is mandatory.");
                System.exit(-1);
            }
        }

        logger.info("[*] End of scanning of {}", target);
    }
}