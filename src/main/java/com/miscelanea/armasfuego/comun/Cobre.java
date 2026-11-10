package com.miscelanea.armasfuego.comun;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * El cobre de las nueve armas de fuego: cuatro fases (nueva, expuesta, erosionada, oxidada) que se pasan con el uso
 * (más deprisa mojado). Cada fase es un intercambio, no solo un castigo: la nueva es precisa y fiable con el daño
 * normal; la oxidada se encasquilla y dispersa más, pero el cañón gastado y basto pega más fuerte y perfora más
 * armadura; las otras dos, términos medios (todo en la config, {@code armas_de_fuego_equilibrio}). Un panal la encera
 * para unos cuantos disparos (la cera se gasta; volver a encerarla la rellena) y un hacha quita la cera o un paso de
 * óxido. Va en el NBT del arma ({@code Oxido} 0..3, {@code Cera} disparos que le quedan, {@code Usos}).
 */
public final class Cobre {
    private Cobre() {}

    public static final String OXIDO = "Oxido", ENCERADO = "Encerado", USOS = "Usos", CERA = "Cera", ENCERADAS = "Enceradas";
    /** La marca de cera de antes (sin contador) de la escopeta. */
    private static final String ENCERADA = "Encerada";

    // ------------------------------------------------------------------ lo que hace cada fase (de la config)

    /** Encasquillado que añade la fase (0..1). */
    public static float atasco(int ox) {
        return EquilibrioArmas.oxidoAtasco(ox);
    }

    /** El encasquillado de la fase al disparar (la prueba del CI los apaga). */
    public static float atascoDisparo(int ox) {
        return EquilibrioArmas.sinAtascosPrueba ? 0F : atasco(ox);
    }

    /** Dispersión (radianes) que añade la fase. */
    public static double dispersion(int ox) {
        return EquilibrioArmas.oxidoDispersion(ox);
    }

    /** Multiplicador del daño de la fase (la oxidada, el cañón gastado y basto, pega más). */
    public static float dano(int ox) {
        return EquilibrioArmas.oxidoDano(ox);
    }

    /** Parte del daño que se salta la armadura que añade la fase (0..1). */
    public static float perforacion(int ox) {
        return EquilibrioArmas.oxidoPerforacion(ox);
    }

    public static float dano(ItemStack s) {
        return dano(oxido(s));
    }

    public static float perforacion(ItemStack s) {
        return perforacion(oxido(s));
    }

    public static int oxido(ItemStack s) {
        return s.hasTag() ? Mth.clamp(s.getTag().getInt(OXIDO), 0, 3) : 0;
    }

    public static void ponerOxido(ItemStack s, int o) {
        s.getOrCreateTag().putInt(OXIDO, Mth.clamp(o, 0, 3));
    }

    /** ¿Tiene cera? (los disparos que le quedan, o la marca de antes sin contador) */
    public static boolean encerado(ItemStack s) {
        if (!s.hasTag()) return false;
        CompoundTag t = s.getTag();
        return t.getInt(CERA) > 0 || t.getBoolean(ENCERADO) || t.getBoolean(ENCERADA);
    }

    /** Los disparos de cera que le quedan (la marca de antes, sin contador, cuenta como recién encerada). */
    public static int cera(ItemStack s, int duracion) {
        if (!s.hasTag()) return 0;
        CompoundTag t = s.getTag();
        if (t.getInt(CERA) > 0) return t.getInt(CERA);
        return t.getBoolean(ENCERADO) || t.getBoolean(ENCERADA) ? duracion : 0;
    }

    public static void ponerCera(ItemStack s, int n) {
        CompoundTag t = s.getOrCreateTag();
        t.remove(ENCERADO);
        t.remove(ENCERADA);
        if (n > 0) t.putInt(CERA, n);
        else t.remove(CERA);
    }

