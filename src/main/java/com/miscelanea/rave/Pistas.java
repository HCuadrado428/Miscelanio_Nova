package com.miscelanea.rave;

import com.miscelanea.baile.CancionesBaile;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.RecordItem;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Lo que puede pinchar la mesa de DJ: las canciones de la pista de baile ({@code baile:<id>}) y los discos de música de
 * cualquier mod ({@code disco:<objeto>}).
 */
public final class Pistas {
    private Pistas() {}

    public record Pista(String id, Component titulo, ResourceLocation sonido, int ticks, float bpm) {}

    private static List<Pista> lista;

    public static List<Pista> todas() {
        if (lista != null) return lista;
        List<Pista> l = new ArrayList<>();
        for (CancionesBaile.Cancion c : CancionesBaile.lista())
            l.add(new Pista("baile:" + c.id, c.tituloClave != null ? Component.translatable(c.tituloClave) : Component.literal(c.titulo),
                    new ResourceLocation(c.sonido), Math.max(20, Math.round(c.duracion * 20)), c.bpm));
        for (Item it : ForgeRegistries.ITEMS.getValues())
            if (it instanceof RecordItem r) {
                ResourceLocation k = ForgeRegistries.ITEMS.getKey(it);
                l.add(new Pista("disco:" + k, r.getDisplayName(), r.getSound().getLocation(), r.getLengthInTicks(), 120F));
            }
        return lista = l;
    }

    @Nullable
    public static Pista de(String id) {
        if (id == null || id.isEmpty()) return null;
        for (Pista p : todas()) if (p.id().equals(id)) return p;
        return null;
    }

    public static SoundEvent evento(Pista p) {
        return SoundEvent.createVariableRangeEvent(p.sonido());
    }
}
