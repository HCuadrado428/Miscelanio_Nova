package com.miscelanea.laboratorio;

import com.miscelanea.tortura.Lazo;
import com.miscelanea.tortura.aparatos.Aparatos;
import com.miscelanea.tortura.efectos.EfectosRegistro;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Las fichas del cuaderno de laboratorio y las lecturas que miden los experimentos. Una ficha es un experimento
 * hecho: estación, sujeto, nivel (0 suave, 1 intenso, 2 extremo), cuándo y una lista de lecturas (clave -> texto).
 * Se guardan en el propio cuaderno (etiqueta {@code Fichas}); el cuaderno guarda como mucho {@link #MAX_FICHAS}.
 */
public final class Experimentos {
    private Experimentos() {}

    public static final int MAX_FICHAS = 40;

    // ------------------------------------------------------------------ cuaderno y fichas
    /** El primer cuaderno que lleva encima (mano o inventario), o vacío. */
    public static ItemStack cuaderno(Player p) {
        if (p.getMainHandItem().is(LabRegistro.CUADERNO.get())) return p.getMainHandItem();
        if (p.getOffhandItem().is(LabRegistro.CUADERNO.get())) return p.getOffhandItem();
        for (ItemStack s : p.getInventory().items) if (s.is(LabRegistro.CUADERNO.get())) return s;
        return ItemStack.EMPTY;
    }

    public static ListTag fichas(ItemStack cuaderno) {
        return cuaderno.isEmpty() || cuaderno.getTag() == null ? new ListTag() : cuaderno.getTag().getList("Fichas", Tag.TAG_COMPOUND);
    }

    /**
     * Apunta un experimento en el cuaderno del científico. Devuelve false si no lleva ninguno. {@code lecturas}: clave
     * (la del texto {@code laboratorio.miscelanea.lectura.<clave>}) y su valor ya escrito ("74 ppm").
     */
    public static boolean anotar(Player cientifico, String estacion, @Nullable Player sujeto, int nivel, Map<String, String> lecturas) {
        ItemStack cuaderno = cuaderno(cientifico);
        if (cuaderno.isEmpty()) return false;
        CompoundTag f = new CompoundTag();
        f.putString("Estacion", estacion);
        f.putString("Sujeto", nombreSujeto(cientifico, sujeto));
        f.putInt("Nivel", nivel);
        f.putLong("Dia", cientifico.level().getGameTime() / 24000L);
        ListTag ls = new ListTag();
        for (var e : lecturas.entrySet()) {
            CompoundTag l = new CompoundTag();
            l.putString("K", e.getKey());
            l.putString("V", e.getValue());
            ls.add(l);
        }
        f.put("Lecturas", ls);
        ListTag todas = fichas(cuaderno);
        todas.add(f);
        while (todas.size() > MAX_FICHAS) todas.remove(0);
        cuaderno.getOrCreateTag().put("Fichas", todas);
        return true;
    }

    /** "Sujeto 7" si el científico lleva un portapapeles con ese jugador numerado; si no, su nombre. */
    public static String nombreSujeto(Player cientifico, @Nullable Player sujeto) {
        if (sujeto == null) return "?";
        for (ItemStack s : cientifico.getInventory().items) {
            if (!s.is(LabRegistro.PORTAPAPELES.get()) || s.getTag() == null) continue;
            CompoundTag sujetos = s.getTag().getCompound("Sujetos");
            if (sujetos.contains(sujeto.getStringUUID())) return "Sujeto " + sujetos.getInt(sujeto.getStringUUID());
        }
        return sujeto.getGameProfile().getName();
    }

    // ------------------------------------------------------------------ lecturas
    /** Cuánto miedo tiene (0 a 1): del efecto de miedo, de estar atado y de estar metido en un aparato. */
    public static double miedo(LivingEntity v) {
        double m = 0;
        MobEffectInstance e = v.getEffect(EfectosRegistro.MIEDO.get());
        if (e != null) m += 0.5 + 0.25 * e.getAmplifier();
        if (Lazo.estado(v) != 0) m += 0.15;
        if (Aparatos.en(v)) m += 0.35;
        if (v.hurtTime > 0) m += 0.1;
        return Math.max(0, Math.min(1, m));
    }

    /** Pulso en pulsaciones por minuto: sube con el miedo, con correr y con el daño reciente. */
    public static int pulso(LivingEntity v) {
        double p = 62 + 78 * miedo(v) + (v.isSprinting() ? 25 : 0) + (v.hurtTime > 0 ? 15 : 0);
        return (int) Math.round(p) + v.getRandom().nextInt(5) - 2;
    }

    /** Las lecturas de un reconocimiento (lo que mide el pulsómetro). */
    public static Map<String, String> reconocimiento(LivingEntity v) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("pulso", pulso(v) + " ppm");
        m.put("miedo", Math.round(miedo(v) * 100) + " %");
        return m;
    }

}
