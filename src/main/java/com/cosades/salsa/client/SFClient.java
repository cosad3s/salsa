package com.cosades.salsa.client;

import com.cosades.salsa.cache.ObjectFieldCache;
import com.cosades.salsa.configuration.AuraConfiguration;
import com.cosades.salsa.configuration.SalesforceSObjectsConfiguration;
import com.cosades.salsa.enumeration.SalesforceAuraHttpResponseBodyActionsStateEnum;
import com.cosades.salsa.exception.*;
import com.cosades.salsa.pojo.*;
import com.cosades.salsa.utils.AuraHttpUtils;
import com.cosades.salsa.utils.SOAPRequestFactory;
import com.cosades.salsa.utils.SOAPUtils;
import com.cosades.salsa.utils.SObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class SFClient extends BaseClient {
    private static final Logger logger = LoggerFactory.getLogger(SFClient.class);
    private final Map<String, ObjectFieldCache> objectFieldCaches = new HashMap<>();
    private final Map<String, List<String>> unsupportedDescriptorObjects = new HashMap<>();
    private final Set<String> recordTypes = new TreeSet<>();
    private boolean introspectionEnabledForTypes = true;
    private boolean wordlistEnabledForTypes = true;
    private boolean apiEnabledForTypes = true;
    private boolean customTypesOnly = false;
    private final String SOBJECTS_REST_API_KEY = "SOBJECTS_REST_API";
    private final String SOAP_API_KEY = "SOAP_API";
    private final String QUERY_DATA_API_KEY = "QUERY_DATA_REST_API";

    public SFClient(final String baseUrl,
                    final String proxy,
                    final String userAgent) throws HttpClientBadUrlException {
        if (StringUtils.isNotBlank(proxy)) {
            String[] proxyParts = proxy.split(":");
            if (proxyParts.length != 2) {
                logger.error("Cannot parse proxy string from {}", proxy);
                System.exit(-1);
            }
            String proxyHost = proxyParts[0];
            int proxyPort = Integer.parseInt(proxyParts[1]);
            this.httpClient = new HttpClient(baseUrl, userAgent, proxyHost,proxyPort);
        } else {
            this.httpClient = new HttpClient(baseUrl, userAgent, null, -1);
        }
        this.auraAppName = this.auraAppNames.pop();
    }

    public SFClient(final String baseUrl,
                    final String proxy,
                    final String userAgent,
                    final boolean introspectionEnabledForTypes,
                    final boolean wordlistEnabledForTypes,
                    final boolean apiEnabledForTypes,
                    final boolean customTypesOnly,
                    final String appName) throws HttpClientBadUrlException {
        this(baseUrl, proxy, userAgent);
        this.introspectionEnabledForTypes = introspectionEnabledForTypes;
        this.wordlistEnabledForTypes = wordlistEnabledForTypes;
        this.apiEnabledForTypes = apiEnabledForTypes;
        this.customTypesOnly = customTypesOnly;
        if (StringUtils.isNotBlank(appName)) {
            this.auraAppNames.addFirst(appName);
        }
    }

    /**
     * Try to identify the Salesforce Aura (check multiple paths and regex matches)
     * @return identified path if any
     */
    public String detectAura() {
        return this.detectAura(null);
    }

    /**
     * Try to identify the Salesforce Aura (check multiple paths and regex matches)
     * @param forcedPath: force a specific path
     * @return identified path if any
     */
    public String detectAura(final String forcedPath) {

        List<String> testedPaths = StringUtils.isNotBlank(forcedPath) ? List.of(forcedPath) : AuraConfiguration.getAuraUris();

        for (String path : testedPaths) {
            logger.debug("[x] Searching for Salesforce Aura on {}", path);
            HttpReponsePojo response = this.httpClient.post(path);
            if ((response.getCode() == 200 || response.getCode() == 401) && AuraHttpUtils.isSalesforceAura(response.getBody())) {
                auraPath = path;
                break;
            }
        }
        return auraPath;
    }

    /**
     * Find object from Salesforce Aura
     * @param recordId: requested recordId (required)
     * @param sObjectTypes: requested objectType(s) (optional; but more chance to find the record)
     */
    public SalesforceSObjectPojo getObject(final String recordId, final String ... sObjectTypes) throws SalesforceAuraMissingRecordIdException, SalesforceAuraInvalidParameters, SalesforceAuraClientBadRequestException, SalesforceAuraUnauthenticatedException {
        Map<SalesforceItemKeyPojo, SalesforceSObjectPojo> items = new HashMap<>();

        if (sObjectTypes == null) {
            logger.info("[*] Looking for sObject with recordId {}.", recordId);
            this.getObject(items, recordId, null);
        } else {
            logger.info("[*] Looking for sObject with recordId {} and type(s) {}.", recordId, sObjectTypes);
            for (String sObjectType : sObjectTypes) {
                this.getObject(items, recordId, sObjectType);
            }
        }

        return SObjectUtils.merge(items.values());
    }

    public List<SalesforceSObjectPojo> getObjects(final String[] sObjectTypes) throws SalesforceAuraClientBadRequestException, SalesforceAuraUnauthenticatedException {
        Map<SalesforceItemKeyPojo, SalesforceSObjectPojo> items = new HashMap<>();

        if (sObjectTypes != null) {
            logger.info("[*] Looking for all objects with type(s) {}.", List.of(sObjectTypes));
            this.getObjects(items, sObjectTypes);
        } else {
            logger.info("[*] Looking for all objects with standard or custom types.");
            this.getObjects(items, null);
        }

        Set<String> retrievedRecordIds = items.keySet().stream().map(SalesforceItemKeyPojo::getRecordId).collect(Collectors.toSet());

        Map<String, List<SalesforceSObjectPojo>> retrievedObjects = new HashMap<>();
        for (String retrievedRecordId : retrievedRecordIds) {
            retrievedObjects.put(retrievedRecordId, items.entrySet().stream().filter(e -> retrievedRecordId.equals(e.getKey().getRecordId())).map(Map.Entry::getValue).toList());
        }

        List<SalesforceSObjectPojo> mergedRetrievedObjects = new ArrayList<>();
        for (List<SalesforceSObjectPojo> objects : retrievedObjects.values()) {
            SalesforceSObjectPojo o = SObjectUtils.merge(objects);
            if (o != null) {
                mergedRetrievedObjects.add(o);
            }
        }

        return mergedRetrievedObjects;
    }

    /**
     * Find object from Salesforce Aura
     *
     * @param items       items bags (descriptor: object found)
     * @param recordId    requested recordId (required)
     * @param sObjectType requested objectType (optional; but more chance to find the record)
     */
    @SuppressWarnings("unchecked")
    private void getObject(Map<SalesforceItemKeyPojo, SalesforceSObjectPojo> items, final String recordId, final String sObjectType) throws SalesforceAuraMissingRecordIdException, SalesforceAuraClientBadRequestException, SalesforceAuraUnauthenticatedException, SalesforceAuraInvalidParameters {

        if (StringUtils.isBlank(recordId)) {
            logger.error("[!] Cannot select record: no recordId specified.");
            throw new SalesforceAuraMissingRecordIdException();
        }

        // Aura service way

        // Find all fields
        if (StringUtils.isNotBlank(sObjectType)) {
            try {
                this.getObjectWithFields(items, recordId, sObjectType, null);
            } catch (SalesforceAuraInvalidParameters e) {
                logger.error("[!] Invalid request parameters.", e);
                throw e;
            }
        }

        SalesforceAuraMessagePojo[] messages = AuraConfiguration.getActionMessageFor("getrecord");
        for (SalesforceAuraMessagePojo message : messages) {

            String descriptor = message.getDescriptor();
            SalesforceItemKeyPojo key = new SalesforceItemKeyPojo(descriptor, recordId, sObjectType);
            if (items.containsKey(key)) {
                logger.trace("[xx] Already lookup for recordId {} with descriptor {}. Skip.", recordId, descriptor);
                continue;
            }
            logger.debug("[x] Will use descriptor {}", descriptor);

            // Set recordId
            if (message.getParams().containsKey("recordId")) {
                message.setParamsValue("recordId", recordId);
            }

            // Manage recordDescriptor case
            if (message.getParams().containsKey("recordDescriptor")) {
                message.setParamsValue("recordDescriptor", recordId + ".undefined.null.null.null.Id.VIEW");
            }

            // Request
            SalesforceAuraHttpRequestBodyPojo requestBodyPojo =
                    new SalesforceAuraHttpRequestBodyPojo(
                            message.getDescriptor(),
                            message.getParams(),
                            this.credentials);

            SalesforceAuraHttpResponseBodyPojo salesforceAuraHttpResponseBody;
            try {
                salesforceAuraHttpResponseBody = this.sendAura(requestBodyPojo);
            } catch (SalesforceAuraClientBadRequestException e) {
                logger.error("[!] Invalid request.", e);
                throw e;
            }

            // Process result
            SalesforceAuraHttpResponseBodyActionsPojo[] actionsResults = salesforceAuraHttpResponseBody.getActions();
            if (actionsResults == null || actionsResults.length == 0) {
                logger.debug("[x] Cannot try find object with recordId {} and descriptor {}: empty results (bad action ?).", recordId, descriptor);
                continue;
            }

            // Check if unknown column has been requested or unsupported record type for the descriptor
            if (!SalesforceAuraHttpResponseBodyActionsStateEnum.SUCCESS.equals(actionsResults[0].getState())) {

                // Unsupported object type
                if (StringUtils.isNotBlank(sObjectType)) {
                    String unsupported = AuraHttpUtils.checkUnsupportedRecordResults(salesforceAuraHttpResponseBody.getRawBody());
                    if (StringUtils.isNotBlank(unsupported)) {
                        this.updateUnsupportedObject(descriptor, unsupported);
                        continue;
                    }
                }
            }

            // Results can be found in the "context" or in "actions" part

            Map<String, Object> returnValue = actionsResults[0].getReturnValue();
            // From "context" part
            if (returnValue == null || returnValue.isEmpty()) {
                logger.debug("[x] Cannot try find object with recordId {} and descriptor {}: no ReturnValue! Will check from globalValueProviders ...", recordId, descriptor);

                SalesforceAuraHttpResponseBodyContextPojo context = salesforceAuraHttpResponseBody.getContext();
                if (context == null) {
                    logger.error("[!] Cannot try find object with recordId {} and descriptor {}: no returned context!", recordId, descriptor);
                    continue;
                }

                SalesforceAuraHttpResponseBodyContextGlobalValueProviderPojo[] globalValueProviderPojos = context.getGlobalValueProviders();
                if (globalValueProviderPojos == null || globalValueProviderPojos.length == 0) {
                    logger.error("[!] Cannot try find object with recordId {} and descriptor {}: no globalValueProviders in returned context!", recordId, descriptor);
                    continue;
                }

                Optional<SalesforceAuraHttpResponseBodyContextGlobalValueProviderPojo> globalValueProviderPojo =
                        Arrays.stream(globalValueProviderPojos).filter(gvp -> "$Record".equalsIgnoreCase(gvp.getType())).findFirst();
                if (globalValueProviderPojo.isEmpty()) {
                    logger.error("[!] Cannot try find object with recordId {} and descriptor {}: no globalValueProviders of type '$record' found in returned context!", recordId, descriptor);
                    continue;
                }

                Map<String, Object> recordValues = globalValueProviderPojo.get().getValues();
                Object records = recordValues.get("records");
                if (records == null || ((Map<String, Object>) records).isEmpty()) {
                    logger.error("[!] No records found from recordId {} and descriptor {}: {}", recordId, descriptor, recordValues);
                    continue;
                }
                Map<String, Object> recordsMap = (Map<String, Object>) records;

                Object recordValue = recordsMap.get(recordId);
                if (recordValue == null) {
                    logger.error("[!] Invalid records found from recordId {} and descriptor {}: {}", recordId, descriptor, recordValues);
                    continue;
                }
                Map<String, Object> recordValueMap = (Map<String, Object>) recordValue;
                if (    recordValueMap.values().toArray().length == 0 ||
                        recordValueMap.containsKey("inaccessible")
                        ) {
                    logger.error("[!] The recordId {} should be existing through descriptor {} but cannot be accessed with those credentials.", recordId, descriptor);
                    continue;
                }

                Map<String, Object> recordMap = (Map<String, Object>) recordValueMap.values().toArray()[0];
                Map<String, Object> record = (Map<String, Object>) recordMap.get("record");

                // Still save the partial object
                items.put(key, SObjectUtils.createSObject(record));

                // Let's try to find the other fields from retrieve type
                if (StringUtils.isBlank(sObjectType)) {
                    this.getObject(items, recordId, recordValueMap.keySet().toArray()[0].toString());
                }

            // From "actions" part
            } else {
                if (returnValue.containsKey("onLoadErrorMessage")) {
                    logger.error("[!] The recordId {} cannot be found through descriptor {} (error: {}).", recordId, descriptor, returnValue.get("onLoadErrorMessage"));
                    continue;
                }

                Object record = returnValue.get("record");
                if (record == null) {
                    logger.error("[!] The recordId {} cannot be found through descriptor {} (empty record).", recordId, descriptor);
                    continue;
                }
                Map<String, Object> recordMap = (Map<String, Object>) record;

                logger.info("[*] Found record {} with descriptor {}!", recordId, descriptor);
                logger.debug("[x] Record found: {}", recordMap);

                SalesforceSObjectPojo o = SObjectUtils.createSObject(recordMap);

                // Add retrieved object
                items.put(key, o);
            }
        }

        // Sobject REST API way
        if (StringUtils.isNotBlank(sObjectType)) {
            logger.debug("[x] Will retrieve sObject {} of type {} from REST sObject API.", recordId, sObjectType);
            // Request
            SalesforceSObjectPojo o = this.sendRESTGetSObject(sObjectType, recordId);
            if (o != null) {
                logger.info("[*] Found sObject {} of type {} from REST sObject API: {}", recordId, sObjectType, o);
                SalesforceItemKeyPojo key = new SalesforceItemKeyPojo(SOBJECTS_REST_API_KEY, recordId, sObjectType);
                items.put(key, o);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void getObjects(Map<SalesforceItemKeyPojo, SalesforceSObjectPojo> items, String[] recordTypes) throws SalesforceAuraClientBadRequestException, SalesforceAuraUnauthenticatedException {

        Set<String> recordTypesToRetrieve;
        if (recordTypes != null) {
            recordTypesToRetrieve = new TreeSet<>(Arrays.asList(recordTypes));
        } else {
            recordTypesToRetrieve = this.generateRecordTypesList();
        }

        logger.debug("[x] Will try to get all objects from {} types]", recordTypesToRetrieve.size());
        logger.trace("[xx] Will try to get all objects for types [{}]", recordTypesToRetrieve);

        // Aura service way
        SalesforceAuraMessagePojo[] auraMessages = AuraConfiguration.getActionMessageFor("getrecords");
        for (String recordTypeToRetrieve : recordTypesToRetrieve) {

            logger.info("[*] Aura: looking for records for type {}", recordTypeToRetrieve);

            for (SalesforceAuraMessagePojo message : auraMessages) {

                String descriptor = message.getDescriptor();

                if (!this.isObjectSupportedBy(descriptor, recordTypeToRetrieve)) {
                    continue;
                }

                if (message.getParams().containsKey("objectApiName")) {
                    message.setParamsValue("objectApiName", recordTypeToRetrieve);
                }

                if (message.getParams().containsKey("scope")) {
                    message.setParamsValue("scope", recordTypeToRetrieve);
                }
                if (message.getParams().containsKey("term")) {
                    // Default term (2 letters minimum)
                    message.setParamsValue("term", "th$$");
                }
                if (message.getParams().containsKey("pageSize")) {
                    message.setParamsValue("pageSize", "100");
                }
                if (message.getParams().containsKey("currentPage")) {
                    message.setParamsValue("currentPage", "0");
                }
                if (message.getParams().containsKey("entityNameOrId")) {
                    message.setParamsValue("entityNameOrId", recordTypeToRetrieve);
                }
                if (message.getParams().containsKey("limit")) {
                    message.setParamsValue("limit", "100");
                }

                // Request
                SalesforceAuraHttpRequestBodyPojo requestBodyPojo =
                        new SalesforceAuraHttpRequestBodyPojo(
                                message.getDescriptor(),
                                message.getParams(),
                                this.credentials);

                SalesforceAuraHttpResponseBodyPojo salesforceAuraHttpResponseBody;
                try {
                    salesforceAuraHttpResponseBody = this.sendAura(requestBodyPojo);
                } catch (SalesforceAuraClientBadRequestException e) {
                    logger.debug("[!] Invalid request.", e);
                    throw e;
                } catch (SalesforceAuraInvalidParameters e) {
                    logger.debug("[!] Invalid request parameters.", e);
                    continue;
                }

                // Process result
                SalesforceAuraHttpResponseBodyActionsPojo[] actionsResults = salesforceAuraHttpResponseBody.getActions();
                if (actionsResults == null || actionsResults.length == 0) {
                    logger.debug("[x] Cannot find records of type {} from descriptor {}: empty results (bad action ?).", recordTypeToRetrieve, descriptor);
                    continue;
                }

                // "Maybe" an error happened
                if (!SalesforceAuraHttpResponseBodyActionsStateEnum.SUCCESS.equals(actionsResults[0].getState())) {
                    // Unsupported object type
                    String unsupported = AuraHttpUtils.checkUnsupportedRecordResults(salesforceAuraHttpResponseBody.getRawBody());
                    if (StringUtils.isNotBlank(unsupported)) {
                        this.updateUnsupportedObject(descriptor, unsupported);
                        continue;
                    }
                }

                Map<String, Object> returnValue = actionsResults[0].getReturnValue();

                if (returnValue == null || returnValue.isEmpty()) {
                    logger.debug("[!] Records of type {} cannot be found through descriptor {}.", recordTypeToRetrieve, descriptor);
                    continue;
                }
                List<SalesforceSObjectPojo> objects = new ArrayList<>();

                Object records1 = returnValue.get("records");
                if (records1 == null) {

                    // "ListView" case
                    Object listOfPartialRecordsObj = returnValue.get("lists");
                    if (listOfPartialRecordsObj != null) {
                        List<Map<String, Object>> listOfPartialRecords = (List<Map<String, Object>>) listOfPartialRecordsObj;
                        logger.debug("[x] Found {} records from returned list: {}", listOfPartialRecords.size(), listOfPartialRecords);
                        for (Map<String, Object> partialRecord : listOfPartialRecords) {
                            Object id = partialRecord.get("id");

                            if (id != null) {
                                try {
                                    SalesforceSObjectPojo sObjectPojo = this.getObject(id.toString(), "ListView");
                                    objects.add(sObjectPojo);
                                } catch (SalesforceAuraMissingRecordIdException e) {
                                    logger.error("[!] Error on search for record {} from previous list.", id);
                                } catch (SalesforceAuraInvalidParameters e) {
                                    logger.error("[!] Invalid parameter transmitted to Aura.");
                                }
                            }
                        }
                    }

                    // "result" case
                    Object listOfRecordsObj = returnValue.get("result");
                    if (listOfRecordsObj == null) {
                        logger.debug("[!] Records of type {} cannot be found through descriptor {} (empty records).", recordTypeToRetrieve, descriptor);
                        continue;
                    }

                    List<Map<String, Map<String, Object>>> listOfRecords = (List<Map<String, Map<String, Object>>>) listOfRecordsObj;
                    for (Map<String, Map<String, Object>> recordMap : listOfRecords) {
                        Map<String, Object> record = recordMap.get("record");

                        // Enrich the results
                        String recordId = record.get("Id").toString();
                        SalesforceSObjectPojo so;
                        try {
                            so = this.getObject(recordId, recordTypeToRetrieve);
                        } catch (SalesforceAuraMissingRecordIdException | SalesforceAuraInvalidParameters e) {
                            logger.error("[!] Error on search for record {}.", recordId);

                            // Error: just save the initial record
                            SalesforceSObjectPojo o = SObjectUtils.createSObject(record);

                            // Update fields cache if necessary
                            String sObjectType = o.getSObjectType();
                            if (!objectFieldCaches.containsKey(sObjectType)) {
                                ObjectFieldCache cache = new ObjectFieldCache();
                                objectFieldCaches.put(sObjectType, cache);
                            }
                            ObjectFieldCache cache = objectFieldCaches.get(sObjectType);
                            cache.addFields(o.getFields().stream().map(f -> sObjectType + "." + f.getName()).collect(Collectors.toSet()));

                            objects.add(o);
                            continue;
                        }
                        if (so != null) {
                            objects.add(so);
                        }
                    }

                } else {
                    List<Map<String, Object>> records;
                    if (records1 instanceof List) {
                        records = (List<Map<String, Object>>) records1;
                    } else {
                        records = (List<Map<String, Object>>)(((Map<String, Object>) records1).get("records"));
                    }

                    logger.debug("[x] Will try to get all fields from all got records.");
                    for (Map<String, Object> o : records) {
                        String recordId = o.get("id").toString();

                        // Enrich the results
                        SalesforceSObjectPojo so;
                        try {
                            so = this.getObject(recordId, recordTypeToRetrieve);
                        } catch (SalesforceAuraMissingRecordIdException | SalesforceAuraInvalidParameters e) {
                            logger.error("[!] Error on search for record {}.", recordId);

                            // Error: just save the initial record
                            SalesforceSObjectPojo newSObject = SObjectUtils.createSObject(o);

                            // Update fields cache if necessary
                            String sObjectType = newSObject.getSObjectType();
                            if (!objectFieldCaches.containsKey(sObjectType)) {
                                ObjectFieldCache cache = new ObjectFieldCache();
                                objectFieldCaches.put(sObjectType, cache);
                            }
                            ObjectFieldCache cache = objectFieldCaches.get(sObjectType);
                            cache.addFields(newSObject.getFields().stream().map(f -> sObjectType + "." + f.getName()).collect(Collectors.toSet()));

                            objects.add(newSObject);
                            continue;
                        }
                        if (so != null) {
                            objects.add(so);
                        }
                    }
                }

                if (!objects.isEmpty()) {
                    logger.info("[*] {} object(s) retrieved with descriptor {} from object type {}!", objects.size(), descriptor, recordTypeToRetrieve);
                    for (SalesforceSObjectPojo object : objects) {
                        items.put(new SalesforceItemKeyPojo(descriptor, object.getId(), object.getSObjectType()), object);
                    }

                } else {
                    logger.debug("[!] Records of type {} cannot be found through descriptor {}.", recordTypeToRetrieve, descriptor);
                }
            }

            // API
            if (StringUtils.isNotBlank(this.credentials.getSid())) {

                // SOAP API
                logger.info("[*] SOAP: looking for records for type {}", recordTypeToRetrieve);
                String request = SOAPRequestFactory.createQueryRequest(this.credentials.getSid(), recordTypeToRetrieve, new String[]{"Id"});
                HttpReponsePojo response = this.sendSOAP(request);
                if (response != null && StringUtils.isNotBlank(response.getBody()) && !SOAPUtils.checkUnsupportedEntity(response.getBody())) {

                    try {
                        List<String> sObjectIds = SOAPUtils.parseSOAPRecords(response.getBody());

                        logger.info("[*] Found {} entities of types {} through SOAP API!", sObjectIds.size(), recordTypeToRetrieve);
                        logger.trace("[xx] Will try to find all object data matching these IDs.");
                        for (String id : sObjectIds) {
                            SalesforceSObjectPojo sobject = this.getObject(id, recordTypeToRetrieve);
                            logger.trace("[xx] SOAP: Found object {}.", sobject);
                            items.put(new SalesforceItemKeyPojo(SOAP_API_KEY, id, recordTypeToRetrieve), sobject);
                        }

                    } catch (SalesforceSOAPParsingException e) {
                        logger.error("[!] Cannot parse SOAP response", e);
                    } catch (SalesforceAuraInvalidParameters e) {
                        logger.error("[!] Invalid parameter transmitted to Aura.");
                    } catch (SalesforceAuraMissingRecordIdException e) {
                        logger.error("[!] Record id cannot be found.");
                    }
                }

                // Query Data REST API
                logger.info("[*] Query Data API: looking for records for type {}", recordTypeToRetrieve);
                Set<SalesforceSObjectPojo> sobjectsFromQuery = this.sendRESTGetSObjectsFromQuery(recordTypeToRetrieve);
                if (!sobjectsFromQuery.isEmpty()) {
                    logger.info("[*] Found {} entities of types {} through Query Data REST API!", sobjectsFromQuery.size(), recordTypeToRetrieve);
                    for (SalesforceSObjectPojo o : sobjectsFromQuery) {
                        logger.trace("[xx] Query Data API: Found object {}.", o);
                        items.put(new SalesforceItemKeyPojo(QUERY_DATA_API_KEY, o.getId(), recordTypeToRetrieve), o);
                    }
                }

                // Sobject Data REST API
                logger.info("[*] SObject Data API: looking for records for type {}", recordTypeToRetrieve);
                SalesforceRESTSObjectsRecentHttpResponseBodyPojo sobjectsRecentsFromQuery = this.sendRESTGetSObjectsRecent(recordTypeToRetrieve);
                if (sobjectsRecentsFromQuery != null && !sobjectsRecentsFromQuery.getRecentItems().isEmpty()) {
                    logger.info("[*] Found {} entities of types {} through SObject Data REST API!", sobjectsRecentsFromQuery.getRecentItems().size(), recordTypeToRetrieve);
                    for (Map o : sobjectsRecentsFromQuery.getRecentItems()) {
                        logger.trace("[xx] Query Data API: Found object {}.", o);

                        try {
                            SalesforceSObjectPojo sobject = this.getObject(o.get("Id").toString(), recordTypeToRetrieve);
                            logger.trace("[xx] Query Data API: Found rich object {}.", sobject);
                            items.put(new SalesforceItemKeyPojo(SOBJECTS_REST_API_KEY, sobject.getId(), recordTypeToRetrieve), sobject);
                        } catch (SalesforceAuraMissingRecordIdException e) {
                            logger.error("[!] Record id cannot be found.");
                        } catch (SalesforceAuraInvalidParameters e) {
                            logger.error("[!] Invalid parameter transmitted to Aura.");
                        }
                    }
                }
            } else {
                logger.debug("[x] Cannot continue with API testing: no authentication SID provided.");
            }
        }
    }

    public List<SalesforceSObjectPojo> createRecords(final String[] sObjectTypes) throws SalesforceAuraClientBadRequestException, SalesforceAuraUnauthenticatedException {
        Map<SalesforceItemKeyPojo, SalesforceSObjectPojo> items = new HashMap<>();

        if (sObjectTypes != null) {
            logger.info("[*] Trying to create all objects with type(s) {}.", List.of(sObjectTypes));
            this.createRecords(items, sObjectTypes);
        } else {
            logger.info("[*] Trying to create all types of objects.");
            this.createRecords(items, null);
        }

        Set<String> recordIds = items.keySet().stream().map(SalesforceItemKeyPojo::getRecordId).collect(Collectors.toSet());

        Map<String, List<SalesforceSObjectPojo>> createdObjects = new HashMap<>();
        for (String recordId : recordIds) {
            createdObjects.put(recordId, items.entrySet().stream().filter(e -> recordId.equals(e.getKey().getRecordId())).map(Map.Entry::getValue).toList());
        }

        List<SalesforceSObjectPojo> mergedObjects = new ArrayList<>();
        for (List<SalesforceSObjectPojo> objects : createdObjects.values()) {
            SalesforceSObjectPojo o =SObjectUtils.merge(objects);
            if (o != null) {
                mergedObjects.add(o);
            }
        }

        return mergedObjects;
    }

    private void createRecords(Map<SalesforceItemKeyPojo, SalesforceSObjectPojo> items, String[] recordTypes) throws SalesforceAuraClientBadRequestException, SalesforceAuraUnauthenticatedException {
        SalesforceAuraMessagePojo[] messages = AuraConfiguration.getActionMessageFor("createrecord");

        Set<String> types;
        if (recordTypes != null) {
            types = new TreeSet<>(Arrays.asList(recordTypes));
        } else {
            types = this.generateRecordTypesList();
        }

        logger.debug("[x] Will try to create objects for {} types]", types.size());
        logger.trace("[xx] Will try to create objects for types [{}]", types);

        for (final String type : types) {
            for (SalesforceAuraMessagePojo message : messages) {

                String descriptor = message.getDescriptor();

                if (!this.isObjectSupportedBy(descriptor, type)) {
                    continue;
                }

                message.setParamsValue("apiName", type);

                final Set<String> fieldNames;
                try {
                    fieldNames = this.getObjectFieldNames(type);
                } catch (SalesforceAuraInvalidParameters e) {
                    logger.error("[!] Invalid request parameters.", e);
                    continue;
                }
                final Set<SalesforceSObjectFieldPojo> fields = fieldNames.stream().map(f -> new SalesforceSObjectFieldPojo(f, "")).collect(Collectors.toSet());

                if (fields.isEmpty()) {
                    logger.warn("[!] Will try to create object type {} without field through descriptor {} (cannot retrieve object info).", type, descriptor);
                    message.setParamsValue("fields", new HashMap<>());

                    // Request
                    SalesforceAuraHttpRequestBodyPojo requestBodyPojo =
                            new SalesforceAuraHttpRequestBodyPojo(
                                    descriptor,
                                    message.getParams(),
                                    this.credentials);

                    SalesforceAuraHttpResponseBodyPojo salesforceAuraHttpResponseBody;
                    try {
                        salesforceAuraHttpResponseBody = this.sendAura(requestBodyPojo);
                    } catch (SalesforceAuraClientBadRequestException e) {
                        logger.error("[!] Invalid request.", e);
                        throw e;
                    } catch (SalesforceAuraInvalidParameters e) {
                        logger.error("[!] Invalid request parameters.", e);
                        continue;
                    }

                    // Process result
                    SalesforceAuraHttpResponseBodyActionsPojo[] actionsResults = salesforceAuraHttpResponseBody.getActions();
                    if (actionsResults == null || actionsResults.length == 0) {
                        logger.debug("[x] Cannot retrieve sobject types from descriptor {}: empty results (bad action ?).", descriptor);
                        return;
                    }

                    Map<String, Object> returnValue = actionsResults[0].getReturnValue();
                    if (    !SalesforceAuraHttpResponseBodyActionsStateEnum.SUCCESS.equals(actionsResults[0].getState()) ||
                            returnValue == null || returnValue.isEmpty()) {
                        logger.debug("[x] Cannot create object {} through descriptor {}.", type, descriptor);
                        continue;
                    }

                    logger.info("[*] Object {} can be created without field through descriptor {}! Feel free to manually explore other fields by yourself.", type, descriptor);
                    SalesforceSObjectPojo createdObject = SObjectUtils.createSObject(returnValue);
                    SalesforceItemKeyPojo key = new SalesforceItemKeyPojo(descriptor, createdObject.getId(), type);
                    items.put(key, createdObject);
                } else {
                    logger.warn("[!] Will try to create each {} fields on object type {} through descriptor {}.", fields.size(), type, descriptor);

                    for (final SalesforceSObjectFieldPojo field : fields) {

                        // Set fields
                        Map<String, Object> fieldsMap = new HashMap<>();
                        String fieldName = field.getName();

                        Object fieldValue = SObjectUtils.generateFakeDataFromField(field);
                        if (fieldValue instanceof SalesforceSObjectPojo) {
                            fieldName = fieldName + ".Id";
                            fieldValue = ((SalesforceSObjectPojo) fieldValue).getField("Id").getValue();
                        }
                        fieldsMap.put(fieldName, fieldValue);
                        logger.debug("[x] Try with fields {}", fieldsMap);
                        message.setParamsValue("fields", fieldsMap);

                        // Request
                        SalesforceAuraHttpRequestBodyPojo requestBodyPojo =
                                new SalesforceAuraHttpRequestBodyPojo(
                                        descriptor,
                                        message.getParams(),
                                        this.credentials);

                        SalesforceAuraHttpResponseBodyPojo salesforceAuraHttpResponseBody;
                        try {
                            salesforceAuraHttpResponseBody = this.sendAura(requestBodyPojo);
                        } catch (SalesforceAuraClientBadRequestException e) {
                            logger.error("[!] Invalid request.", e);
                            throw e;
                        } catch (SalesforceAuraInvalidParameters e) {
                            logger.error("[!] Invalid request parameters.", e);
                            continue;
                        }

                        // Process result
                        SalesforceAuraHttpResponseBodyActionsPojo[] actionsResults = salesforceAuraHttpResponseBody.getActions();
                        if (actionsResults == null || actionsResults.length == 0) {
                            logger.debug("[x] Cannot retrieve sobject types from descriptor {}: empty results (bad action ?).", descriptor);
                            return;
                        }

                        Map<String, Object> returnValue = actionsResults[0].getReturnValue();
                        if (!SalesforceAuraHttpResponseBodyActionsStateEnum.SUCCESS.equals(actionsResults[0].getState()) ||
                                returnValue == null || returnValue.isEmpty()) {
                            logger.debug("[x] Cannot create object {} with field {} through descriptor {}.", type, fieldName, descriptor);
                            continue;
                        }

                        logger.info("[*] Object {} can be created with minimum field {} through descriptor {}! Feel free to manually explore other fields by yourself.", type, fieldName, descriptor);
                        SalesforceSObjectPojo createdObject = SObjectUtils.createSObject(returnValue);
                        SalesforceItemKeyPojo key = new SalesforceItemKeyPojo(descriptor, createdObject.getId(), type);
                        items.put(key, createdObject);
                        break;
                    }
                }
            }
        }
    }


    @SuppressWarnings("rawtypes")
    public void writeToObjectFields(final SalesforceSObjectPojo object) throws SalesforceAuraClientBadRequestException, SalesforceAuraMissingRecordIdException, SalesforceAuraUnauthenticatedException {
        if (object == null) {
            logger.error("[!] Cannot write to null object.");
            return;
        }

        final String objectId = object.getId();
        if (StringUtils.isBlank(objectId)) {
            logger.error("[!] Cannot test objects fields: no Id from provided object {}.", object);
            throw new SalesforceAuraMissingRecordIdException();
        }

        final Set<SalesforceSObjectFieldPojo> fields = object.getFields();
        final String objectType = object.getSObjectType();

        this.writeToObjectFields(objectId, objectType, fields);
    }

    @SuppressWarnings({"rawtypes","unchecked"})
    public void writeToObjectFields(final String objectId, final String[] objectTypes) throws SalesforceAuraClientBadRequestException, SalesforceAuraMissingRecordIdException, SalesforceAuraUnauthenticatedException {

        if (StringUtils.isBlank(objectId)) {
            logger.error("[!] Cannot test objects fields: no Id provided.");
            throw new SalesforceAuraMissingRecordIdException();
        }

        if (objectTypes == null) {
            logger.error("[!] Cannot test objects fields: objectType Id provided.");
            throw new SalesforceAuraMissingRecordIdException();
        }

        for (String objectType : objectTypes) {
            final Set<String> fieldNames;
            try {
                fieldNames = this.getObjectFieldNames(objectType);
            } catch (SalesforceAuraInvalidParameters e) {
                logger.error("[!] Invalid request parameters.", e);
                continue;
            }
            final Set<SalesforceSObjectFieldPojo> fields = fieldNames.stream().map(f -> new SalesforceSObjectFieldPojo(f, "")).collect(Collectors.toSet());

            logger.warn("[!] All field types will be String as no input object: higher error rate.");

            if (fields.isEmpty()) {
                logger.error("[!] Cannot write data to the fields of specified object {}: no field identified.", objectId);
            }

            this.writeToObjectFields(objectId, objectType, fields);
        }
    }

    @SuppressWarnings("rawtypes")
    private void writeToObjectFields(final String objectId, final String objectType, final Set<SalesforceSObjectFieldPojo> fields) throws SalesforceAuraClientBadRequestException, SalesforceAuraMissingRecordIdException, SalesforceAuraUnauthenticatedException {

        if (fields == null || fields.isEmpty()) {
            logger.error("[!] Cannot write data to the fields of specified object {}: no field identified.", objectId);
            return;
        }


        for (final SalesforceSObjectFieldPojo field : fields) {

            // Aura update
            SalesforceAuraMessagePojo[] messages= AuraConfiguration.getActionMessageFor("writerecordfields");
            final SalesforceAuraMessagePojo message = messages[0];
            final String descriptor = message.getDescriptor();

            logger.info("[*] Will try to update each {} fields on sObjects {} through descriptor {}.", fields.size(), objectId, descriptor);

            // Set record type
            message.setParamsValue("apiName", objectType);
            // Set record id
            message.setParamsValue("recordId", objectId);

            // Set fields
            Map<String, Map<String, Object>> recordInput = new HashMap<>();
            Map<String, Object> fieldsMap = new HashMap<>();
            String fieldName = field.getName();

            Object fieldValue = SObjectUtils.generateFakeDataFromField(field);
            if (fieldValue instanceof SalesforceSObjectPojo) {
                fieldName = fieldName + ".Id";
                fieldValue = ((SalesforceSObjectPojo) fieldValue).getField("Id").getValue();
            }
            fieldsMap.put(fieldName, fieldValue);
            recordInput.put("fields", fieldsMap);
            logger.debug("[x] Try with fields {}", fieldsMap);
            message.setParamsValue("recordInput", recordInput);

            // Request
            SalesforceAuraHttpRequestBodyPojo requestBodyPojo =
                    new SalesforceAuraHttpRequestBodyPojo(
                            message.getDescriptor(),
                            message.getParams(),
                            this.credentials);

            SalesforceAuraHttpResponseBodyPojo salesforceAuraHttpResponseBody;
            try {
                salesforceAuraHttpResponseBody = this.sendAura(requestBodyPojo);
            } catch (SalesforceAuraClientBadRequestException e) {
                logger.error("[!] Invalid request.", e);
                throw e;
            } catch (SalesforceAuraInvalidParameters e) {
                logger.error("[!] Invalid request parameters.", e);
                continue;
            }

            // Process result
            SalesforceAuraHttpResponseBodyActionsPojo[] actionsResults = salesforceAuraHttpResponseBody.getActions();
            if (actionsResults == null || actionsResults.length == 0) {
                logger.debug("[x] Cannot retrieve sobject types from descriptor {}: empty results (bad action ?).", descriptor);
                return;
            }

            // "Maybe" an error happened
            if (!SalesforceAuraHttpResponseBodyActionsStateEnum.SUCCESS.equals(actionsResults[0].getState())) {
                logger.debug("[x] Cannot update field {} from object {} {} with descriptor {}.", fieldName, objectType, objectId, descriptor);
                if (AuraHttpUtils.checkUnsupportedUpdate(salesforceAuraHttpResponseBody.getRawBody())) {
                    logger.warn("[!] [{}] record cannot be updated (tested on {} with descriptor {}) due to security measures.", objectType, objectId, descriptor);
                    return;
                }
                if (AuraHttpUtils.checkInvalidValueForField(salesforceAuraHttpResponseBody.getRawBody())) {
                    logger.warn("[!] Field probably updatable: [{}][{}] (tested on {} with descriptor {}) but got error due to probable invalid / custom type! Check manually.", objectType, fieldName, objectId, descriptor);
                }
                else if (AuraHttpUtils.checkSecuredField(salesforceAuraHttpResponseBody.getRawBody())) {
                    logger.warn("[!] Field not updatable: [{}][{}] (tested on {} with descriptor {}) due to security measures.", objectType, fieldName, objectId, descriptor);
                }
                continue;
            }

            // Check if the field has been updated
            Map<SalesforceItemKeyPojo, SalesforceSObjectPojo> items = new HashMap<>();
            try {
                this.getObjectWithFields(items, objectId, objectType, Set.of(objectType + "." + fieldName));
            } catch (SalesforceAuraInvalidParameters e) {
                logger.error("[!] Invalid request parameters.", e);
                continue;
            }
            if (items.isEmpty()) {
                logger.error("[!] Cannot retrieve updated object {} according to the updated field {}.", objectId, fieldName);
            }
            SalesforceSObjectPojo updatedItem = items.values().toArray(new SalesforceSObjectPojo[0])[0];
            SalesforceSObjectFieldPojo updatedField = updatedItem.getField(fieldName);
            if (updatedField.getRealValue().equals(fieldValue)) {
                logger.info("[*] Field updatable: [{}][{}] (tested on {} with descriptor {})!", objectType, fieldName, objectId, descriptor);
            } else {
                logger.debug("[x] It appears that the field {} from object {} {} could not be updated with descriptor {}!", fieldName, objectType, objectId, descriptor);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Set<SalesforceSObjectPojo> getCurrentObjectsTypeFromAura() throws SalesforceAuraClientBadRequestException, SalesforceAuraUnauthenticatedException {

        logger.info("[*] Will retrieve all sObjects types known by the target from Aura service.");

        Set<SalesforceSObjectPojo> knownObjects = new HashSet<>();

        SalesforceAuraMessagePojo[] messages = AuraConfiguration.getActionMessageFor("getrecordtypes");

        for (SalesforceAuraMessagePojo message : messages) {
            String descriptor = message.getDescriptor();

            // Request
            SalesforceAuraHttpRequestBodyPojo requestBodyPojo =
                    new SalesforceAuraHttpRequestBodyPojo(
                            message.getDescriptor(),
                            message.getParams(),
                            this.credentials);

            SalesforceAuraHttpResponseBodyPojo salesforceAuraHttpResponseBody;
            try {
                salesforceAuraHttpResponseBody = this.sendAura(requestBodyPojo);
            } catch (SalesforceAuraClientBadRequestException e) {
                logger.error("[!] Invalid request.", e);
                throw e;
            } catch (SalesforceAuraInvalidParameters e) {
                logger.error("[!] Invalid request parameters.", e);
                continue;
            }

            // Process result
            SalesforceAuraHttpResponseBodyActionsPojo[] actionsResults = salesforceAuraHttpResponseBody.getActions();
            if (actionsResults == null || actionsResults.length == 0) {
                logger.debug("[x] Cannot retrieve sobject types from descriptor {}: empty results (bad action ?).", descriptor);
                return knownObjects;
            }

            Map<String, Object> returnValue = actionsResults[0].getReturnValue();
            if (returnValue == null || returnValue.isEmpty()) {
                logger.debug("[x] Cannot retrieve sobject types from descriptor {}: no return value.", descriptor);
                return knownObjects;
            }

            Object rootLayoutConfigObj = returnValue.get("rootLayoutConfig");
            // Entities can be in "rootLayoutConfig" sub-attributes or "apiNamesToKeyPrefixes"
            if (rootLayoutConfigObj == null) {
                logger.trace("[x] Searching for sobject types from descriptor {}: no rootLayoutConfig value.", descriptor);

                Object apiNamesToKeyPrefixesObj = returnValue.get("apiNamesToKeyPrefixes");
                if (apiNamesToKeyPrefixesObj == null) {
                    logger.debug("[x] Cannot retrieve sobject types from descriptor {}: no apiNamesToKeyPrefixes value.", descriptor);
                    return knownObjects;
                }

                Map<String, Object> apiNamesToKeyPrefixes = (Map<String, Object>) apiNamesToKeyPrefixesObj;
                if (!apiNamesToKeyPrefixes.isEmpty()) {
                    for (String supportedEntity : apiNamesToKeyPrefixes.keySet()) {
                        SalesforceSObjectPojo sObject = SObjectUtils.buildEmptyObjectFromType(supportedEntity);
                        knownObjects.add(sObject);
                    }
                }

            } else {
                Map<String, Object> rootLayoutConfig = (Map<String, Object>)  rootLayoutConfigObj;

                Object attributesObj = rootLayoutConfig.get("attributes");
                if (attributesObj == null) {
                    logger.debug("[x] Cannot retrieve sobject types from descriptor {}: no attributes value.", descriptor);
                    return knownObjects;
                }
                Map<String, Object> attributes = (Map<String, Object>)  attributesObj;

                Object valuesObj = attributes.get("values");
                if (valuesObj == null) {
                    logger.debug("[x] Cannot retrieve sobject types from descriptor {}: no values value.", descriptor);
                    return knownObjects;
                }
                Map<String, Object> values = (Map<String, Object>)  valuesObj;

                Object appMetadataObj = values.get("appMetadata");
                if (appMetadataObj == null) {
                    logger.debug("[x] Cannot retrieve sobject types from descriptor {}: no appMetadata value.", descriptor);
                    return knownObjects;
                }
                Map<String, Object> appMetadata = (Map<String, Object>)  appMetadataObj;

                Object supportedEntitiesObj = appMetadata.get("supportedEntities");
                if (supportedEntitiesObj == null) {
                    logger.debug("[x] Cannot retrieve sobject types from descriptor {}: no supportedEntities value.", descriptor);
                    return knownObjects;
                }

                List<String> supportedEntities = (List<String>) supportedEntitiesObj;

                if (supportedEntities.isEmpty()) {
                    return knownObjects;
                }

                logger.debug("[x] Found {} object types from Salesforce Aura API (with descriptor {}).", supportedEntities.size(), descriptor);
                for (String supportedEntity : supportedEntities) {
                    SalesforceSObjectPojo sObject = SObjectUtils.buildEmptyObjectFromType(supportedEntity);
                    knownObjects.add(sObject);
                }
            }
        }

        logger.info("[*] Found {} object types from Salesforce Aura service!", knownObjects.size());
        return knownObjects;
    }

    private Set<SalesforceSObjectPojo> getCurrentObjectsTypeFromREST() throws SalesforceAuraClientBadRequestException, SalesforceAuraUnauthenticatedException {

        Set<SalesforceSObjectPojo> knownObjects = new HashSet<>();

        logger.info("[*] Will retrieve all sObjects types known by the target from REST sObject API.");

        // Request
        SalesforceRESTSObjectsListHttpResponseBodyPojo response = this.sendRESTGetSObjectsList();

        // Process result
        if (response != null) {
            SalesforceRESTSObjectsHttpResponseBodySobjectsItemPojo[] sObjects = response.getSobjects();
            if (sObjects == null || sObjects.length == 0) {
                logger.debug("[x] Cannot retrieve sobject types from REST sObjects API: empty results.");
                return knownObjects;
            }

            logger.debug("[x] Found {} object types from Salesforce sObject REST API.", sObjects.length);
            for (SalesforceRESTSObjectsHttpResponseBodySobjectsItemPojo supportedEntity : sObjects) {
                SalesforceSObjectPojo sObject = SObjectUtils.buildEmptyObjectFromType(supportedEntity.getName());
                knownObjects.add(sObject);
            }

            logger.info("[*] Found {} object types from Salesforce REST sObject API!", knownObjects.size());
        }

        return knownObjects;
    }

    @SuppressWarnings("unchecked")
    private Set<String> getObjectFieldNames(final String sObjectType) throws SalesforceAuraClientBadRequestException, SalesforceAuraUnauthenticatedException, SalesforceAuraInvalidParameters {

        Set<String> fields = new TreeSet<>();
        if (StringUtils.isBlank(sObjectType)) {
            logger.error("[!] No object type for fields search!");
            return fields;
        }

        // Look in cache first
        if (this.objectFieldCaches.containsKey(sObjectType)) {
            logger.trace("[xx] Will use field cache for object type {}", sObjectType);
            return this.objectFieldCaches.get(sObjectType).getFields();
        } else {
            this.objectFieldCaches.put(sObjectType, new ObjectFieldCache());
        }

        // Aura service way
        SalesforceAuraMessagePojo[] messages = AuraConfiguration.getActionMessageFor("getrecordinfo");
        for (SalesforceAuraMessagePojo message : messages) {

            String descriptor = message.getDescriptor();

            logger.debug("[x] Will look for fields for object type {} through Aura descriptor {}", sObjectType, descriptor);

            if (!this.isObjectSupportedBy(descriptor, sObjectType)) {
                break;
            }

            message.setParamsValue("objectApiName", sObjectType);

            // Request
            SalesforceAuraHttpRequestBodyPojo requestBodyPojo =
                    new SalesforceAuraHttpRequestBodyPojo(
                            descriptor,
                            message.getParams(),
                            this.credentials);

            SalesforceAuraHttpResponseBodyPojo salesforceAuraHttpResponseBody;
            try {
                salesforceAuraHttpResponseBody = this.sendAura(requestBodyPojo);
            } catch (SalesforceAuraClientBadRequestException e) {
                logger.error("[!] Invalid request.", e);
                throw e;
            } catch (SalesforceAuraInvalidParameters e) {
                logger.error("[!] Invalid request parameters.", e);
                throw e;
            }

            // Process result
            SalesforceAuraHttpResponseBodyActionsPojo[] actionsResults = salesforceAuraHttpResponseBody.getActions();
            if (actionsResults == null || actionsResults.length == 0) {
                logger.debug("[x] Cannot find records fields for type {} with descriptor {}: empty results (bad action ?).", sObjectType, descriptor);
                break;
            }

            // "Maybe" an error happened
            if (!SalesforceAuraHttpResponseBodyActionsStateEnum.SUCCESS.equals(actionsResults[0].getState())) {
                // Unsupported object type
                String unsupported = AuraHttpUtils.checkUnsupportedRecordResults(salesforceAuraHttpResponseBody.getRawBody());
                if (StringUtils.isNotBlank(unsupported)) {
                    this.updateUnsupportedObject(descriptor, unsupported);
                    break;
                }
            }

            Map<String, Object> returnValue = actionsResults[0].getReturnValue();

            if (returnValue == null || returnValue.isEmpty()) {
                logger.error("[!] Cannot find fields for object type {} through descriptor {}.", sObjectType, descriptor);
                break;
            }

            Map<String, Object> returnValueFields = (Map<String, Object>) returnValue.get("fields");
            Set<String> knownFields = returnValueFields.keySet().stream().map(f -> sObjectType + "." + f).collect(Collectors.toSet());

            // Update fields cache
            if (!knownFields.isEmpty()) {
                logger.info("[*] Found {} fields for sObject type {} from Aura service.", knownFields.size(), sObjectType);
                ObjectFieldCache cache = objectFieldCaches.get(sObjectType);
                cache.addFields(knownFields);
                objectFieldCaches.put(sObjectType, cache);
            }
        }

        // SObjects REST API way
        logger.debug("[x] Will look for fields for object type {} through SObjects REST API.", sObjectType);
        // Request
        Set<String> knownFields = this.sendRESTGetSObjectDescribeFields(sObjectType);
        if (knownFields != null && !knownFields.isEmpty()) {
            logger.info("[*] Found {} fields for sObject type {} from REST sObject API.", knownFields.size(), sObjectType);
            ObjectFieldCache cache = objectFieldCaches.get(sObjectType);
            cache.addFields(knownFields);
        }

        return objectFieldCaches.get(sObjectType).getFields();
    }

    @SuppressWarnings({"unchecked","rawtypes"})
    private void getObjectWithFields(Map<SalesforceItemKeyPojo, SalesforceSObjectPojo> items, final String recordId, final String sObjectType, final Set<String> fields) throws SalesforceAuraMissingRecordIdException, SalesforceAuraClientBadRequestException, SalesforceAuraUnauthenticatedException, SalesforceAuraInvalidParameters {

        if (StringUtils.isBlank(recordId)) {
            logger.error("[!] Cannot select record with fields: no recordId specified.");
            throw new SalesforceAuraMissingRecordIdException();
        }

        SalesforceAuraMessagePojo[] messages = AuraConfiguration.getActionMessageFor("getrecordfields");
        // One method exists
        SalesforceAuraMessagePojo message = messages[0];

        String descriptor = message.getDescriptor();
        SalesforceItemKeyPojo key = new SalesforceItemKeyPojo(descriptor, recordId, sObjectType);
        if (items.containsKey(key)) {
            logger.trace("[xx] Already lookup for recordId {} with descriptor {}. Skip.", recordId, descriptor);
            return;
        }
        // Check support
        if (!this.isObjectSupportedBy(descriptor, sObjectType)) {
            return;
        }
        logger.debug("[x] Will use descriptor {}", descriptor);

        // Set recordId
        message.setParamsValue("recordId", recordId);

        // Set objectApiName
        message.setParamsValue("objectApiName", sObjectType);

        // Set fields
        Set<String> computedFields = new TreeSet<>();

        // No fields specified: find them
        // TODO: this part needs more tests
        if (fields == null) {

            // No known fields for this object: find & compute them
            if (!objectFieldCaches.containsKey(sObjectType)) {

                // Find from Salesforce APIs
                this.getObjectFieldNames(sObjectType);
            }

            // Generate from introspection
            if (this.introspectionEnabledForTypes) {
                SalesforceSObjectPojo sObject = SalesforceSObjectsConfiguration.getSObject(sObjectType);
                if (sObject == null) {
                    // Ex: CustomType__c.Id
                    String defaultField = sObjectType + ".Id";
                    logger.debug("[x] Will use default field {}: sObject not found (custom ?).", defaultField);
                    computedFields.add(defaultField);
                } else {
                    for (SalesforceSObjectFieldPojo f : sObject.getFields()) {

                        if (f.getValue() instanceof Object[]) {
                            logger.trace("[xx] Skipping field {}: cannot get array field.", f.getName());
                            continue;
                        }

                        // Ex: User.FirstName
                        String field = sObjectType + "." + f.getName();

                        // SObject relation (masked by SalesforceSObjectPojo): use "Id" - Ex: User.Profile.Id
                        if (f.getValue() instanceof SalesforceSObjectPojo) {
                            field = field + ".Id";
                        }

                        computedFields.add(field);
                    }
                }
            }

            // Get field cache
            computedFields.addAll(objectFieldCaches.get(sObjectType).getFields());

            // Request for specific fields
        } else {
            // Empty fields list: problem on selecting fields
            if (fields.isEmpty()) {
                logger.error("[!] The record {} cannot be read: fields are unreadable.", recordId);
            // Precise selection of fields
            } else {
                computedFields = fields;
            }
        }
        logger.debug("[x] Will use {} selected fields.", computedFields.size());
        logger.trace("[x] Will use selected fields: {}", computedFields);
        message.setParamsValue("fields", computedFields);

        // Request
        SalesforceAuraHttpRequestBodyPojo requestBodyPojo =
                new SalesforceAuraHttpRequestBodyPojo(
                        message.getDescriptor(),
                        message.getParams(),
                        this.credentials);

        SalesforceAuraHttpResponseBodyPojo salesforceAuraHttpResponseBody;
        try {
            salesforceAuraHttpResponseBody = this.sendAura(requestBodyPojo);
        } catch (SalesforceAuraClientBadRequestException e) {
            logger.error("[!] Invalid request.", e);
            throw e;
        } catch (SalesforceAuraInvalidParameters e) {
            logger.error("[!] Invalid request parameters.", e);
            throw e;
        }

        // Process result
        SalesforceAuraHttpResponseBodyActionsPojo[] actionsResults = salesforceAuraHttpResponseBody.getActions();
        if (actionsResults == null || actionsResults.length == 0) {
            logger.debug("[x] Cannot try find object with recordId {} and descriptor {}: empty results (bad action ?).", recordId, descriptor);
            return;
        }

        // Check if unknown column has been requested or unsupported record type for the descriptor
        if (!SalesforceAuraHttpResponseBodyActionsStateEnum.SUCCESS.equals(actionsResults[0].getState())) {

            // Unsupported object type
            if (StringUtils.isNotBlank(sObjectType)) {
                String unsupported = AuraHttpUtils.checkUnsupportedRecordResults(salesforceAuraHttpResponseBody.getRawBody());
                if (StringUtils.isNotBlank(unsupported)) {
                    this.updateUnsupportedObject(descriptor, unsupported);
                    return;
                }
            }

            // Unsupported field
            if (message.getParams().containsKey("fields")) {
                String unknownColumn = AuraHttpUtils.checkUnknownColumnFromResults(salesforceAuraHttpResponseBody.getRawBody());
                if (StringUtils.isNotBlank(unknownColumn)) {
                    // Retry without this field
                    Set<String> newFields = ((Set<String>)message.getParams().get("fields")).stream()
                            .filter(f -> !f.contains(sObjectType + "." + unknownColumn))
                            .collect(Collectors.toSet());
                    objectFieldCaches.get(sObjectType).setFields(newFields);
                    if (newFields.isEmpty()) {
                        logger.error("[!] No fields to request after filtering: the record {} will not be read from descriptor {}", recordId, descriptor);
                    } else {
                        this.getObjectWithFields(items, recordId, sObjectType, newFields);
                    }
                    return;
                }
            }
        }

        // Results can be found "actions" part for record with fields

        Map<String, Object> returnValue = actionsResults[0].getReturnValue();

        logger.debug("[x] Found record {} with fields through descriptor {}!", recordId, descriptor);

        if (returnValue != null) {
            logger.debug("[x] Record found: {}", returnValue);
            if (fields != null
                    && !fields.isEmpty()) {
                logger.debug("[x] Saving fields for object type {}: {}", sObjectType, fields);

                // Update fields cache if necessary
                if (!objectFieldCaches.containsKey(sObjectType)) {
                    ObjectFieldCache cache = new ObjectFieldCache();
                    objectFieldCaches.put(sObjectType, cache);
                }
                ObjectFieldCache cache = objectFieldCaches.get(sObjectType);
                cache.addFields(fields);
            }
            items.put(key, SObjectUtils.createSObject(returnValue));
        } else {
            logger.warn("[!] Cannot find record with fields for ID {} and type {}.", recordId, sObjectType);
        }
    }

    private void updateUnsupportedObject(final String descriptor, final String recordType) {
        if (StringUtils.isNotBlank(recordType)) {
            List<String> unsupportedObjects = unsupportedDescriptorObjects.get(descriptor);
            if (unsupportedObjects == null) {
                unsupportedObjects = new ArrayList<>();
            }
            logger.debug("[x] Mark the object type {} as 'not supported' by descriptor {}", recordType, descriptor);
            unsupportedObjects.add(recordType);
            unsupportedDescriptorObjects.put(descriptor,unsupportedObjects);
        }
    }

    private boolean isObjectSupportedBy(final String descriptor, final String recordType) {
        List<String> unsupportedObjects = unsupportedDescriptorObjects.get(descriptor);
        if (unsupportedObjects == null) {
            return true;
        }
        if (!unsupportedObjects.contains(recordType)) {
            return true;
        }
        logger.debug("[x] The object type {} is not supported by descriptor {}", recordType, descriptor);
        return false;
    }

    private Set<String> generateRecordTypesList() throws SalesforceAuraClientBadRequestException, SalesforceAuraUnauthenticatedException {
        if (recordTypes.isEmpty()) {

            // From introspection
            if (this.introspectionEnabledForTypes) {
                logger.debug("[x] Get record types from introspection");
                Set<SalesforceSObjectPojo> sObjects = SalesforceSObjectsConfiguration.getSObjects();
                recordTypes.addAll(sObjects.stream().map(SalesforceSObjectPojo::getSObjectType).toList());
            }

            // From wordlist (in the wild)
            if (this.wordlistEnabledForTypes) {
                logger.debug("[x] Get record types from wordlist");
                recordTypes.addAll(SalesforceSObjectsConfiguration.getWildSObjectNames());
            }

            // From API
            if (this.apiEnabledForTypes) {
                logger.debug("[x] Get record types from APIs");
                recordTypes.addAll(this.getCurrentObjectsTypeFromAura().stream().map(SalesforceSObjectPojo::getSObjectType).toList());
                recordTypes.addAll(this.getCurrentObjectsTypeFromREST().stream().map(SalesforceSObjectPojo::getSObjectType).toList());
            }

            if (this.customTypesOnly) {
                recordTypes.removeIf(r -> !r.endsWith("__c"));
                logger.info("[*] Reducing to {} custom object types.", recordTypes.size());
                logger.debug("[x] Custom object types: {}", recordTypes);
            }
        }

        return recordTypes;
    }
}