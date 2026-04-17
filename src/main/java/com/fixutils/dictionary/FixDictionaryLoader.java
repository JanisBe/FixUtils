package com.fixutils.dictionary;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class FixDictionaryLoader {
    private FixDictionaryLoader() {
        /* This utility class should not be instantiated */
    }


    public static Map<Integer, FixFieldDescriptor> load(InputStream is) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(is);
        return parseFields(document);
    }

    public static Map<Integer, FixFieldDescriptor> load(File file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(file);
        return parseFields(document);
    }

    private static Map<Integer, FixFieldDescriptor> parseFields(Document document) {
        Map<Integer, FixFieldDescriptor> fields = new HashMap<>();

        NodeList fieldNodes = document.getElementsByTagName("field");
        for (int i = 0; i < fieldNodes.getLength(); i++) {
            Node node = fieldNodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;

                // QuickFIX fields can be defined top-level inside <fields> or inside messages.
                // The main definition has 'number', 'name', 'type'.
                String numStr = element.getAttribute("number");
                String name = element.getAttribute("name");
                String type = element.getAttribute("type");

                if (numStr == null || numStr.isEmpty() || type == null || type.isEmpty()) {
                    continue; // Might be a reference inside a message/group without type/number
                }

                try {
                    int number = Integer.parseInt(numStr);
                    Map<String, String> enumValues = parseEnums(element);
                    fields.put(number, new FixFieldDescriptor(number, name, type, enumValues));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return fields;
    }

    private static Map<String, String> parseEnums(Element fieldElement) {
        Map<String, String> enumValues = new HashMap<>();
        NodeList valueNodes = fieldElement.getElementsByTagName("value");
        for (int i = 0; i < valueNodes.getLength(); i++) {
            Node node = valueNodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;
                String enumCode = element.getAttribute("enum");
                String desc = element.getAttribute("description");
                if (enumCode != null && !enumCode.isEmpty()) {
                    enumValues.put(enumCode, desc != null ? desc : "");
                }
            }
        }
        return enumValues;
    }
}
