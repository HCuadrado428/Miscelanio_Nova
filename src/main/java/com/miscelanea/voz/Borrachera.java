package com.miscelanea.voz;

import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Borrachera, parte del servidor: hipos (los oye todo el mundo), vomitar (agacharse seguido {@link #TICKS_VOMITAR}
 * ticks: partículas verdes, arcada y se pasa la borrachera), mobs que andan en zigzag y chat farfullado. El resto
 * (cámara que se mece, controles que se van, visión doble, voz de lengua de trapo) va en el cliente.
 */
public final class Borrachera {
    private Borrachera() {}

    private static final String CLAVE_SNEAK = "MiscelaneaBorrAgachado", CLAVE_HIPO = "MiscelaneaBorrHipo";
    /** 3 s agachado seguidos. */
    public static final int TICKS_VOMITAR = 60;

    // ------------------------------------------------------------------ jugador (desde LivingTickEvent, donde se puede quitar el efecto)
    public static void tickJugador(ServerPlayer p, MobEffectInstance ef) {
        CompoundTag d = p.getPersistentData();
        int amp = ef.getAmplifier();
        // hipo
        int hipo = d.getInt(CLAVE_HIPO);
        if (hipo <= 0) {
            if (d.contains(CLAVE_HIPO)) {
                p.level().playSound(null, p.getX(), p.getY() + p.getEyeHeight() * 0.8, p.getZ(), VozRegistro.HIPO.get(), SoundSource.PLAYERS,
                        0.9F, 0.9F + p.getRandom().nextFloat() * 0.25F);
                // un saltito con el hipo
                Vec3 v = p.getDeltaMovement();
                if (p.onGround()) p.setDeltaMovement(v.x, Math.max(v.y, 0.12), v.z);
                p.hurtMarked = true;
            }
            hipo = 60 + p.getRandom().nextInt(140) / (amp + 1);
        }
        d.putInt(CLAVE_HIPO, hipo - 1);
        // agacharse seguido → vomitar
        int ag = p.isShiftKeyDown() && p.onGround() && !p.isPassenger() ? d.getInt(CLAVE_SNEAK) + 1 : 0;
        d.putInt(CLAVE_SNEAK, ag);
        if (ag >= TICKS_VOMITAR) {
            d.putInt(CLAVE_SNEAK, 0);
            vomitar(p);
        }
    }

    public static void vomitar(ServerPlayer p) {
        if (!(p.level() instanceof ServerLevel sl)) return;
        Vec3 mira = p.getLookAngle();
        Vec3 boca = p.getEyePosition().add(mira.scale(0.35)).subtract(0, 0.25, 0);
        ItemParticleOption verde = new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.SLIME_BALL));
        for (int i = 0; i < 28; i++) {
            RandomSource r = p.getRandom();
            Vec3 v = mira.scale(0.25 + r.nextFloat() * 0.25).add((r.nextFloat() - 0.5) * 0.12, (r.nextFloat() - 0.3) * 0.1, (r.nextFloat() - 0.5) * 0.12);
            sl.sendParticles(verde, boca.x, boca.y, boca.z, 0, v.x, v.y, v.z, 1.0);
        }
        sl.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.LIME_DYE)), boca.x, boca.y, boca.z, 10, 0.15, 0.1, 0.15, 0.08);
        sl.playSound(null, p.getX(), p.getY(), p.getZ(), VozRegistro.VOMITO.get(), SoundSource.PLAYERS, 1.0F, 0.95F + p.getRandom().nextFloat() * 0.1F);
        p.causeFoodExhaustion(6.0F);
        p.removeEffect(VozRegistro.BORRACHERA.get());
        p.getPersistentData().remove(CLAVE_HIPO);
    }

    public static void limpiar(ServerPlayer p) {
        CompoundTag d = p.getPersistentData();
        d.remove(CLAVE_SNEAK);
        d.remove(CLAVE_HIPO);
    }

    // ------------------------------------------------------------------ mobs: zigzag (desde applyEffectTick, cada 4 ticks)
    public static void tickMob(PathfinderMob m, int amp) {
        Vec3 v = m.getDeltaMovement();
        double h = Math.sqrt(v.x * v.x + v.z * v.z);
        float t = m.tickCount * 0.55F;
        float k = 0.5F + 0.5F * amp;
        // tambalea la mirada aunque esté quieto
        m.setYRot(m.getYRot() + Mth.sin(t) * 6F * k);
        m.yBodyRot = m.getYRot();
        if (h < 0.015 || !m.onGround()) return;
        Vec3 lado = new Vec3(-v.z / h, 0, v.x / h);
        double emp = Mth.sin(t) * 0.07 * k + (m.getRandom().nextFloat() - 0.5) * 0.02;
        m.setDeltaMovement(v.add(lado.scale(emp)));
        // de vez en cuando un traspiés: se para un momento y vuelve a andar
        if (m.getRandom().nextInt(60) == 0) m.getNavigation().stop();
    }

    // ------------------------------------------------------------------ chat farfullado
    private static final String VOCALES = "aeiouáéíóú";

    public static String farfullar(String texto, int amp, RandomSource r) {
        float k = Math.min(1F, 0.5F + 0.25F * amp);
        StringBuilder out = new StringBuilder(texto.length() * 2);
        int i = 0, n = texto.length(), palabras = 0;
        while (i < n) {
            char c = texto.charAt(i);
            if (!Character.isLetter(c)) {
                out.append(c);
                i++;
                continue;
            }
            int j = i;
            while (j < n && Character.isLetter(texto.charAt(j))) j++;
            String p = texto.substring(i, j);
            i = j;
            palabras++;
            // "s" arrastrada: "eshto"
            if (r.nextFloat() < 0.5F * k) p = p.replaceFirst("[sS]", r.nextFloat() < 0.5F ? "sh" : "ss");
            // letras bailadas
            if (p.length() >= 4 && r.nextFloat() < 0.4F * k) {
                int a = 1 + r.nextInt(p.length() - 2);
                char[] cs = p.toCharArray();
                char tmp = cs[a];
                cs[a] = cs[a + 1];
                cs[a + 1] = tmp;
                p = new String(cs);
            }
            // vocal alargada: "holaaa"
            if (r.nextFloat() < 0.35F * k) {
                int ult = -1;
                for (int q = 0; q < p.length(); q++) if (VOCALES.indexOf(Character.toLowerCase(p.charAt(q))) >= 0) ult = q;
                if (ult >= 0) p = p.substring(0, ult + 1) + String.valueOf(p.charAt(ult)).repeat(1 + r.nextInt(3)) + p.substring(ult + 1);
            }
            out.append(p);
            if (palabras % 3 == 0 && r.nextFloat() < 0.45F * k) out.append(" *hic*");
        }
        if (r.nextFloat() < 0.3F * k) out.append(" *hic*");
        return out.toString();
    }
}
