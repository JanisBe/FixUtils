package com.fixutils.dictionary;

import com.fixutils.parser.TagValuePair;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FixDictionarySortingTest {

    @Test
    void testUserExampleSortingWithFix42() throws Exception {
        // User example:
        // Input: 35=8 | 49=PHLX | 8=FIX.4.2 | 9=178 |
        // Dictionary: FIX42.xml
        // Expected order: 8 (BeginString), 9 (BodyLength), 35 (MsgType), 49 (SenderCompID)

        FixDictionaryData fix42;
        try (InputStream is = getClass().getResourceAsStream("/Dictionaries/FIX42.xml")) {
            assertNotNull(is, "FIX42.xml should be on classpath");
            fix42 = FixDictionaryLoader.loadData(is);
        }

        List<TagValuePair> input = List.of(
                new TagValuePair(35, "8"),
                new TagValuePair(49, "PHLX"),
                new TagValuePair(8, "FIX.4.2"),
                new TagValuePair(9, "178")
        );

        List<TagValuePair> sorted = fix42.sort(input);

        assertEquals(4, sorted.size());
        assertEquals(8, sorted.get(0).tag());
        assertEquals("FIX.4.2", sorted.get(0).value());

        assertEquals(9, sorted.get(1).tag());
        assertEquals("178", sorted.get(1).value());

        assertEquals(35, sorted.get(2).tag());
        assertEquals("8", sorted.get(2).value());

        assertEquals(49, sorted.get(3).tag());
        assertEquals("PHLX", sorted.get(3).value());
    }

    @Test
    void testSortingWithHeaderBodyAndTrailer() throws Exception {
        FixDictionaryData fix42;
        try (InputStream is = getClass().getResourceAsStream("/Dictionaries/FIX42.xml")) {
            assertNotNull(is);
            fix42 = FixDictionaryLoader.loadData(is);
        }

        // Tags:
        // 10: CheckSum (trailer)
        // 37: OrderID (ExecutionReport body)
        // 35: MsgType (header)
        // 49: SenderCompID (header)
        // 9: BodyLength (header)
        // 8: BeginString (header)
        // 56: TargetCompID (header)
        // 11: ClOrdID (ExecutionReport body, comes after OrderID 37 in FIX42)
        List<TagValuePair> input = List.of(
                new TagValuePair(10, "042"),
                new TagValuePair(37, "ORD123"),
                new TagValuePair(35, "8"),
                new TagValuePair(56, "TARGET"),
                new TagValuePair(11, "CLORD1"),
                new TagValuePair(49, "PHLX"),
                new TagValuePair(9, "178"),
                new TagValuePair(8, "FIX.4.2")
        );

        List<TagValuePair> sorted = fix42.sort(input);

        List<Integer> sortedTags = sorted.stream().map(TagValuePair::tag).toList();
        // In FIX42:
        // Header: BeginString (8), BodyLength (9), MsgType (35), SenderCompID (49), TargetCompID (56)
        // Body (MsgType 8 ExecutionReport): OrderID (37), ClOrdID (11)
        // Trailer: CheckSum (10)
        assertEquals(List.of(8, 9, 35, 49, 56, 37, 11, 10), sortedTags);
    }
}
