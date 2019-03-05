package com.linsage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author linsage
 * @create 2017-06-14  上午11:06
 */
public class KV<K, V> extends LinkedHashMap<K, V> {

    public <K, V> KV() {
    }

    public static <K, V> KV by(K key, V value) {
        return new KV().set(key, value);
    }

    public static <K, V> KV create() {
        return new KV();
    }

    public KV set(K key, V value) {
        super.put(key, value);
        return this;
    }

    public KV set(Map map) {
        super.putAll(map);
        return this;
    }

    public KV set(KV KV) {
        super.putAll(KV);
        return this;
    }

    public KV delete(Object key) {
        super.remove(key);
        return this;
    }

    public <T> T getAs(Object key) {
        return (T) get(key);
    }

    public String getStr(Object key) {
        return (String) get(key);
    }

    public Integer getInt(Object key) {
        return (Integer) get(key);
    }

    public Long getLong(Object key) {
        return (Long) get(key);
    }

    public Boolean getBoolean(Object key) {
        return (Boolean) get(key);
    }

    public Float getFloat(Object key) {
        return (Float) get(key);
    }

    public boolean notNull(Object key) {
        return get(key) != null;
    }

    public boolean isNull(Object key) {
        return get(key) == null;
    }

    public boolean isTrue(Object key) {
        Object value = get(key);
        return (value instanceof Boolean && ((Boolean) value == true));
    }

    public boolean isFalse(Object key) {
        Object value = get(key);
        return (value instanceof Boolean && ((Boolean) value == false));
    }

    public String toJson() {
        return new Gson().toJson(this);
    }

    public String toPrettyJson() {
        return new GsonBuilder().setPrettyPrinting().create().toJson(this);
    }

    public boolean equals(Object KV) {
        return KV instanceof KV && super.equals(KV);
    }


}
