package com.hypixel.hytale.server.core.ui.builder;

import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;

import javax.annotation.Nonnull;

/**
 * Stub — UIEventBuilder for binding UI events.
 * Real class: com.hypixel.hytale.server.core.ui.builder.UIEventBuilder
 *
 * Uses 3-arg addEventBinding (per rpgstats reference).
 */
public class UIEventBuilder {

    public UIEventBuilder() {}

    /**
     * Bind an event to a UI element.
     *
     * @param eventType event type (e.g. Activating, ValueChanged)
     * @param selector  CSS-like selector (e.g. "#BtnDeposit")
     * @param data      event data to send back on activation
     */
    @Nonnull
    public UIEventBuilder addEventBinding(@Nonnull CustomUIEventBindingType eventType,
                                          @Nonnull String selector,
                                          @Nonnull EventData data) {
        return this;
    }
}