    /** Lo que dura la cera de un arma, en disparos (la Gatling y la Thompson, por cadencia, mucho más). */
    public static int duracionCera(ItemStack s) {
        var it = s.getItem();
        return it instanceof com.miscelanea.armasfuego.gatling.GatlingItem || it instanceof com.miscelanea.armasfuego.oeste.ThompsonItem
                ? EquilibrioArmas.ceraAutomaticas() : EquilibrioArmas.cera();
    }

    /** Resultado de {@link #desgastar}: nada, cambio de fase o se acabó la cera. */
    public static final int NADA = 0, FASE = 1, SIN_CERA = 2;

    /**
     * Un disparo más. Con cera, se gasta un disparo de cera (y avisa si se acaba: vuelve a oxidarse); sin ella, cada
     * {@code cada} disparos (mojado, el triple de rápido) pasa a la siguiente fase.
     */
    public static int gastar(ItemStack s, int cada, boolean mojado) {
        if (encerado(s)) {
            int c = cera(s, duracionCera(s)) - 1;
            ponerCera(s, c);
            return c <= 0 ? SIN_CERA : NADA;
        }
        return desgastar(s, cada, mojado) ? FASE : NADA;
    }

    /** Aviso pequeño en la barra de acción de lo que haya pasado en {@link #gastar}. */
    public static void avisar(ServerPlayer p, ItemStack s, int que) {
        if (que == FASE) avisar(p, s);
        else if (que == SIN_CERA) {
            p.displayClientMessage(Component.translatable("armafuego.miscelanea.sin_cera").withStyle(ChatFormatting.GOLD), true);
            p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AXE_WAX_OFF, SoundSource.PLAYERS, 0.5F, 1.4F);
        }
    }

    /**
     * Un disparo más sin cera: cada {@code cada} disparos (mojado, el triple de rápido) pasa a la siguiente fase.
     * Devuelve si ha cambiado de fase.
     */
    public static boolean desgastar(ItemStack s, int cada, boolean mojado) {
        if (encerado(s) || oxido(s) >= 3) return false;
        CompoundTag t = s.getOrCreateTag();
        int u = t.getInt(USOS) + (mojado ? 3 : 1);
        if (u >= cada) {
            t.putInt(OXIDO, oxido(s) + 1);
            t.putInt(USOS, 0);
            return true;
        }
        t.putInt(USOS, u);
        return false;
    }

    /** Aviso en la barra de acción al pasar de fase. */
    public static void avisar(ServerPlayer p, ItemStack s) {
        p.displayClientMessage(Component.translatable("armafuego.miscelanea.oxido." + oxido(s)).withStyle(ChatFormatting.DARK_AQUA), true);
    }

    public static boolean esPanal(ItemStack s) {
        return s.is(Items.HONEYCOMB);
    }

    public static boolean esHacha(ItemStack s) {
        return s.getItem() instanceof AxeItem;
    }

    /**
     * Panal: encera para {@link #duracionCera} disparos (si ya tenía cera, la rellena). Devuelve si ha hecho algo. Los
     * sonidos y chispas, en {@code donde} (null = sin partículas).
     */
    public static boolean encerar(Player p, ItemStack s, ItemStack panal, Vec3 donde) {
        int dur = duracionCera(s);
        if (cera(s, dur) >= dur) return false;
        ponerCera(s, dur);
        if (!p.getAbilities().instabuild) panal.shrink(1);
        efecto(p, donde, true);
        // encerar la misma arma una y otra vez
        int n = s.getOrCreateTag().getInt(ENCERADAS) + 1;
        s.getOrCreateTag().putInt(ENCERADAS, n);
        if (n >= 5 && p instanceof ServerPlayer sp) com.miscelanea.logros.LogrosMiscelanea.dar(sp, "cera_cinco");
        return true;
    }

    /** Hacha: quita la cera o, si no tiene, un paso de óxido (como los bloques de cobre). */
    public static boolean rascar(Player p, ItemStack s, ItemStack hacha, Vec3 donde) {
        if (encerado(s)) {
            ponerCera(s, 0);
            sonido(p, donde, SoundEvents.AXE_WAX_OFF);
            if (donde != null && p.level() instanceof ServerLevel l) l.sendParticles(ParticleTypes.WAX_OFF, donde.x, donde.y, donde.z, 8, 0.2, 0.2, 0.2, 0.5);
        } else if (oxido(s) > 0) {
            s.getOrCreateTag().putInt(OXIDO, oxido(s) - 1);
            s.getOrCreateTag().putInt(USOS, 0);
            sonido(p, donde, SoundEvents.AXE_SCRAPE);
            if (donde != null && p.level() instanceof ServerLevel l) l.sendParticles(ParticleTypes.SCRAPE, donde.x, donde.y, donde.z, 8, 0.2, 0.2, 0.2, 0.5);
        } else return false;
        if (!p.level().isClientSide && hacha.isDamageableItem() && !p.getAbilities().instabuild && hacha.hurt(1, p.getRandom(), p instanceof ServerPlayer sp ? sp : null))
            hacha.shrink(1);
        return true;
    }

    private static void efecto(Player p, Vec3 donde, boolean cera) {
        sonido(p, donde, SoundEvents.HONEYCOMB_WAX_ON);
        if (donde != null && p.level() instanceof ServerLevel l) l.sendParticles(ParticleTypes.WAX_ON, donde.x, donde.y, donde.z, 8, 0.2, 0.2, 0.2, 0.5);
    }

    private static void sonido(Player p, Vec3 donde, net.minecraft.sounds.SoundEvent e) {
        if (p.level().isClientSide) {
            if (donde == null) p.playSound(e, 1.0F, 1.0F);   // en el inventario solo lo oye el que lo hace
            return;
        }
        Vec3 q = donde == null ? p.position() : donde;
        p.level().playSound(donde == null ? p : null, q.x, q.y, q.z, e, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /** El nombre con el estado del cobre: «Winchester 1873 erosionada (encerada)», como el del revólver. */
    public static MutableComponent nombre(MutableComponent n, ItemStack s, boolean femenino) {
        String g = femenino ? "armafuego.miscelanea.f." : "armafuego.miscelanea.m.";
        int ox = oxido(s);
        if (ox > 0) n = Component.translatable(g + "oxido_nombre." + ox, n);
        if (encerado(s)) n = Component.translatable(g + "encerado_nombre", n);
        return n;
    }

    /** La línea del tooltip: estado del cobre (y si está encerado). */
    public static Component estado(ItemStack s) {
        String[] ox = {"normal", "expuesto", "erosionado", "oxidado"};
        Component e = Component.translatable("item.miscelanea.rifle.cobre." + ox[oxido(s)]);
        return Component.translatable(encerado(s) ? "item.miscelanea.rifle.estado_encerado" : "item.miscelanea.rifle.estado", e).withStyle(ChatFormatting.DARK_AQUA);
    }

    /** Lo que hace la fase de ahora, en una línea corta («Oxidada: +15 % de daño y +10 de perforación; dispersa y se encasquilla más»). */
    public static Component efectoFase(ItemStack s) {
        int ox = oxido(s);
        if (ox == 0) return Component.translatable("armafuego.miscelanea.fase.0").withStyle(ChatFormatting.DARK_GRAY);
        return Component.translatable("armafuego.miscelanea.fase." + ox, Math.round((dano(ox) - 1) * 100), Math.round(perforacion(ox) * 100),
                Math.round(atasco(ox) * 100)).withStyle(ChatFormatting.DARK_GRAY);
    }

    /** La cera que le queda («Cera: 143 disparos»), o null si no tiene. */
    @javax.annotation.Nullable
    public static Component ceraQueda(ItemStack s) {
        if (!encerado(s)) return null;
        return Component.translatable("armafuego.miscelanea.cera_queda", cera(s, duracionCera(s))).withStyle(ChatFormatting.GOLD);
    }

    /** Las dos líneas del cobre para el tooltip de cualquier arma (efecto de la fase y cera que queda). */
    public static void tooltip(ItemStack s, java.util.List<Component> l) {
        l.add(efectoFase(s));
        Component c = ceraQueda(s);
        if (c != null) l.add(c);
    }
}
