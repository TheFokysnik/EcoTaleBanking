package com.hypixel.hytale.codec.builder;

import com.hypixel.hytale.codec.Codec;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Stub — BuilderCodec for UI data deserialization.
 * Real API declared parameter type is Codec (superclass), even though
 * callers pass KeyedCodec instances. JVM matches on declared type.
 */
public class BuilderCodec<T> {

    private BuilderCodec() {}

    public static <T> Builder<T> builder(Class<T> clazz, Supplier<T> constructor) {
        return new Builder<>();
    }

    public static class Builder<T> {
        public <V> Builder<T> addField(Codec<V> codec,
                                       BiConsumer<T, V> setter,
                                       Function<T, V> getter) {
            return this;
        }

        public BuilderCodec<T> build() { return new BuilderCodec<>(); }
    }
}
