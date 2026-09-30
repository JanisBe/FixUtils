package com.fixutils.dictionary;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.InputStream;
import java.util.*;

public class FixDictionaryLoader {
    private FixDictionaryLoader() {
        /* This utility class should not be instantiated */
    }

    public static Map<Integer, FixFieldDescriptor> load(InputStream is) throws Exception {
        return loadData(is).fields();
    }

    public static Map<Integer, FixFieldDescriptor> load(File file) throws Exception {
        return loadData(file).fields();
    }

    public static FixDictionaryData loadData(InputStream is) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(is);
        return parseDictionaryData(document);
    }

    public static FixDictionaryData loadData(File file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(file);
        return parseDictionaryData(document);
    }

    private static FixDictionaryData parseDictionaryData(Document document) {
        Map<Integer, FixFieldDescriptor> fields = new HashMap<>();
        Map<String, Integer> nameToTag = new HashMap<>();
        List<Integer> allFieldTags = new ArrayList<>();

        NodeList fieldNodes = document.getElementsByTagName("field");
        for (int i = 0; i < fieldNodes.getLength(); i++) {
            Node node = fieldNodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;
                String numStr = element.getAttribute("number");
                String name = element.getAttribute("name");
                String type = element.getAttribute("type");

                if (numStr == null || numStr.isEmpty() || type == null || type.isEmpty()) {
                    continue;
                }

                try {
                    int number = Integer.parseInt(numStr);
                    Map<String, String> enumValues = parseEnums(element);
                    fields.put(number, new FixFieldDescriptor(number, name, type, enumValues));
                    if (name != null && !name.isEmpty()) {
                        nameToTag.put(name, number);
                    }
                    allFieldTags.add(number);
                } catch (NumberFormatException ignored) {
                    //ignore
                }
            }
        }

        Map<String, Element> componentElements = new HashMap<>();
        NodeList compNodes = document.getElementsByTagName("component");
        for (int i = 0; i < compNodes.getLength(); i++) {
            Node node = compNodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;
                Node parentNode = element.getParentNode();
                if (parentNode != null && "components".equals(parentNode.getNodeName())) {
                    String name = element.getAttribute("name");
                    if (!name.isEmpty()) {
                        componentElements.put(name, element);
                    }
                }
            }
        }

        List<Integer> headerTags = new ArrayList<>();
        NodeList headerNodes = document.getElementsByTagName("header");
        if (headerNodes.getLength() > 0) {
            extractTagsRecursively((Element) headerNodes.item(0), nameToTag, componentElements, new HashSet<>(), headerTags);
        }

        List<Integer> trailerTags = new ArrayList<>();
        NodeList trailerNodes = document.getElementsByTagName("trailer");
        if (trailerNodes.getLength() > 0) {
            extractTagsRecursively((Element) trailerNodes.item(0), nameToTag, componentElements, new HashSet<>(), trailerTags);
        }

        Map<String, List<Integer>> messageTags = new HashMap<>();
        NodeList msgNodes = document.getElementsByTagName("message");
        for (int i = 0; i < msgNodes.getLength(); i++) {
            Node node = msgNodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element msgElem = (Element) node;
                String msgType = msgElem.getAttribute("msgtype");
                if (!msgType.isEmpty()) {
                    List<Integer> msgTagList = new ArrayList<>();
                    extractTagsRecursively(msgElem, nameToTag, componentElements, new HashSet<>(), msgTagList);
                    messageTags.put(msgType, msgTagList);
                }
            }
        }

        return new FixDictionaryData(fields, headerTags, trailerTags, messageTags, allFieldTags);
    }

    private static void extractTagsRecursively(Element parent,
                                               Map<String, Integer> nameToTag,
                                               Map<String, Element> componentElements,
                                               Set<String> visitedComponents,
                                               List<Integer> result) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element elem = (Element) child;
            String tagName = elem.getTagName();
            if ("field".equals(tagName)) {
                String name = elem.getAttribute("name");
                Integer tag = nameToTag.get(name);
                if (tag != null && !result.contains(tag)) {
                    result.add(tag);
                }
            } else if ("group".equals(tagName)) {
                String name = elem.getAttribute("name");
                Integer tag = nameToTag.get(name);
                if (tag != null && !result.contains(tag)) {
                    result.add(tag);
                }
                extractTagsRecursively(elem, nameToTag, componentElements, visitedComponents, result);
            } else if ("component".equals(tagName)) {
                String compName = elem.getAttribute("name");
                if (!compName.isEmpty() && visitedComponents.add(compName)) {
                    Element compDef = componentElements.get(compName);
                    if (compDef != null) {
                        extractTagsRecursively(compDef, nameToTag, componentElements, visitedComponents, result);
                    }
                    visitedComponents.remove(compName);
                }
            }
        }
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
