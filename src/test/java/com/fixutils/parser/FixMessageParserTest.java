package com.fixutils.parser;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FixMessageParserTest {

    @Test
    void testParseWithPipeSeparator() {
        String message = "8=FIX.4.4|9=148|35=D|10=092|";
        List<TagValuePair> pairs = FixMessageParser.parse(message, "|");

        assertEquals(4, pairs.size());
        assertEquals(8, pairs.get(0).tag());
        assertEquals("FIX.4.4", pairs.get(0).value());
        assertEquals(9, pairs.get(1).tag());
        assertEquals("148", pairs.get(1).value());
        assertEquals(35, pairs.get(2).tag());
        assertEquals("D", pairs.get(2).value());
        assertEquals(10, pairs.get(3).tag());
        assertEquals("092", pairs.get(3).value());
    }

    @Test
    void testParseWithSohSeparator() {
        String message = "8=FIX.4.2\u00019=88\u000135=0\u000110=123\u0001";
        List<TagValuePair> pairs = FixMessageParser.parse(message, "\u0001");

        assertEquals(4, pairs.size());
        assertEquals(8, pairs.get(0).tag());
        assertEquals("FIX.4.2", pairs.get(0).value());
    }

    @Test
    void testParseWithCaretSeparator() {
        String message = "8=FIX.4.1^9=12^35=A^10=000";
        List<TagValuePair> pairs = FixMessageParser.parse(message, "^");

        assertEquals(4, pairs.size());
        assertEquals(10, pairs.get(3).tag());
        assertEquals("000", pairs.get(3).value());
    }

    @Test
    void testParseWithEmptyInput() {
        assertTrue(FixMessageParser.parse("", "|").isEmpty());
        assertTrue(FixMessageParser.parse(null, "|").isEmpty());
    }

    @Test
    void testParseWithMalformedPairs() {
        // Tag without value, missing '=', and non-numeric tag
        String message = "8=FIX.4.4|9|ABC=123|35=D|10=092";
        List<TagValuePair> pairs = FixMessageParser.parse(message, "|");

        // Should skip '9' (no '=') and 'ABC=123' (non-numeric tag)
        assertEquals(3, pairs.size());
        assertEquals(8, pairs.get(0).tag());
        assertEquals(35, pairs.get(1).tag());
        assertEquals(10, pairs.get(2).tag());
    }

    @Test
    void testParseWithEmptyTokens() {
        String message = "8=FIX.4.4|||35=D||10=092|";
        List<TagValuePair> pairs = FixMessageParser.parse(message, "|");

        assertEquals(3, pairs.size());
        assertEquals(8, pairs.get(0).tag());
        assertEquals(35, pairs.get(1).tag());
        assertEquals(10, pairs.get(2).tag());
    }
}
