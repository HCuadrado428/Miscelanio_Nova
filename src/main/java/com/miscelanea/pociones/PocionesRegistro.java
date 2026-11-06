package com.miscelanea.pociones;

import com.miscelanea.Miscelanea;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Pociones "del cuerpo" (solo de creativo, sin receta de alquimia): espejismo (te ven como otro jugador), sombra (la
 * sombra de Hollow Knight), gigante y enano (Forge escala el tamaño), ecolocalización (a ciegas, ves por los sonidos),
 * sed de sangre (berserk de Doom) y niebla viviente (Silent Hill). Cada efecto tiene su poción de 3:00 y la larga de
 * 8:00; las arrojadizas y persistentes salen solas en la pestaña de combate. Registro aparte (como EfectosRegistro):
 * basta con {@link #registrar(IEventBus)} desde el constructor del mod. Todo funciona también con /effect.
 */
public final class PocionesRegistro {
    private PocionesRegistro() {}

    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, Miscelanea.MOD_ID);
    public static final DeferredRegister<Potion> POTIONS = DeferredRegister.create(ForgeRegistries.POTIONS, Miscelanea.MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, Miscelanea.MOD_ID);
    public static final DeferredRegister<net.minecraft.core.particles.ParticleType<?>> PARTICLES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, Miscelanea.MOD_ID);

    // ---- colores (también el de la botella)
    public static final int COLOR_ESPEJISMO = 0x9C6BFF, COLOR_SOMBRA = 0x1C1A26, COLOR_GIGANTE = 0xD98B2B, COLOR_ENANO = 0x6BC26B,
            COLOR_ECO = 0x58C8FF, COLOR_SANGRE = 0xB3121B, COLOR_NIEBLA = 0xA9A39B;

    // ---- efectos
    public static final RegistryObject<MobEffect> ESPEJISMO = EFFECTS.register("espejismo",
            () -> new EfectoCuerpo(MobEffectCategory.NEUTRAL, COLOR_ESPEJISMO));
    public static final RegistryObject<MobEffect> SOMBRA = EFFECTS.register("sombra",
            () -> new EfectoCuerpo(MobEffectCategory.NEUTRAL, COLOR_SOMBRA));
    public static final RegistryObject<MobEffect> GIGANTE = EFFECTS.register("gigante",
            () -> new EfectoCuerpo(MobEffectCategory.BENEFICIAL, COLOR_GIGANTE));
    public static final RegistryObject<MobEffect> ENANO = EFFECTS.register("enano",
            () -> new EfectoCuerpo(MobEffectCategory.NEUTRAL, COLOR_ENANO));
    public static final RegistryObject<MobEffect> ECOLOCALIZACION = EFFECTS.register("ecolocalizacion",
            () -> new EfectoCuerpo(MobEffectCategory.NEUTRAL, COLOR_ECO));
    /** Sed de sangre: la velocidad de ataque va doblada por el propio efecto (x2 por nivel); el resto, en PocionesEventos. */
    public static final RegistryObject<MobEffect> SED_DE_SANGRE = EFFECTS.register("sed_de_sangre",
            () -> new EfectoCuerpo(MobEffectCategory.BENEFICIAL, COLOR_SANGRE)
                    .addAttributeModifier(Attributes.ATTACK_SPEED, "7c1f6a52-3d2e-4b7a-9c1d-5e8f0a2b4c6d", 1.0, AttributeModifier.Operation.MULTIPLY_TOTAL));
    public static final RegistryObject<MobEffect> NIEBLA_VIVIENTE = EFFECTS.register("niebla_viviente",
            () -> new EfectoCuerpo(MobEffectCategory.HARMFUL, COLOR_NIEBLA));

    // ---- pociones (3:00 y 8:00); la sed de sangre dura 30 s (y 1:30 la larga), como el berserk
    private static final List<RegistryObject<Potion>> TODAS = new ArrayList<>();
    public static final RegistryObject<Potion> P_ESPEJISMO = pocion("espejismo", ESPEJISMO, 3600);
    public static final RegistryObject<Potion> P_ESPEJISMO_LARGA = pocion("espejismo_largo", "espejismo", ESPEJISMO, 9600);
    public static final RegistryObject<Potion> P_SOMBRA = pocion("sombra", SOMBRA, 3600);
    public static final RegistryObject<Potion> P_SOMBRA_LARGA = pocion("sombra_largo", "sombra", SOMBRA, 9600);
    public static final RegistryObject<Potion> P_GIGANTE = pocion("gigante", GIGANTE, 3600);
    public static final RegistryObject<Potion> P_GIGANTE_LARGA = pocion("gigante_largo", "gigante", GIGANTE, 9600);
    public static final RegistryObject<Potion> P_ENANO = pocion("enano", ENANO, 3600);
    public static final RegistryObject<Potion> P_ENANO_LARGA = pocion("enano_largo", "enano", ENANO, 9600);
    public static final RegistryObject<Potion> P_ECO = pocion("ecolocalizacion", ECOLOCALIZACION, 3600);
    public static final RegistryObject<Potion> P_ECO_LARGA = pocion("ecolocalizacion_largo", "ecolocalizacion", ECOLOCALIZACION, 9600);
    public static final RegistryObject<Potion> P_SANGRE = pocion("sed_de_sangre", SED_DE_SANGRE, 600);
    public static final RegistryObject<Potion> P_SANGRE_LARGA = pocion("sed_de_sangre_largo", "sed_de_sangre", SED_DE_SANGRE, 1800);
    public static final RegistryObject<Potion> P_NIEBLA = pocion("niebla_viviente", NIEBLA_VIVIENTE, 3600);
    public static final RegistryObject<Potion> P_NIEBLA_LARGA = pocion("niebla_viviente_largo", "niebla_viviente", NIEBLA_VIVIENTE, 9600);

    private static RegistryObject<Potion> pocion(String id, RegistryObject<MobEffect> ef, int ticks) {
        return pocion(id, id, ef, ticks);
    }

    private static RegistryObject<Potion> pocion(String id, String nombre, RegistryObject<MobEffect> ef, int ticks) {
        RegistryObject<Potion> r = POTIONS.register(id, () -> new Potion(nombre, new MobEffectInstance(ef.get(), ticks)));
        TODAS.add(r);
        return r;
    }

    /** Las botellas (normal, arrojadiza y persistente, cortas y largas) para la pestaña de combate, junto a la tortura. */
    public static List<ItemStack> pociones() {
        List<ItemStack> l = new ArrayList<>();
        for (RegistryObject<Potion> p : TODAS)
            for (Item i : new Item[]{Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION})
                l.add(PotionUtils.setPotion(new ItemStack(i), p.get()));
        return l;
    }

    // ---- sonidos (procedurales, gen_pociones1.py)
    /** La palmada del eco (sonar propio), el golpe de música del berserk, el chof de reventar, la sirena y el pisotón. */
    public static final RegistryObject<SoundEvent> PALMADA = sonido("pociones_palmada");
    public static final RegistryObject<SoundEvent> BERSERK = sonido("pociones_berserk");
    public static final RegistryObject<SoundEvent> REVENTAR = sonido("pociones_reventar");
    public static final RegistryObject<SoundEvent> SIRENA = sonido("pociones_sirena");
    public static final RegistryObject<SoundEvent> PISOTON = sonido("pociones_pisoton");

    // ---- partículas
    /** Voluta de humo negro que suelta la sombra (se deshace al subir). */
    public static final RegistryObject<net.minecraft.core.particles.SimpleParticleType> HUMO_SOMBRA =
            PARTICLES.register("sombra_humo", () -> new net.minecraft.core.particles.SimpleParticleType(false));

    private static RegistryObject<SoundEvent> sonido(String n) {
        return SOUNDS.register(n, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(Miscelanea.MOD_ID, n)));
    }

    public static void registrar(IEventBus modBus) {
        EFFECTS.register(modBus);
        POTIONS.register(modBus);
        SOUNDS.register(modBus);
        PARTICLES.register(modBus);
    }

    /** Efecto sin lógica propia en el tick: todo lo hacen los eventos (PocionesEventos) y el cliente. */
    public static class EfectoCuerpo extends MobEffect {
        public EfectoCuerpo(MobEffectCategory c, int color) {
            super(c, color);
        }
    }
}
