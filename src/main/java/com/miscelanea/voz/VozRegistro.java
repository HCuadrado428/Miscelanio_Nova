package com.miscelanea.voz;

import com.miscelanea.Miscelanea;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registro de las pociones "de voz" (solo creativo, sin recetas): helio, tartamudeo, ventilador y borrachera. Son
 * efectos normales de poción; lo que cambia la voz vive en el cliente ({@code com.miscelanea.client.voz}) y solo
 * funciona si está instalado Simple Voice Chat (sin él, el resto de cada efecto sigue funcionando).
 * <p>
 * Va aparte de los demás registros: basta con llamar a {@link #registrar(IEventBus)} desde el constructor del mod.
 */
public final class VozRegistro {
    private VozRegistro() {}

    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, Miscelanea.MOD_ID);
    public static final DeferredRegister<Potion> POTIONS = DeferredRegister.create(ForgeRegistries.POTIONS, Miscelanea.MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, Miscelanea.MOD_ID);

    /** 3 min / 8 min, como las de vanilla. */
    public static final int CORTA = 3600, LARGA = 9600;

    // ---- efectos
    public static final RegistryObject<MobEffect> HELIO = EFFECTS.register("helio",
            () -> new VozEffect(MobEffectCategory.NEUTRAL, 0xF2E35C));
    public static final RegistryObject<MobEffect> TARTAMUDEO = EFFECTS.register("tartamudeo",
            () -> new VozEffect(MobEffectCategory.HARMFUL, 0x7FA8D8));
    public static final RegistryObject<MobEffect> VENTILADOR = EFFECTS.register("ventilador",
            () -> new VozEffect(MobEffectCategory.NEUTRAL, 0x9ED6E8));
    public static final RegistryObject<MobEffect> BORRACHERA = EFFECTS.register("borrachera", BorracheraEffect::new);

    // ---- pociones (nombre base compartido: "helio" → item.minecraft.potion.effect.helio)
    public static final RegistryObject<Potion> POCION_HELIO = pocion("helio", HELIO, CORTA);
    public static final RegistryObject<Potion> POCION_HELIO_LARGA = pocion("long_helio", "helio", HELIO, LARGA);
    public static final RegistryObject<Potion> POCION_TARTAMUDEO = pocion("tartamudeo", TARTAMUDEO, CORTA);
    public static final RegistryObject<Potion> POCION_TARTAMUDEO_LARGA = pocion("long_tartamudeo", "tartamudeo", TARTAMUDEO, LARGA);
    public static final RegistryObject<Potion> POCION_VENTILADOR = pocion("ventilador", VENTILADOR, CORTA);
    public static final RegistryObject<Potion> POCION_VENTILADOR_LARGA = pocion("long_ventilador", "ventilador", VENTILADOR, LARGA);
    public static final RegistryObject<Potion> POCION_BORRACHERA = pocion("borrachera", BORRACHERA, CORTA);
    public static final RegistryObject<Potion> POCION_BORRACHERA_LARGA = pocion("long_borrachera", "borrachera", BORRACHERA, LARGA);

    /** Pociones normales que salen en la pestaña de creativo (3 min y 8 min de cada una). */
    @SuppressWarnings("unchecked")
    public static final RegistryObject<Potion>[] FRASCOS_CREATIVO = new RegistryObject[]{
            POCION_HELIO, POCION_HELIO_LARGA, POCION_TARTAMUDEO, POCION_TARTAMUDEO_LARGA,
            POCION_VENTILADOR, POCION_VENTILADOR_LARGA, POCION_BORRACHERA, POCION_BORRACHERA_LARGA};

    // ---- sonidos
    public static final RegistryObject<SoundEvent> HIPO = sonido("hipo");
    public static final RegistryObject<SoundEvent> VOMITO = sonido("vomito");

    private static RegistryObject<Potion> pocion(String id, RegistryObject<MobEffect> ef, int dur) {
        return pocion(id, id, ef, dur);
    }

    private static RegistryObject<Potion> pocion(String id, String nombre, RegistryObject<MobEffect> ef, int dur) {
        return POTIONS.register(id, () -> new Potion(nombre, new MobEffectInstance(ef.get(), dur)));
    }

    private static RegistryObject<SoundEvent> sonido(String n) {
        return SOUNDS.register(n, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(Miscelanea.MOD_ID, n)));
    }

    /** Frasco de poción normal con la poción dada (para la pestaña de creativo). */
    public static ItemStack frasco(RegistryObject<Potion> p) {
        return PotionUtils.setPotion(new ItemStack(Items.POTION), p.get());
    }

    /** Poción arrojadiza. */
    public static ItemStack arrojadiza(RegistryObject<Potion> p) {
        return PotionUtils.setPotion(new ItemStack(Items.SPLASH_POTION), p.get());
    }

    public static void registrar(IEventBus modBus) {
        EFFECTS.register(modBus);
        POTIONS.register(modBus);
        SOUNDS.register(modBus);
        VozPorEfecto.porDefecto();
    }
}
