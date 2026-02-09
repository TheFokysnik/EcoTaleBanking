package com.hypixel.hytale.server.core.entity.entities.player.pages;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Stub — Interactive custom UI page (native Hytale API).
 * Real class: com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage
 */
public abstract class InteractiveCustomUIPage<D> {

    protected final PlayerRef playerRef;
    private final CustomPageLifetime lifetime;
    private final BuilderCodec<D> codec;

    protected InteractiveCustomUIPage(@Nonnull PlayerRef playerRef,
                                      @Nonnull CustomPageLifetime lifetime,
                                      @Nonnull BuilderCodec<D> codec) {
        this.playerRef = playerRef;
        this.lifetime = lifetime;
        this.codec = codec;
    }

    /**
     * Build the page: load .ui template, bind events, set initial state.
     */
    public abstract void build(@Nonnull Ref<EntityStore> ref,
                               @Nonnull UICommandBuilder cmd,
                               @Nonnull UIEventBuilder events,
                               @Nonnull Store<EntityStore> store);

    /**
     * Handle an incoming UI event from the client.
     */
    public abstract void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                         @Nonnull Store<EntityStore> store,
                                         @Nonnull D data);

    /** Close the page. */
    public void close() {}

    /** Send a UI update with no command builder. */
    public void sendUpdate() {}

    /** Send a UI update with state changes. */
    public void sendUpdate(@Nonnull UICommandBuilder cmd) {}

    public PlayerRef getPlayerRef() { return playerRef; }
    public CustomPageLifetime getLifetime() { return lifetime; }
    public BuilderCodec<D> getCodec() { return codec; }
}
