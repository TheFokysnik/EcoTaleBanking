package com.hypixel.hytale.server.core.ui.builder;

import javax.annotation.Nonnull;

/**
 * Stub — EventData for UI event bindings.
 * Real class: com.hypixel.hytale.server.core.ui.builder.EventData
 *
 * Usage: new EventData().append("action", "withdraw").append("id", depositId)
 */
public class EventData {

    /** Public constructor for creating empty event data. */
    public EventData() {}

    /** Static factory for single key-value. */
    @Nonnull
    public static EventData of(@Nonnull String key, @Nonnull String value) {
        return new EventData();
    }

    /** Append a key-value pair (chaining). */
    @Nonnull
    public EventData append(@Nonnull String key, @Nonnull String value) {
        return this;
    }
}
