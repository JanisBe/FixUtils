package com.fixutils.dictionary;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;

import java.io.File;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service(Service.Level.APP)
public final class FixDictionaryService {
    private static final Logger LOG = Logger.getInstance(FixDictionaryService.class);

    private final Map<String, Map<Integer, FixFieldDescriptor>> loadedDictionaries = new ConcurrentHashMap<>();
    
    private static final String[] BUNDLED = {
        "FIX40.xml", "FIX41.xml", "FIX42.xml", "FIX43.xml",
        "FIX44.xml", "FIX44.modified.xml",
        "FIX50.xml", "FIX50SP1.xml", "FIX50SP1.modified.xml",
        "FIX50SP2.xml", "FIX50SP2.modified.xml", "FIXT11.xml"
    };

    public FixDictionaryService() {
        // Pre-load bundled dictionaries
        for (String file : BUNDLED) {
            String name = stripExtension(file);
            try (InputStream is = getClass().getResourceAsStream("/Dictionaries/" + file)) {
                if (is != null) {
                    Map<Integer, FixFieldDescriptor> fields = FixDictionaryLoader.load(is);
                    loadedDictionaries.put(name, fields);
                } else {
                    LOG.warn("Could not find bundled dictionary: " + file);
                }
            } catch (Exception e) {
                LOG.error("Failed to load bundled dictionary: " + file, e);
            }
        }
    }

    public List<String> getDisplayNames() {
        List<String> names = new ArrayList<>(loadedDictionaries.keySet());
        names.sort(String::compareToIgnoreCase);
        return names;
    }

    public Map<Integer, FixFieldDescriptor> getDictionary(String name) {
        return loadedDictionaries.getOrDefault(name, Collections.emptyMap());
    }

    public boolean loadExternal(File file) {
        try {
            Map<Integer, FixFieldDescriptor> fields = FixDictionaryLoader.load(file);
            loadedDictionaries.put(file.getName(), fields);
            return true;
        } catch (Exception e) {
            LOG.error("Failed to load external dictionary: " + file.getAbsolutePath(), e);
            return false;
        }
    }

    private String stripExtension(String filename) {
        if (filename.endsWith(".xml")) {
            return filename.substring(0, filename.length() - 4);
        }
        return filename;
    }
}
