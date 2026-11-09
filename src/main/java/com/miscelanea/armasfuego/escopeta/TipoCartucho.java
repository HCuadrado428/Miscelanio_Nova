package com.miscelanea.armasfuego.escopeta;

import com.miscelanea.registry.ModItems;
import net.minecraft.world.item.Item;

/**
 * Cartuchos de la escopeta de presión. {@code perdigones}: cuántos salen; {@code dano}: multiplicador del daño de cada
 * perdigón (la grava es 1); {@code alcance}: multiplicador del alcance; {@code reciclable}: si Reciclaje puede salvarlo.
 */
public enum TipoCartucho {
    GRAVA("grava", 10, 1.0F, 1.0F, true),
    INCENDIARIO("incendiario", 8, 0.6F, 0.9F, false),
    ESCARCHA("escarcha", 8, 0.4F, 0.9F, false),
    MIEL("miel", 6, 0.3F, 0.7F, false),
    SAL("sal", 12, 0.0F, 0.8F, true),
    SEMILLAS("semillas", 10, 0.0F, 0.8F, true),
    CONFETI("confeti", 16, 0.0F, 0.7F, false);

    public final String id;
    public final int perdigones;
    public final float dano, alcance;
    public final boolean reciclable;

    TipoCartucho(String id, int perdigones, float dano, float alcance, boolean reciclable) {
        this.id = id;
        this.perdigones = perdigones;
        this.dano = dano;
        this.alcance = alcance;
        this.reciclable = reciclable;
    }

    public static TipoCartucho porId(String id) {
        for (TipoCartucho t : values()) if (t.id.equals(id)) return t;
        return null;
    }

    public Item item() {
        return ModItems.escopetaCartucho(this);
    }

    public static TipoCartucho de(Item item) {
        return item instanceof CartuchoPresionItem c ? c.tipo : null;
    }
}
