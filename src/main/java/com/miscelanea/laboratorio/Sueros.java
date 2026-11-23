package com.miscelanea.laboratorio;

import com.miscelanea.pociones.PocionesRegistro;
import com.miscelanea.voz.VozRegistro;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Supplier;

/**
 * Los sueros del laboratorio: un frasco con uno de nueve efectos del mod. Qué sale al mezclar dos cosas es siempre lo
 * mismo (sale de unos valores fijos), así que se puede descubrir y apuntar en el cuaderno.
 */
public final class Sueros {
    private Sueros() {}

    public static final int TIPOS = 11;
    public static final int SEGUNDOS = 60;

    /** Efecto de cada tipo, por orden: la clave de texto es {@code laboratorio.miscelanea.suero.<n>}. */
    private static final List<Supplier<MobEffect>> EFECTOS = List.of(PocionesRegistro.GIGANTE, PocionesRegistro.ENANO, PocionesRegistro.ECOLOCALIZACION,
            PocionesRegistro.ESPEJISMO, PocionesRegistro.NIEBLA_VIVIENTE, VozRegistro.HELIO, VozRegistro.TARTAMUDEO, VozRegistro.VENTILADOR, VozRegistro.BORRACHERA,
            com.miscelanea.glitch.GlitchRegistro.EFECTO, com.miscelanea.estatica.EstaticaRegistro.EFECTO);

    public static MobEffect efecto(int tipo) {
        return EFECTOS.get(Math.floorMod(tipo, TIPOS)).get();
    }

    public static void aplicar(LivingEntity v, int tipo) {
        v.addEffect(new MobEffectInstance(efecto(tipo), (int) Math.max(20, SEGUNDOS * 20 * LabRopa.factorSuero(v)), 0));
    }

    /** Un frasco de suero de ese tipo (conocido = ya identificado en el analizador). */
    public static ItemStack crear(int tipo, boolean conocido) {
        ItemStack s = new ItemStack(LabRegistro.SUERO.get());
        s.getOrCreateTag().putInt("Tipo", Math.floorMod(tipo, TIPOS));
        if (conocido) s.getOrCreateTag().putBoolean("Conocido", true);
        return s;
    }

    public static int tipo(ItemStack s) {
        return s.getTag() == null ? 0 : Math.floorMod(s.getTag().getInt("Tipo"), TIPOS);
    }

    public static boolean conocido(ItemStack s) {
        return s.getTag() != null && s.getTag().getBoolean("Conocido");
    }

    /** ¿Sirve para mezclar? (muestras, quesos y sueros) */
    public static boolean ingrediente(ItemStack s) {
        return valor(s) > 0;
    }

    /** Valor fijo de cada ingrediente para la mezcla (0 = no sirve). */
    public static int valor(ItemStack s) {
        if (s.is(LabRegistro.MUESTRA_MIEDO.get())) return 1;
        if (s.is(LabRegistro.MUESTRA_MAREO.get())) return 2;
        if (s.is(LabRegistro.MUESTRA_FRIO.get())) return 3;
        if (s.is(LabRegistro.MUESTRA_CALOR.get())) return 4;
        if (s.is(LabRegistro.QUESO.get())) return 5;
        if (s.is(com.miscelanea.glitch.GlitchRegistro.MUESTRA.get())) return 7;   // el glitch: un ingrediente raro
        if (s.is(LabRegistro.SUERO.get())) return 10 + tipo(s);
        return 0;
    }

    /** El suero que sale de mezclar dos ingredientes (el mismo, en cualquier orden). */
    public static ItemStack mezclar(ItemStack a, ItemStack b) {
        int x = valor(a), y = valor(b);
        return crear(Math.floorMod(x * y + x + y, TIPOS), false);
    }
}
