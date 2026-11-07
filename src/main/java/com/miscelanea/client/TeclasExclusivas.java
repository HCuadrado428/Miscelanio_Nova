package com.miscelanea.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Teclas del mod que, mientras están en uso, tienen preferencia sobre las de otros mods que usen la misma tecla. Cada
 * una va con su momento: las de la Llave Espada solo con ella en la mano, las del Hacha Leviatán con el hacha, recargar
 * con la ballesta de repetición, la rueda de la Estática siendo Estática, empujar en la caja de agua de Saw dentro de
 * ella. Fuera de ese momento la tecla es de todos, como siempre. Las teclas de Minecraft nunca se bloquean (ni la rueda
 * del ratón, que no es una tecla): así se puede seguir cambiando de objeto, andando o saltando con el arma.
 */
public final class TeclasExclusivas {
    private TeclasExclusivas() {}

    private record Exclusiva(KeyMapping tecla, BooleanSupplier cuando) {}

    private static final List<Exclusiva> LISTA = new ArrayList<>();
    private static final Set<String> VANILLA = Set.of(KeyMapping.CATEGORY_MOVEMENT, KeyMapping.CATEGORY_MISC, KeyMapping.CATEGORY_MULTIPLAYER,
            KeyMapping.CATEGORY_GAMEPLAY, KeyMapping.CATEGORY_INVENTORY, KeyMapping.CATEGORY_INTERFACE, KeyMapping.CATEGORY_CREATIVE);

    private static boolean enMano(Item i) {
        Player p = Minecraft.getInstance().player;
        return p != null && (p.getMainHandItem().is(i) || p.getOffhandItem().is(i));
    }

    static {
        BooleanSupplier llave = () -> enMano(com.miscelanea.registry.ModItems.LLAVE_ESPADA.get());
        BooleanSupplier hacha = () -> enMano(com.miscelanea.registry.ModItems.HACHA_LEVIATAN.get());
        poner(com.miscelanea.client.combate.LlaveCliente.COMANDO, llave);
        poner(com.miscelanea.client.combate.LlaveCliente.CAMBIAR, llave);
        poner(com.miscelanea.client.combate.LlaveCliente.FIJAR, () -> llave.getAsBoolean() || hacha.getAsBoolean());
        poner(com.miscelanea.client.combate.HachaCliente.APUNTAR, hacha);
        poner(com.miscelanea.client.combate.HachaCliente.ESCUDO, hacha);
        poner(com.miscelanea.client.armas.BallestaCliente.RECARGAR, () -> enMano(com.miscelanea.registry.ModItems.BALLESTA_REPETICION.get()));
        poner(com.miscelanea.client.estatica.EstaticaClienteMod.RUEDA, () -> {
            Player p = Minecraft.getInstance().player;
            return p != null && com.miscelanea.client.estatica.EstaticaCliente.es(p.getId());
        });
        poner(com.miscelanea.client.tortura.saw.SawCliente.MANOS, () -> {
            Player p = Minecraft.getInstance().player;
            return p != null && com.miscelanea.client.tortura.saw.SawCliente.caja(p.getId()) != null;
        });
    }

    /** Para añadir más desde otros sitios del mod: esta tecla manda cuando 'cuando' sea cierto. */
    public static synchronized void poner(KeyMapping k, BooleanSupplier cuando) {
        LISTA.add(new Exclusiva(k, cuando));
    }

    /**
     * Si alguna tecla del mod en uso está en esta tecla: las que deben enterarse (esas y las de Minecraft activas en esa
     * tecla). Si no, null (y la pulsación va a todas, como siempre).
     */
    @Nullable
    public static synchronized List<KeyMapping> receptores(InputConstants.Key tecla) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || tecla == null || tecla == InputConstants.UNKNOWN) return null;
        List<KeyMapping> mias = null;
        for (Exclusiva e : LISTA) {
            KeyMapping k = e.tecla();
            if (k.isUnbound() || !k.getKey().equals(tecla)) continue;
            boolean activa;
            try {
                activa = e.cuando().getAsBoolean();
            } catch (RuntimeException ex) {
                activa = false;
            }
            if (!activa) continue;
            if (mias == null) mias = new ArrayList<>();
            mias.add(k);
        }
        if (mias == null) return null;
        for (KeyMapping k : mc.options.keyMappings)
            if (!mias.contains(k) && VANILLA.contains(k.getCategory()) && k.getKey().equals(tecla) && k.getKeyConflictContext().isActive()) mias.add(k);
        return mias;
    }
}
