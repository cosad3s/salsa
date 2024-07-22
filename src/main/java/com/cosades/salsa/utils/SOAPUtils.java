package com.cosades.salsa.utils;

import com.cosades.salsa.exception.SalesforceSOAPParsingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class SOAPUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(SOAPUtils.class);

    public static boolean checkUnsupportedEntity(final String body) {
        final String regex = ".*INVALID_TYPE.*";
        final Pattern regexPattern = Pattern.compile(regex);
        Matcher matcher = regexPattern.matcher(body);
        if (matcher.find()) {
            LOGGER.trace("[x] The target object type is not supported with SOAP.");
            return true;
        }
        return false;
    }

    public static List<String> parseSOAPRecords(final String body) throws SalesforceSOAPParsingException {

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        Document document = null;

        try {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            StringReader stringReader = new StringReader(body);
            document = builder.parse(new InputSource(stringReader));
        } catch (ParserConfigurationException | IOException | SAXException e) {
            throw new SalesforceSOAPParsingException(e);
        }

        Node response = document.getDocumentElement().getLastChild().getFirstChild().getFirstChild();
        NodeList l = response.getChildNodes();

        List<String> ids = new ArrayList<>();

        // Skip the 3 first tags as there are not related to records content
        if (l.getLength() > 3) {
            LOGGER.trace("[xx] Found {} records and maybe more through SOAP API (will not look for more with this method).", l.getLength() - 3);

            for (int i = 2 ; i < l.getLength() - 1 ; i ++) {
                String id = l.item(i).getTextContent();
                ids.add(id);
            }
        } else {
            LOGGER.trace("[xx] No records found after SOAP parsing.");
        }

        return ids;
    }
}
