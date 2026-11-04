package com.miscelanea.manual;

import com.miscelanea.ConfigServidor;
import com.miscelanea.Miscelanea;
import com.miscelanea.registry.ModTabs;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * El manual del mod: un libro ilustrado ({@link ManualItem}) que abre una guía de todo lo que trae Miscelánea, con un
 * capítulo por pestaña del creativo, los controles y los comandos. El contenido no está en el código: lo leen los
 * archivos {@code assets/<espacio>/manual/*.json} (ver {@code com.miscelanea.client.manual.ManualLibro}), así que
 * cualquier rama puede añadir entradas con un JSON propio sin tocar nada de aquí.
 * <p>
 * Registro aparte (como BromasRegistro): basta con llamar a {@link #registrar(IEventBus)} desde el constructor del mod.
 * Se regala una vez a cada jugador la primera vez que entra (configurable: {@code manual.regalo_al_entrar}).
 */
public final class ManualRegistro {
    private ManualRegistro() {}

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Miscelanea.MOD_ID);

    public static final RegistryObject<Item> MANUAL = ITEMS.register("manual_miscelanea",
            () -> new ManualItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    /** Marca en los datos persistentes del jugador (sobrevive a la muerte) de que ya se le regaló el manual. */
    private static final String CLAVE_REGALO = "MiscelaneaManualRegalado";

    public static void registrar(IEventBus modBus) {
        ITEMS.register(modBus);
        // antes que Miscelanea.creativeTabs (que comprueba qué objetos se han quedado sin pestaña)
        modBus.addListener(EventPriority.HIGH, ManualRegistro::pestanas);
        MinecraftForge.EVENT_BUS.addListener(ManualRegistro::entrar);
    }

    private static void pestanas(BuildCreativeModeTabContentsEvent e) {
        if (ModTabs.VARIOS.getKey().equals(e.getTabKey())) e.accept(MANUAL);
    }

    private static void entrar(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !ConfigServidor.manualRegalo()) return;
        CompoundTag persistente = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (persistente.getBoolean(CLAVE_REGALO)) return;
        persistente.putBoolean(CLAVE_REGALO, true);
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, persistente);
        ItemStack libro = new ItemStack(MANUAL.get());
        if (!p.getInventory().add(libro)) p.drop(libro, false);
    }
}
