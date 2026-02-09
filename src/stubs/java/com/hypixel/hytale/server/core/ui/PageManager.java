package com.hypixel.hytale.server.core.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Stub — PageManager for opening/closing custom UI pages.
 * Real class: com.hypixel.hytale.server.core.ui.PageManager
 */
public class PageManager {

    /** Open a custom UI page for the player. */
    public void openCustomPage(@Nonnull Ref<EntityStore> ref,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull InteractiveCustomUIPage<?> page) {}

    /** Get the currently open custom page (or null). */
    public InteractiveCustomUIPage<?> getCustomPage() { return null; }
}
