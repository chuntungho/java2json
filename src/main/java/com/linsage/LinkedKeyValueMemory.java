package com.linsage;

import com.google.gson.GsonBuilder;

import java.util.LinkedHashMap;

public class LinkedKeyValueMemory<K, V> extends LinkedHashMap<K, V> {

    public LinkedKeyValueMemory set(K key, V value) {
        super.put(key, value);
        return this;
    }

    public String toPrettyJson() {
        return new GsonBuilder().setPrettyPrinting().create().toJson(this);
    }

}
