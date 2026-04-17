package com.fixutils.dictionary;

import java.util.Map;

public record FixFieldDescriptor(
    int number,
    String name,
    String type,
    Map<String, String> enumValues
) {}
