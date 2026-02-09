package com.hypixel.hytale.codec;

/**
 * Stub — Codec primitives for serialization.
 */
public class Codec<T> {
    protected Codec() {}

    public static final Codec<String>  STRING  = new Codec<>();
    public static final Codec<Integer> INTEGER = new Codec<>();
    public static final Codec<Boolean> BOOL    = new Codec<>();
    public static final Codec<Double>  DOUBLE  = new Codec<>();
    public static final Codec<Float>   FLOAT   = new Codec<>();
}
