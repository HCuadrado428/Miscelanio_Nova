package com.miscelanea.tortura.saw;

import com.miscelanea.tortura.aparatos.Aparatos;
import com.miscelanea.tortura.heridas.Parte;
import com.miscelanea.tortura.heridas.Tipo;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Lo que hace el alambre de espino a quien se mete en él (servidor). Se lleva la cuenta de lo que cada entidad recorre
 * dentro del alambre (en memoria: si sale más de {@link #OLVIDO} ticks, se empieza de cero): cada bloque entero, 1 de
 * daño "alambre_espino", el ruido de desgarro y una herida donde toca: las piernas casi siempre; el cuerpo y los brazos
 * si el alambre está a la altura del pecho; la cabeza si salta.
 */
public final class Alambre {
    private Alambre() {}

    public static final int OLVIDO = 6;
    private static final Map<UUID, Estado> ESTADOS = new HashMap<>();

    private static final class Estado {
        long tick;
        Vec3 ultima;
        double recorrido;
    }

    public static void tocar(LivingEntity e, BlockPos alambre) {
        if (!(e.level() instanceof ServerLevel sl) || !e.isAlive() || exento(e)) return;
        long t = sl.getGameTime();
        Estado st = ESTADOS.get(e.getUUID());
        if (st == null || t - st.tick > OLVIDO || t < st.tick || e.position().distanceToSqr(st.ultima) > 9) {
            st = new Estado();
            st.tick = t;
            st.ultima = e.position();
            ESTADOS.put(e.getUUID(), st);
            sl.playSound(null, e.getX(), e.getY() + 0.5, e.getZ(), SawRegistro.ALAMBRE_RASGAR.get(), SoundSource.BLOCKS, 0.5F, 1.2F);
            return;
        }
        if (st.tick == t) return;   // ya contado este tick (toca varios bloques a la vez)
        st.tick = t;
        Vec3 ahora = e.position();
        st.recorrido += ahora.distanceTo(st.ultima);
        st.ultima = ahora;
        while (st.recorrido >= 1.0 && e.isAlive()) {
            st.recorrido -= 1.0;
            desgarrar(e, sl, alambre);
        }
    }

    private static boolean exento(LivingEntity e) {
        return e instanceof Player p && (p.isCreative() || p.isSpectator());
    }

    private static void desgarrar(LivingEntity e, ServerLevel sl, BlockPos alambre) {
        RandomSource r = e.getRandom();
        boolean alto = alambre.getY() >= Math.floor(e.getY() + 0.01) + 1;
        boolean salta = !e.onGround() && e.getDeltaMovement().y > 0.02;
        Parte parte;
        if (salta && r.nextInt(3) == 0) parte = Parte.CABEZA;
        else if (alto) parte = switch (r.nextInt(3)) {
            case 0 -> Parte.TORSO;
            case 1 -> Parte.BRAZO_DER;
            default -> Parte.BRAZO_IZQ;
        };
        else parte = r.nextInt(5) == 0 ? (r.nextBoolean() ? Parte.BRAZO_DER : Parte.BRAZO_IZQ) : (r.nextBoolean() ? Parte.PIERNA_DER : Parte.PIERNA_IZQ);
        Aparatos.herida(e, parte, r.nextInt(3) == 0 ? Tipo.CORTE : Tipo.RASGUNO, 1);
        sl.playSound(null, e.getX(), e.getY() + 0.6, e.getZ(), SawRegistro.ALAMBRE_RASGAR.get(), SoundSource.PLAYERS, 1F, 0.85F + r.nextFloat() * 0.3F);
        double y = parte == Parte.CABEZA ? e.getEyeY() : alto ? e.getY() + 1.1 : e.getY() + 0.4;
        sl.sendParticles(new DustParticleOptions(new Vector3f(0.5F, 0.03F, 0.03F), 0.9F), e.getX(), y, e.getZ(), 5, 0.2, 0.15, 0.2, 0.0);
        Aparatos.danar(e, SawRegistro.DANO_ALAMBRE, 1F, null);
    }

    /** Golpear el alambre con la mano. */
    public static void cortarMano(Player p, BlockPos pos) {
        if (exento(p) || !(p.level() instanceof ServerLevel sl)) return;
        Aparatos.herida(p, p.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT ? Parte.BRAZO_DER : Parte.BRAZO_IZQ, Tipo.RASGUNO, 1);
        sl.playSound(null, pos, SawRegistro.ALAMBRE_RASGAR.get(), SoundSource.BLOCKS, 0.8F, 1.4F);
        p.displayClientMessage(Component.translatable("msg.miscelanea.saw.alambre.mano").withStyle(ChatFormatting.RED), true);
        Aparatos.danar(p, SawRegistro.DANO_ALAMBRE, 1F, null);
    }

    /** Cada pocos segundos: se olvida a quien ya no está dentro. */
    public static void limpiar(long ahora, boolean todo) {
        if (todo) {
            ESTADOS.clear();
            return;
        }
        for (Iterator<Estado> it = ESTADOS.values().iterator(); it.hasNext(); ) {
            Estado st = it.next();
            if (Math.abs(ahora - st.tick) > 200) it.remove();
        }
    }
}
