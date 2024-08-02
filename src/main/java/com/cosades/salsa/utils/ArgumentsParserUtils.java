package com.cosades.salsa.utils;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

public abstract class ArgumentsParserUtils {

    private static Namespace parsedArguments;

    public static String getArgument(final String[] args, final String argumentName) {

        if (parsedArguments == null) {
            ArgumentParser parser = ArgumentParsers.newFor("SALSA \uD83D\uDC83⚡ - SALesforce Scanner for Aura (and beyond)").build()
                    .defaultHelp(true)
                    .description("Enumeration of vulnerabilities and misconfiguration against Salesforce endpoint.");
            parser.addArgument("-t", "--target")
                    .type(String.class)
                    .required(true)
                    .help("Target URL");
            parser.addArgument("-u", "--username")
                    .type(String.class)
                    .help("Username (for authenticated mode)");
            parser.addArgument("-p", "--password")
                    .type(String.class)
                    .help("Password (for authenticated mode)");
            parser.addArgument("--sid")
                    .type(String.class)
                    .help("The SID cookie value (for authenticated mode - instead of username/password)");
            parser.addArgument("--token")
                    .type(String.class)
                    .help("The aura token (for authenticated mode - instead of username/password)");
            parser.addArgument("--path")
                    .type(String.class)
                    .help("Set specific base path.");
            parser.addArgument("--id")
                    .type(String.class)
                    .help("Find a specific record from its id.");
            parser.addArgument("--bruteforce")
                    .action(Arguments.storeTrue())
                    .help("Enable bruteforce of Salesforce identifiers from a specific record id (from --recordid).");
            parser.addArgument("--bruteforcesize")
                    .type(Integer.class)
                    .setDefault(10)
                    .help("Specific identifiers amount to bruteforce.");
            parser.addArgument("--types")
                    .type(String.class)
                    .help("Target record(s) only from following type(s) (should be comma-separated).");
            parser.addArgument("--update")
                    .action(Arguments.storeTrue())
                    .help("Test for record fields update permissions (WARNING: will inject data in the app!).");
            parser.addArgument("--create")
                    .action(Arguments.storeTrue())
                    .help("Test for record creation permissions (WARNING: will inject data in the app!).");
            parser.addArgument("--ua")
                    .type(String.class)
                    .help("Set specific User-Agent.");
            parser.addArgument("--proxy")
                    .type(String.class)
                    .help("Use following HTTP proxy (ex: 127.0.0.1:8080).");
            parser.addArgument("--dump")
                    .action(Arguments.storeTrue())
                    .help("Dump records as Json files.");
            parser.addArgument("--output")
                    .type(String.class)
                    .help("Output folder for dumping records as Json files.");
            parser.addArgument("--typesintrospection")
                    .action(Arguments.storeTrue())
                    .setDefault(false)
                    .help("Use record types from Salesforce package introspection.");
            parser.addArgument("--typeswordlist")
                    .action(Arguments.storeTrue())
                    .setDefault(false)
                    .help("Use record types from internal wordlist.");
            parser.addArgument("--typesapi")
                    .action(Arguments.storeTrue())
                    .setDefault(false)
                    .help("Use record types from APIs on the target.");
            parser.addArgument("--custom")
                    .action(Arguments.storeTrue())
                    .help("Only target custom record types (*__c).");
            parser.addArgument("--app")
                    .type(String.class)
                    .help("Custom AURA App Name.");
            parser.addArgument("--force")
                    .action(Arguments.storeTrue())
                    .help("Continue the scanning actions even if in case of incoherent or incorrect results.");
            parser.addArgument("--debug")
                    .action(Arguments.storeTrue())
                    .help("Increase the log level to DEBUG mode.");
            parser.addArgument("--trace")
                    .action(Arguments.storeTrue())
                    .help("Increase the log level to TRACE mode.");
            try {
                parsedArguments = parser.parseArgs(args);
            } catch (ArgumentParserException e) {
                parser.handleError(e);
                System.exit(-1);
            } catch (Exception e) {
                System.exit(-1);
            }
        }

        return parsedArguments.getString(argumentName);
    }
}
