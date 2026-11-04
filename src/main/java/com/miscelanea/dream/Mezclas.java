package com.miscelanea.dream;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.alchemy.PotionUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Reglas de las mezclas del caldero:
 * <ul>
 *   <li>Vale cualquier poción con efectos (normal, arrojadiza o persistente, del juego o del mod), los frascos de mezcla
 *       y la esencia y el veneno de Dream.</li>
 *   <li>El mismo efecto dos veces: se queda el más fuerte (más nivel; a igual nivel, el más largo).</li>
 *   <li>Cuantos más efectos distintos, menos dura cada uno: x1, x0,8, x0,65, x0,5 para 1-4 efectos (y un poco menos aún
 *       si una poción traía varios: nunca baja de x0,3). Los instantáneos (curación, daño) no cambian.</li>
 * </ul>
 * Los frascos guardan los efectos en las etiquetas de poción del juego (CustomPotionEffects y CustomPotionColor), así que
 * beberlos, lanzarlos, la descripción y las partículas funcionan igual que con una poción normal.
 */
public final class Mezclas {
    private Mezclas() {}

    public static final int MAX_POCIONES = 4;
    public static final String TAG_POCIONES = "MezclaPociones";
    /** Lo que dura el mordisco de la esencia de Dream: 5 minutos. */
    public static final int DURACION_ESENCIA = 5 * 60 * 20;

    /** Efectos que aporta un objeto al caldero (vacío si no sirve). */
    public static List<MobEffectInstance> efectosDe(ItemStack st) {
        if (st.isEmpty()) return List.of();
        if (st.getItem() instanceof EsenciaDreamItem) return List.of(new MobEffectInstance(DreamRegistro.MORDISCO.get(), DURACION_ESENCIA));
        if (st.getItem() instanceof VenenoDreamItem) return PotionUtils.getMobEffects(VenenoDreamItem.asegurar(st.copy()));
        if (st.getItem() instanceof PotionItem) return PotionUtils.getMobEffects(st);
        return List.of();
    }

    public static boolean esPocion(ItemStack st) {
        return st.getItem() instanceof PotionItem || st.getItem() instanceof EsenciaDreamItem;
    }

    public static int colorDe(ItemStack st) {
        if (st.getItem() instanceof EsenciaDreamItem) return DreamRegistro.COLOR_MORDISCO;
        return PotionUtils.getColor(st);
    }

    /** Mete un efecto en la lista: si ya estaba, se queda el más fuerte (o el más largo a igual nivel). */
    public static void fusionar(List<MobEffectInstance> lista, MobEffectInstance nuevo) {
        for (int i = 0; i < lista.size(); i++) {
            MobEffectInstance v = lista.get(i);
            if (v.getEffect() != nuevo.getEffect()) continue;
            boolean mejor = nuevo.getAmplifier() > v.getAmplifier()
                    || (nuevo.getAmplifier() == v.getAmplifier() && nuevo.getDuration() > v.getDuration());
            if (mejor) lista.set(i, new MobEffectInstance(nuevo));
            return;
        }
        lista.add(new MobEffectInstance(nuevo));
    }

    /** Multiplicador de la duración según cuántos efectos distintos lleva la mezcla. */
    public static float factor(int efectos) {
        return switch (Math.max(1, efectos)) {
            case 1 -> 1.0F;
            case 2 -> 0.8F;
            case 3 -> 0.65F;
            case 4 -> 0.5F;
            default -> Math.max(0.3F, 0.5F - 0.05F * (efectos - 4));
        };
    }

    /** Los efectos ya recortados como saldrán en el frasco. */
    public static List<MobEffectInstance> escalados(List<MobEffectInstance> base) {
        float f = factor(base.size());
        List<MobEffectInstance> l = new ArrayList<>();
        for (MobEffectInstance e : base) {
            int d = e.getEffect().isInstantenous() || e.isInfiniteDuration() ? e.getDuration() : Math.max(20, Math.round(e.getDuration() * f));
            l.add(new MobEffectInstance(e.getEffect(), d, e.getAmplifier(), e.isAmbient(), e.isVisible(), e.showIcon()));
        }
        return l;
    }

    /** Media de colores RGB (lo que se ve en el caldero y en el frasco). */
    public static int mediaColor(List<Integer> colores) {
        if (colores.isEmpty()) return 0x385DC6;
        long r = 0, g = 0, b = 0;
        for (int c : colores) {
            // media en el espacio "al cuadrado": se parece más a mezclar pintura con luz que la media directa
            r += (long) (c >> 16 & 255) * (c >> 16 & 255);
            g += (long) (c >> 8 & 255) * (c >> 8 & 255);
            b += (long) (c & 255) * (c & 255);
        }
        int n = colores.size();
        return (int) Math.sqrt(r / (double) n) << 16 | (int) Math.sqrt(g / (double) n) << 8 | (int) Math.sqrt(b / (double) n);
    }

    /** Un frasco de mezcla (normal o arrojadizo) con estos efectos. */
    public static ItemStack frasco(List<MobEffectInstance> efectos, int color, boolean arrojadizo, int pociones) {
        ItemStack st = new ItemStack(arrojadizo ? DreamRegistro.FRASCO_MEZCLA_ARROJADIZO.get() : DreamRegistro.FRASCO_MEZCLA.get());
        PotionUtils.setCustomEffects(st, efectos);
        CompoundTag t = st.getOrCreateTag();
        t.putInt("CustomPotionColor", color & 0xFFFFFF);
        t.putInt(TAG_POCIONES, pociones);
        return st;
    }

    /** Frascos de ejemplo para la pestaña del creativo (velocidad + fuerza + visión nocturna, normal y arrojadizo). */
    public static List<ItemStack> ejemplos() {
        List<MobEffectInstance> base = new ArrayList<>();
        base.add(new MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 3600));
        base.add(new MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST, 3600));
        base.add(new MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION, 3600));
        List<Integer> colores = new ArrayList<>();
        for (MobEffectInstance e : base) colores.add(e.getEffect().getColor());
        int c = mediaColor(colores);
        return List.of(frasco(escalados(base), c, false, 3), frasco(escalados(base), c, true, 3));
    }
}
