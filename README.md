# SALSA - *SALesforce Scanner for Aura (and beyond)*

<p align="center">
<img src="./assets/logo.jpeg" width="150">

**SALSA** has been developped on a lot of my personal free time, to help me on pentesting and bug hunting activites against Salesforce Lightning (Aura) and API assets. Please note it is fully experimental.

I decided to share it for free, to help the community.  
*If you would ever like to buy me a coffee or a beer* 😇 :

[!["Buy Me A Coffee"](https://www.buymeacoffee.com/assets/img/custom_images/orange_img.png)](https://www.buymeacoffee.com/cosades)  

</p>

## Features

- Enumeration and/or dump data records (*and sub-records*) from:
  - Aura controllers
  - Services API (Direct sObjects `/services/data/v60.0/sobjects` or SOQL `/services/data/v60.0/query/`)
  - SOAP (`/services/Soap/c/`)
- Works as unauthenticated or authenticated user (*username / password or `sid` or `aura.token`*).
- Enumeration records entities types (with or without custom entities `*__c` filtering) from:  
  - Target APIs harvesting
  - And/or Salesforce packages reflections
  - And/or encountered entities in the wild
- Test for targetted record identifier.
- Bruteforcing record identifiers.
- ⚠️ Automatized test for arbitrary records creation.
- ⚠️ Automatized test for arbitrary records fields edition.
- *And more: routing to HTTP proxy for investigation, custom User-Agent, automatized finding of entities fields, auto-detect FWUID, etc.*

⚠️: *dangerous & experimental*

## Usage

### Help

```bash
usage: SALSA 💃⚡ - SALesforce Scanner for Aura (and beyond)
       [-h] -t TARGET [-u USERNAME] [-p PASSWORD] [--sid SID] [--token TOKEN] [--path PATH] [--id ID] [--bruteforce] [--types TYPES] [--update] [--create] [--ua UA] [--proxy PROXY] [--dump]
       [--output OUTPUT] [--typesintrospection] [--typeswordlist] [--typesapi] [--custom] [--app APP] [--force] [--debug] [--trace]

Enumeration of vulnerabilities and misconfiguration against Salesforce endpoint.

named arguments:
  -h, --help             show this help message and exit
  -t TARGET, --target TARGET
                         Target URL
  -u USERNAME, --username USERNAME
                         Username (for authenticated mode)
  -p PASSWORD, --password PASSWORD
                         Password (for authenticated mode)
  --sid SID              The SID cookie value (for authenticated mode - instead of username/password)
  --token TOKEN          The aura token (for authenticated mode - instead of username/password)
  --path PATH            Set specific base path.
  --id ID                Find a specific record from its id.
  --bruteforce           Enable bruteforce of Salesforce identifiers from a specific record id (from --recordid). (default: false)
  --types TYPES          Target record(s) only from following type(s) (should be comma-separated).
  --update               Test for record fields update permissions (WARNING: will inject data in the app!). (default: false)
  --create               Test for record creation permissions (WARNING: will inject data in the app!). (default: false)
  --ua UA                Set specific User-Agent.
  --proxy PROXY          Use following HTTP proxy (ex: 127.0.0.1:8080).
  --dump                 Dump records as Json files. (default: false)
  --output OUTPUT        Output folder for dumping records as Json files.
  --typesintrospection   Use record types from Salesforce package introspection. (default: false)
  --typeswordlist        Use record types from internal wordlist. (default: false)
  --typesapi             Use record types from APIs on the target. (default: false)
  --custom               Only target custom record types (*__c). (default: false)
  --app APP              Custom AURA App Name.
  --force                Continue the scanning actions even if in case of incoherent or incorrect results. (default: false)
  --debug                Increase the log level to DEBUG mode. (default: false)
  --trace                Increase the log level to TRACE mode. (default: false)
```

### Examples

<details>
    <summary>Simple scan - Unauthenticated</summary>

```bash
java -jar target/salsa-jar-with-dependencies.jar -t https://www.target.com --typesapi

[*] Searching for Salesforce Aura instance on https://www.target.com ...
[!] Found Salesforce Aura instance on path: /aura
[!] Scan will continue as unauthenticated (guest) user ...
[*] Looking for all objects with standard or custom types.
[*] Will retrieve all sObjects types known by the target from Aura service.
[*] Found 2111 object types from Salesforce Aura service!
[*] Will retrieve all sObjects types known by the target from REST sObject API.
[*] Aura: looking for records for type AINaturalLangProcessRslt
[*] Aura: looking for records for type AINtrlLangProcChunkRslt
[*] Aura: looking for records for type AIPredictionScore
(...)
```

</details>

<details>
    <summary>Simple scan - Unauthenticated - Custom types only</summary>

```bash
❯ java -jar target/salsa-jar-with-dependencies.jar -t https://www.target.com --typesapi --custom

[*] Searching for Salesforce Aura instance on https://www.target.com ...
[!] Found Salesforce Aura instance on path: /aura
[!] Scan will continue as unauthenticated (guest) user ...
[*] Looking for all objects with standard or custom types.
[*] Will retrieve all sObjects types known by the target from Aura service.
[*] Found 2111 object types from Salesforce Aura service!
[*] Will retrieve all sObjects types known by the target from REST sObject API.
[*] Reducing to 4 custom object types.
[*] Aura: looking for records for type CountryLanguage__c
[*] Looking for sObject with recordId 00B0H000007t1qlUAA and type(s) [ListView].
[!] The recordId 00B0H000007t1qlUAA cannot be found through descriptor serviceComponent://ui.force.components.controllers.detail.DetailController/ACTION$getRecord (error: We couldn't find the record you're trying to access. It may have been deleted by another user, or there may have been a system error. Ask your administrator for help.).
[!] No records found from recordId 00B0H000007t1qlUAA and descriptor serviceComponent://ui.force.components.controllers.recordGlobalValueProvider.RecordGvpController/ACTION$getRecord: {objectMetadata={ListView={_nameField=Name, _entityLabel=List View, _keyPrefix=00B}}, quickActionRecordTemplates={}, recordErrors={00B0H000007t1qlUAA={message=We couldn't find the record you're trying to access. It may have been deleted by another user, or there may have been a system error. Ask your administrator for help.}}, records={}, recordTemplates={}, resolvedDraftIds=[], quickActionMetadata={}, refreshErrors=[], requestIds={00B0H000007t1qlUAA=[00B0H000007t1qlUAA.null.null.null.Id.VIEW]}, purgedRecordIds=[], layouts={}}
[*] Aura: looking for records for type Country__c
[*] Looking for sObject with recordId 00B0H000007t1qgUAA and type(s) [ListView].
(...)
```

</details>

<details>
    <summary>Simple scan - Unauthenticated - Targetted record type and bruteforce</summary>

```bash
❯ java -jar target/salsa-jar-with-dependencies.jar -t https://www.target.com --types Store__History --id 0176S0001GvGwvEQQS --bruteforce
Picked up _JAVA_OPTIONS: -Dawt.useSystemAAFontSettings=on -Dswing.aatext=true
[*] Searching for Salesforce Aura instance on https://www.target.com ...
[!] Found Salesforce Aura instance on path: /aura
[!] Scan will continue as unauthenticated (guest) user ...
[*] Looking for sObject with recordId 0176S0001GvGwvMQQS and type(s) [Store__History].
[!] Cannot find fields for object type Store__History through descriptor aura://RecordUiController/ACTION$getObjectInfo.
[!] Cannot find record with fields for ID 0176S0001GvGwvMQQS and type Store__History.
[!] The recordId 0176S0001GvGwvMQQS cannot be found through descriptor serviceComponent://ui.force.components.controllers.detail.DetailController/ACTION$getRecord (error: You don't have access to this record. Ask your administrator for help or to request access.).
[!] No records found from recordId 0176S0001GvGwvMQQS and descriptor serviceComponent://ui.force.components.controllers.recordGlobalValueProvider.RecordGvpController/ACTION$getRecord: {objectMetadata={}, quickActionRecordTemplates={}, recordErrors={0176S0001GvGwvMQQS={message=You don't have access to this record. Ask your administrator for help or to request access., inaccessible=true}}, records={}, recordTemplates={}, resolvedDraftIds=[], quickActionMetadata={}, refreshErrors=[], requestIds={0176S0001GvGwvMQQS=[0176S0001GvGwvMQQS.null.null.null.Id.VIEW]}, purgedRecordIds=[], layouts={}}
[*] Looking for sObject with recordId 0176S0001GvGwvNQQS and type(s) [Store__History].
[!] Cannot find record with fields for ID 0176S0001GvGwvNQQS and type Store__History.
[!] The recordId 0176S0001GvGwvNQQS cannot be found through descriptor serviceComponent://ui.force.components.controllers.detail.DetailController/ACTION$getRecord (error: You don't have access to this record. Ask your administrator for help or to request access.).
[!] No records found from recordId 0176S0001GvGwvNQQS and descriptor serviceComponent://ui.force.components.controllers.recordGlobalValueProvider.RecordGvpController/ACTION$getRecord: {objectMetadata={}, quickActionRecordTemplates={}, recordErrors={0176S0001GvGwvNQQS={message=You don't have access to this record. Ask your administrator for help or to request access., inaccessible=true}}, records={}, recordTemplates={}, resolvedDraftIds=[], quickActionMetadata={}, refreshErrors=[], requestIds={0176S0001GvGwvNQQS=[0176S0001GvGwvNQQS.null.null.null.Id.VIEW]}, purgedRecordIds=[], layouts={}}
[*] Looking for sObject with recordId 0176S0001GvGwvLQQS and type(s) [Store__History].
[!] Cannot find record with fields for ID 0176S0001GvGwvLQQS and type Store__History.
[!] The recordId 0176S0001GvGwvLQQS cannot be found through descriptor serviceComponent://ui.force.components.controllers.detail.DetailController/ACTION$getRecord (error: You don't have access to this record. Ask your administrator for help or to request access.).
[!] No records found from recordId 0176S0001GvGwvLQQS and descriptor serviceComponent://ui.force.components.controllers.recordGlobalValueProvider.RecordGvpController/ACTION$getRecord: {objectMetadata={}, quickActionRecordTemplates={}, recordErrors={0176S0001GvGwvLQQS={message=You don't have access to this record. Ask your administrator for help or to request access., inaccessible=true}}, records={}, recordTemplates={}, resolvedDraftIds=[], quickActionMetadata={}, refreshErrors=[], requestIds={0176S0001GvGwvLQQS=[0176S0001GvGwvLQQS.null.null.null.Id.VIEW]}, purgedRecordIds=[], layouts={}}
[*] Looking for sObject with recordId 0176S0001GvGwvKQQS and type(s) [Store__History].
(...)
```

</details>

<details>
    <summary>Simple scan - Authenticated - Targetted record type</summary>

```bash
❯ java -jar target/salsa-jar-with-dependencies.jar -t https://www.target.com --types User --sid '00Di000.REDACTED' --token "eyJ2ZXIiOi.REDACTED"
Picked up _JAVA_OPTIONS: -Dawt.useSystemAAFontSettings=on -Dswing.aatext=true
[*] Searching for Salesforce Aura instance on https://www.target.com ...
[!] Found Salesforce Aura instance on path: /aura
[!] Will try with explicitly provided credentials {username=''}
[*] Looking for all objects with type(s) [User].
[*] Aura: looking for records for type User
[!] Client is out-of-sync. Will retry with new FWUID: WFIwUmVJdm.REDACTED
[*] Looking for sObject with recordId 005ixxxxx and type(s) [User].
[*] Found 190 fields for sObject type User from Aura service.
[*] Found record 005ixxxxxx with descriptor serviceComponent://ui.force.components.controllers.detail.DetailController/ACTION$getRecord!
[*] 1 object(s) retrieved with descriptor serviceComponent://ui.force.components.controllers.lists.selectableListDataProvider.SelectableListDataProviderController/ACTION$getItems from object type User!
[*] End of scanning of https://www.target.com
```

</details>

<details>
    <summary>Simple scan - Authenticated - Custom record types dump</summary>

```bash
❯ java -jar target/salsa-jar-with-dependencies.jar -t https://www.target.com --typesapi --custom --sid '00Di000.REDACTED' --token "eyJ2ZXIiOi.REDACTED" --dump --proxy 127.0.0.1:8080
Picked up _JAVA_OPTIONS: -Dawt.useSystemAAFontSettings=on -Dswing.aatext=true
[*] Searching for Salesforce Aura instance on https://www.target.com ...
[!] Found Salesforce Aura instance on path: /aura
[!] Will try with explicitly provided credentials {username=''}
[*] Looking for all objects with standard or custom types.
[*] Will retrieve all sObjects types known by the target from Aura service.
[!] Client is out-of-sync. Will retry with new FWUID: WFIwUmVJ...REDACTED
[*] Found 2111 object types from Salesforce Aura service!
[*] Will retrieve all sObjects types known by the target from REST sObject API.
[*] Found 279 object types from Salesforce REST sObject API!
[*] Reducing to 24 custom object types.
[*] Aura: looking for records for type MyOtherType__c
[*] SOAP: looking for records for type MyOtherType__c
[*] Found 0 entities of types MyOtherType__c through SOAP API!
[*] Query Data API: looking for records for type MyOtherType__c
[*] SObject Data API: looking for records for type MyOtherType__c
[*] Aura: looking for records for type Wonderful__c
[*] SOAP: looking for records for type Wonderful__c
[*] Found 0 entities of types Wonderful__c through SOAP API!
[*] Query Data API: looking for records for type Wonderful__c
[*] SObject Data API: looking for records for type Wonderful__c
[*] Aura: looking for records for type MyOtherTypeAgain__c
[*] SOAP: looking for records for type MyOtherTypeAgain__c
[*] Found 0 entities of types MyOtherTypeAgain__c through SOAP API!
[*] Query Data API: looking for records for type MyOtherTypeAgain__c
[*] SObject Data API: looking for records for type MyOtherTypeAgain__c
[*] Aura: looking for records for type MyType__c
[*] SOAP: looking for records for type MyType__c
[*] Found 10 entities of types MyType__c through SOAP API!
[*] Looking for sObject with recordId a4AREDACTED and type(s) [MyType__c].
[!] Cannot find fields for object type MyType__c through descriptor aura://RecordUiController/ACTION$getObjectInfo.
[*] Found 29 fields for sObject type MyType__c from REST sObject API.
[!] Cannot find record with fields for ID a4AREDACTED and type MyType__c.
[!] The recordId a4AREDACTED cannot be found through descriptor serviceComponent://ui.force.components.controllers.detail.DetailController/ACTION$getRecord (error: You don't have access to this record. Ask your administrator for help or to request access.).
[!] No records found from recordId a4AREDACTED and descriptor serviceComponent://ui.force.components.controllers.recordGlobalValueProvider.RecordGvpController/ACTION$getRecord: {objectMetadata={}, quickActionRecordTemplates={}, recordErrors={a4AREDACTED={message=You don't have access to this record. Ask your administrator for help or to request access., inaccessible=true}}, records={}, recordTemplates={}, resolvedDraftIds=[], quickActionMetadata={}, refreshErrors=[], requestIds={a4AREDACTED=[a4AREDACTED.null.null.null.Id.VIEW]}, purgedRecordIds=[], layouts={}}
[*] Found sObject a4AREDACTED of type MyType__c from REST sObject API: [MyType__c]{[[StartDateTime__c=2023-12-05T18:00:00.000+0000], [CreatedDate=2023-11-28T14:07:00.000+0000],....]}
[*] Looking for sObject with recordId a4A6REDACTED and type(s) [MyType__c].
[!] Cannot find record with fields for ID a4A6REDACTED and type MyType__c.
(...)
[*] Query Data API: looking for records for type TR_MyLV_Diamond__c
[*] SObject Data API: looking for records for type TR_MyLV_Diamond__c
[*] Will dump merged object a4AREDACTED to ./output2024.07.22.21.57.00/MyType__c/a4AREDACTED.json
[*] Will dump merged object a2RREDACTED to ./output2024.07.22.21.57.00/MyOtherType__c/a2RREDACTED.json
[*] Will dump merged object a0NREDACTED to ./output2024.07.22.21.57.00/MyOtherTypeAgain__c/a0NREDACTED.json
(...)
```

**Dumped records will be stored into a timestamped output folder**

</details>

## Current limitations

- SOAP `query` requests are limited to 10 items.
- Bruteforcing IDs is limited to 10 items.

## TODO

*Release date: maybe one day*

- [ ] Find & add alternatives authentications.
- [ ] Detect `debug` mode arbitrary activation ([https://www.cosades.com/posts/sf_debug_mode](https://www.cosades.com/posts/sf_debug_mode)).  
- [ ] Download item for *Document* type identifier (hit `https://ATTACHMENTS_DOMAIN/sfc/servlet.shepherd/version/download/<id>` - *URL can also be found in `Generic_DocumentDownloadPathUrl` attribute from descriptor `serviceComponent://ui.comm.runtime.components.aura.components.siteforce.controller.PubliclyCacheableComponentLoaderController/ACTION$getPageComponent`*)  
- [ ] Data API - Composite: `/services/data/vXX.0/composite/batch` (POST, with examples parameters: `{"batchRequests": [{"method": "PATCH", "url": "v38.0/sobjects/OpportunityLineItem/<ID>", "richInput": {"End_Date__c": "2017-01-19"}]}}`)  
- [ ] Data API - Anonymous APEX execution: `/services/data/vXX.0/tooling/executeAnonymous/?anonymousBody=`
- [ ] Async API - Job: `/services/async/xx.0/job` (POST and `<?xml version="1.0" encoding="UTF-8"?><jobInfo xmlns="http://www.force.com/2009/06/asyncapi/dataload"><operation>update</operation><object>OpportunityLineItem</object><contentType>CSV</contentType></jobInfo>` or `<?xml version="1.0" encoding="UTF-8"?><jobInfo xmlns="http://www.force.com/2009/06/asyncapi/dataload"><state>Closed</state></jobInfo>`). Other related endpoints: `/services/data/v60.0/jobs/query`, `/services/async/xx.0/job/JOBID`,  `/services/async/xx.0/job/JOBID/batch`, `/services/async/xx.0/job/JOBID/batch/BATCHID/result`
- [ ] Apex REST API: `/services/apexrest/SoapMessage`, `/services/apexrest/Cases`
- [ ] Find the parameters for other classic Aura controllers 🥹

## Troubleshooting

> **Disclaimer: "spaghetti code" here, due to Salesforce technical contexts discoveries, mixed between official documentations, write-ups, reverse engineering, empirical tests. Hence I could study for small new features proposals or major bug fixes, this tool is now hard to maintain.**

***Then, before opening an issue, please consider the following points:***

1. I strongly encourage you to **switch the logging level** to `DEBUG` or `TRACE` level (`--debug` / `--trace`).
2. The tool can send **thousand of requests** and **works for hours**. Two possible consequences:

- **You can be banned** by the target.
- **The authentication could have a short expiration time on your target**. *I do not know how to detect & manage that part, there is no real homogeneous behaviour for this.* I could only suggest you to reduce the record types to test.

3. I think the tool is adapted to most of Salesforce contexts, **but not all of them**.
4. Route the tool **an HTTP proxy** for further investigation (`--proxy 127.0.0.1:8080` for instance)

## Q/A

*Why is the authentication username/password does not work ?*

> Because the target is maybe not using the Aura Controller `apex://LightningLoginFormController/ACTION$login`: prefer using the `sid` (session id) or `token` (Aura token) after a manual authentication.  

*What's is the difference between `sid` and `token` ?*

> The `token` is used for authenticated Aura controller interactions. The `sid` is used to interact with other APIs. The format are not the same though: for the `token` it is more like a JWT, for the `sid` it is prefixed by the organization identifier.

*Why there are limitations regarding the amount of data dump in queries for example ?*

> Yes, it could be improved with new arguments. The initial reason was that the tool can launch thousand of requests and could last for hours (Entities count / fields cound / controllers count / services count / etc.). The limitations are present to reduce the duration. Feel free to change that.

*How do I find targets ?*

> It is up to you, but it can be done with nuclei: `nuclei -rl 10 -t "http/misconfiguration/salesforce-aura.yaml" -l subdomains.txt`

*Why the source code is so complex ? Why Java ?*

> In the beginning it was a clean set of small scripts. Discoveries after discoveries, I have added, modified, removed some parts. Without unit tests. And Salesforce contexts are very complex / customisable, targets behaviors can differ and code is adapted with some unelegant if/then/else. The last reason is that I wanted to have the most adaptable and automatized tool for this kind of assessment. I dig into complex workflows, but abandonned some steps. Why Java ? Because Salesforce APEX is very close to Java, and Salesforce have some libraries in Java which could be decompiled to be dynamically integrated into the tool. And I like Java (nobody is perfect).

## Credits and ressources

Thanks for all these ressources (tools, write-ups, docs, ...), which help me a lot:

- https://www.fishofprey.com/
- https://developer.salesforce.com/
- https://developer.salesforce.com/blogs/tech-pubs/2017/01/simplify-your-api-code-with-new-composite-resources
- https://developer.salesforce.com/docs/atlas.en-us.api_tooling.meta/api_tooling/intro_rest_resources.htm
- https://developer.salesforce.com/docs/atlas.en-us.api_tooling.meta/api_tooling/tooling_api_objects_traceflag.htm
- https://www.varonis.com/blog/abusing-salesforce-communities
- https://github.com/tedconn/lwr-mobify
- https://github.com/Ophion-Security/sret
- https://github.com/forcedotcom/aura
- https://github.com/jeffzmartin/SalesforceSQLSchemaGenerator
- https://github.com/LTiDi2000/SFMisCheck/blob/main/sf.py
- https://github.com/pingidentity/AuraIntruder/
- https://www.youtube.com/watch?v=wHqp6laTnio
- https://web.archive.org/web/20201031233746/https://www.enumerated.de/index/salesforce
- https://codefriar.wordpress.com/2014/10/30/eval-in-apex-secure-dynamic-code-evaluation-on-the-salesforce1-platform/
