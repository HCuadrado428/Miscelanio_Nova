package com.miscelanea.registry;

import com.miscelanea.Miscelanea;
import com.miscelanea.aspira.AspirafantasmasItem;
import com.miscelanea.aspira.Modo;
import com.miscelanea.aspira.ModoEnchantment;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEnchantments {
    public static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, Miscelanea.MOD_ID);

    /** Solo se pueden poner en la boquilla del Aspirafantasmas. */
    public static final EnchantmentCategory ASPIRAFANTASMAS =
            EnchantmentCategory.create("MISCELANEA_ASPIRAFANTASMAS", item -> item instanceof AspirafantasmasItem);

    public static final RegistryObject<Enchantment> DESTELLO = register(Modo.DESTELLO);
    public static final RegistryObject<Enchantment> DESATASCADOR = register(Modo.DESATASCADOR);
    public static final RegistryObject<Enchantment> SOPLIDO = register(Modo.SOPLIDO);
    public static final RegistryObject<Enchantment> TURBO = register(Modo.TURBO);
    public static final RegistryObject<Enchantment> ALQUIMISTA = register(Modo.ALQUIMISTA);

    // ---- Pistola (revólver de ruleta)
    public static final EnchantmentCategory REVOLVER =
            EnchantmentCategory.create("MISCELANEA_REVOLVER", item -> item instanceof com.miscelanea.armasfuego.pistola.RevolverItem);
    public static final RegistryObject<Enchantment> REVOLVER_ABANICO = ENCHANTMENTS.register("revolver_abanico",
            () -> new com.miscelanea.armasfuego.pistola.EncantamientoRevolver(Enchantment.Rarity.RARE, 1, 20, 0, ModEnchantments.REVOLVER_DESENFUNDE));
    public static final RegistryObject<Enchantment> REVOLVER_CARAMBOLA = ENCHANTMENTS.register("revolver_carambola",
            () -> new com.miscelanea.armasfuego.pistola.EncantamientoRevolver(Enchantment.Rarity.UNCOMMON, 2, 10, 12, null));
    public static final RegistryObject<Enchantment> REVOLVER_DESENFUNDE = ENCHANTMENTS.register("revolver_desenfunde",
            () -> new com.miscelanea.armasfuego.pistola.EncantamientoRevolver(Enchantment.Rarity.UNCOMMON, 1, 15, 0, ModEnchantments.REVOLVER_ABANICO));
    public static final RegistryObject<Enchantment> REVOLVER_SENALERO = ENCHANTMENTS.register("revolver_senalero",
            () -> new com.miscelanea.armasfuego.pistola.EncantamientoRevolver(Enchantment.Rarity.UNCOMMON, 1, 8, 0, null));

    private static RegistryObject<Enchantment> register(Modo modo) {
        return ENCHANTMENTS.register(modo.id(), () -> new ModoEnchantment(modo, ASPIRAFANTASMAS));
    }

    private ModEnchantments() {}
}
