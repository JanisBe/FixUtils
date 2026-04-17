package com.fixutils.dictionary;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class FixDictionaryLoaderTest {

    @Test
    void testLoadFromInputStream() throws Exception {
        String xml = "<fix major=\"4\" minor=\"4\">\n" +
                "  <fields>\n" +
                "    <field number=\"8\" name=\"BeginString\" type=\"STRING\"/>\n" +
                "    <field number=\"35\" name=\"MsgType\" type=\"STRING\">\n" +
                "      <value enum=\"0\" description=\"HEARTBEAT\"/>\n" +
                "      <value enum=\"D\" description=\"ORDER_SINGLE\"/>\n" +
                "    </field>\n" +
                "    <field number=\"44\" name=\"Price\" type=\"PRICE\"/>\n" +
                "  </fields>\n" +
                "</fix>";

        InputStream is = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
        Map<Integer, FixFieldDescriptor> fields = FixDictionaryLoader.load(is);

        assertEquals(3, fields.size());

        // Test BeginString
        FixFieldDescriptor beginString = fields.get(8);
        assertNotNull(beginString);
        assertEquals("BeginString", beginString.name());
        assertEquals("STRING", beginString.type());
        assertTrue(beginString.enumValues().isEmpty());

        // Test MsgType with Enums
        FixFieldDescriptor msgType = fields.get(35);
        assertNotNull(msgType);
        assertEquals("MsgType", msgType.name());
        assertEquals(2, msgType.enumValues().size());
        assertEquals("HEARTBEAT", msgType.enumValues().get("0"));
        assertEquals("ORDER_SINGLE", msgType.enumValues().get("D"));

        // Test Price
        FixFieldDescriptor price = fields.get(44);
        assertNotNull(price);
        assertEquals("Price", price.name());
        assertEquals("PRICE", price.type());
    }

    @Test
    void testLoadWithMalformedField() throws Exception {
        String xml = "<fix>\n" +
                "  <fields>\n" +
                "    <field name=\"Incomplete\"/>\n" + // missing number and type
                "    <field number=\"1\" name=\"Account\" type=\"STRING\"/>\n" +
                "  </fields>\n" +
                "</fix>";

        InputStream is = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
        Map<Integer, FixFieldDescriptor> fields = FixDictionaryLoader.load(is);

        assertEquals(1, fields.size());
        assertNotNull(fields.get(1));
    }
}
