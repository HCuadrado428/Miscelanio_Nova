package com.miscelanea.registry;

import com.miscelanea.Miscelanea;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

/**
 * Pestañas del creativo propias del mod, una por tema. El contenido se añade en {@code Miscelanea.creativeTabs}
 * (evento {@code BuildCreativeModeTabContentsEvent}), que es donde cada paquete suma sus objetos.
 */
public final class ModTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Miscelanea.MOD_ID);

    /** Feria y atracciones. */
    public static final RegistryObject<CreativeModeTab> FERIA = tab("feria", () -> new ItemStack(ModItems.TOMBOLA.get()));
    /** Sala recreativa: máquinas, cabinas, fichas, consola y cartuchos. */
    public static final RegistryObject<CreativeModeTab> RECREATIVAS = tab("recreativas", () -> new ItemStack(ModItems.recreativaJuego("comecocos")));
    /** Juegos de mesa. */
    public static final RegistryObject<CreativeModeTab> JUEGOS_MESA = tab("juegos_mesa", () -> new ItemStack(ModItems.PARCHIS.get()));
    /** Armas y combate. */
    public static final RegistryObject<CreativeModeTab> ARMAS = tab("armas", () -> new ItemStack(ModItems.BFG.get()));
    /** Sala de juegos / tortura. */
    public static final RegistryObject<CreativeModeTab> TORTURA = tab("tortura", () -> new ItemStack(com.miscelanea.tortura.TorturaRegistro.TRAMPA_OSO.get()));
    /** Pociones y efectos del mod. */
    public static final RegistryObject<CreativeModeTab> POCIONES = tab("pociones", () -> com.miscelanea.tortura.efectos.EfectosRegistro.pocionesFantasma().get(0));
    /** Bromas pesadas (pero inofensivas): varita uwu, cartel de "PATÉAME", duende travieso... */
    public static final RegistryObject<CreativeModeTab> BROMAS = tab("bromas", () -> new ItemStack(com.miscelanea.bromas.BromasRegistro.VARITA_UWU.get()));
    /** Varios: muebles, cocina, aspirafantasmas, bichos, tesoros... */
    public static final RegistryObject<CreativeModeTab> VARIOS = tab("varios", () -> new ItemStack(ModItems.ASPIRAFANTASMAS.get()));

    private static RegistryObject<CreativeModeTab> tab(String nombre, Supplier<ItemStack> icono) {
        return TABS.register(nombre, () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup." + Miscelanea.MOD_ID + "." + nombre))
                .icon(icono)
                .build());
    }

    private ModTabs() {}
}
