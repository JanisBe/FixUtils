package com.fixutils.validation;

import java.util.regex.Pattern;

public class FixTypeValidator {

    private static final Pattern INT_PATTERN = Pattern.compile("^[+-]?\\d+$");
    private static final Pattern FLOAT_PATTERN = Pattern.compile("^[+-]?\\d+(\\.\\d+)?$");
    private static final Pattern UTC_TIMESTAMP_PATTERN = Pattern.compile("^\\d{8}-\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?$");
    private static final Pattern UTC_DATE_PATTERN = Pattern.compile("^\\d{8}$");
    private static final Pattern UTC_TIME_PATTERN = Pattern.compile("^\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?$");
    private static final Pattern CURRENCY_PATTERN = Pattern.compile("^[A-Z]{3}$");
    private static final Pattern MONTH_YEAR_PATTERN = Pattern.compile("^\\d{6}(\\d{2}|w\\d)?$");
    private static final Pattern DAY_OF_MONTH_PATTERN = Pattern.compile("^([1-9]|[12]\\d|3[01])$");

    private FixTypeValidator() {
        // Utility class
    }

    /**
     * Checks if a field's value matches its defined FIX type.
     * Empty values are considered valid as they are usually transient during editing.
     *
     * @final
     */
    public static boolean isValid(String value, String type) {
        if (value == null || value.trim().isEmpty()) {
            return true;
        }
        if (type == null) {
            return true;
        }

        String normalizedType = type.toUpperCase();
        return switch (normalizedType) {
            case "INT", "LENGTH", "SEQNUM", "NUMINGROUP" -> INT_PATTERN.matcher(value).matches();
            case "FLOAT", "PRICE", "QTY", "AMT", "PERCENTAGE", "PRICEOFFSET" -> FLOAT_PATTERN.matcher(value).matches();
            case "CHAR" -> value.length() == 1;
            case "BOOLEAN" -> "Y".equals(value) || "N".equals(value);
            case "UTCTIMESTAMP" -> UTC_TIMESTAMP_PATTERN.matcher(value).matches();
            case "UTCDATEONLY", "LOCALMKTDATE", "UTCDATE" -> UTC_DATE_PATTERN.matcher(value).matches();
            case "UTCTIMEONLY" -> UTC_TIME_PATTERN.matcher(value).matches();
            case "CURRENCY" -> CURRENCY_PATTERN.matcher(value).matches();
            case "MONTHYEAR" -> MONTH_YEAR_PATTERN.matcher(value).matches();
            case "DAYOFMONTH" -> DAY_OF_MONTH_PATTERN.matcher(value).matches();
            default -> true; // Default to true for STRING, DATA, and other types
        };
    }

    /**
     * Returns a helpful description of the expected format for a given FIX type.
     */
    public static String getExpectedFormatHelp(String type) {
        if (type == null) {
            return "";
        }
        return switch (type.toUpperCase()) {
            case "INT", "LENGTH", "SEQNUM", "NUMINGROUP" -> "Integer number (e.g. 123)";
            case "FLOAT", "PRICE", "QTY", "AMT", "PERCENTAGE", "PRICEOFFSET" -> "Decimal number (e.g. 12.34 or -1.5)";
            case "CHAR" -> "Single character (e.g. A)";
            case "BOOLEAN" -> "Y (Yes) or N (No)";
            case "UTCTIMESTAMP" -> "UTC Timestamp in format YYYYMMDD-HH:MM:SS (e.g. 20260524-20:00:00)";
            case "UTCDATEONLY", "LOCALMKTDATE", "UTCDATE" -> "Date in format YYYYMMDD (e.g. 20260524)";
            case "UTCTIMEONLY" -> "Time in format HH:MM:SS (e.g. 20:00:00)";
            case "CURRENCY" -> "3-letter currency code (e.g. USD)";
            case "MONTHYEAR" -> "Month and Year in format YYYYMM (e.g. 202605)";
            case "DAYOFMONTH" -> "Day of month as an integer between 1 and 31";
            default -> "";
        };
    }
}
