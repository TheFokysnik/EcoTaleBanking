package com.hypixel.hytale.codec;

/**
 * Stub — KeyedCodec for serialization/deserialization.
 */
public class KeyedCodec<V> {

    private final String key;

    public KeyedCodec(String key) {
        this.key = key;
    }

    public String getKey() { return key; }
}
