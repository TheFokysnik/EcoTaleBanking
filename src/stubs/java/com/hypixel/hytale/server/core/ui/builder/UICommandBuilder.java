package com.hypixel.hytale.server.core.ui.builder;

import javax.annotation.Nonnull;

/**
 * Stub — UICommandBuilder for manipulating .ui page state.
 * Real class: com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
 */
public class UICommandBuilder {

    /** Public constructor for creating update builders. */
    public UICommandBuilder() {}

    /** Append a .ui template as the root page. */
    @Nonnull
    public UICommandBuilder append(@Nonnull String uiFilePath) { return this; }

    /** Append a .ui template as a child of the given container. */
    @Nonnull
    public UICommandBuilder append(@Nonnull String containerSelector, @Nonnull String uiFilePath) { return this; }

    /** Set a String property on a UI element: "#ElementId.PropertyName" */
    @Nonnull
    public UICommandBuilder set(@Nonnull String selectorDotProperty, @Nonnull String value) { return this; }

    /** Set a boolean property on a UI element. */
    @Nonnull
    public UICommandBuilder set(@Nonnull String selectorDotProperty, boolean value) { return this; }

    /** Set an int property on a UI element. */
    @Nonnull
    public UICommandBuilder set(@Nonnull String selectorDotProperty, int value) { return this; }

    /** Set a float property on a UI element. */
    @Nonnull
    public UICommandBuilder set(@Nonnull String selectorDotProperty, float value) { return this; }

    /** Clear all children from a container element. */
    @Nonnull
    public UICommandBuilder clear(@Nonnull String selector) { return this; }
}
