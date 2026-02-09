package com.hypixel.hytale.codec;

/**
 * Stub — KeyedCodec for serialization/deserialization.
 * Extends Codec to match real Hytale API where Builder.append() takes Codec<V>.
 */
public class KeyedCodec<V> extends Codec<V> {

    private final String key;

    public KeyedCodec(String key, Codec<V> valueCodec) {
        this.key = key;
    }

    public String getKey() { return key; }
}
