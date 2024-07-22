package com.cosades.salsa.utils;

import com.cosades.salsa.exception.SalesforceInvalidIdException;

import java.util.HashSet;
import java.util.Set;

public class SalesforceIdGenerator {

    static final String BASE62 = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    static String encodeBase62(long num) {
        StringBuilder s = new StringBuilder();
        while (num > 0) {
            long remainder = num % 62;
            s.insert(0, BASE62.charAt((int) remainder));
            num /= 62;
        }
        return s.toString();
    }

    static long decodeBase62(String num) {
        long x = 1;
        long result = 0;
        for (int i = num.length() - 1; i >= 0; i--) {
            result += BASE62.indexOf(num.charAt(i)) * x;
            x *= 62;
        }
        return result;
    }

    static String sf15to18(String id) throws SalesforceInvalidIdException {
        if (id == null || id.isEmpty()) {
            throw new SalesforceInvalidIdException("No id given.");
        }
        if (id.length() == 18) {
            return id;
        }
        if (id.length() != 15) {
            throw new SalesforceInvalidIdException("The given id isn't 15 characters long.");
        }

        StringBuilder modifiedId = new StringBuilder(id);

        // Generate three last digits of the id
        for (int i = 0; i < 3; i++) {
            int f = 0;

            // For every 5-digit block of the given id
            for (int j = 0; j < 5; j++) {
                char c = id.charAt(i * 5 + j);

                // Check if c is an uppercase letter
                if (c >= 'A' && c <= 'Z') {
                    // Set a 1 at the character's position in the reversed segment
                    f += 1 << j;
                }
            }

            // Add the calculated character for the current block to the id
            modifiedId.append("ABCDEFGHIJKLMNOPQRSTUVWXYZ012345".charAt(f));
        }

        return modifiedId.toString();
    }

    public static Set<String> generateIds(String salesforceId, int quantity) throws SalesforceInvalidIdException {
        Set<String> ids = new HashSet<>();

        String prefix = salesforceId.substring(0, 10);
        long num = decodeBase62(salesforceId.substring(10, 15));

        int direction = quantity < 0 ? -1 : 1;

        for (int i = 0; i < Math.abs(quantity); i++) {
            long nextNum = num + (i + 1) * direction;
            ids.add(sf15to18(prefix + encodeBase62(nextNum)));
        }

        return ids;
    }
}
