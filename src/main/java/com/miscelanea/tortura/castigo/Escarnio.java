package com.miscelanea.tortura.castigo;

import com.miscelanea.network.ModNetwork;
import com.miscelanea.tortura.TorturaRegistro;
import com.miscelanea.tortura.heridas.Parte;
import com.miscelanea.tortura.qte.Qte;
import com.miscelanea.tortura.qte.QteLogica;
import com.miscelanea.tortura.qte.QteParams;
import com.miscelanea.tortura.qte.QteTipo;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Escarnio público: marcador de los que tiran cosas al del cepo (combos), la LLUVIA DE TOMATES (3 impactos en 5 s al
 * mismo blanco) y la ESQUIVA (el del cepo ve venir el tomate y aparta la cabeza). Todo en memoria del servidor.
 */
public final class Escarnio {
    private Escarnio() {}

    public static final int VENTANA_LLUVIA = 5 * 20, ESPERA_LLUVIA = 10 * 20, COMBO_TICKS = 3 * 20, LLUVIA_N = 16;

    private static final class Sesion {
        final Map<UUID, Integer> puntos = new LinkedHashMap<>();
        final Map<UUID, String> nombres = new HashMap<>();
        final Map<UUID, Long> ultimo = new HashMap<>();
        final Map<UUID, Integer> combo = new HashMap<>();
        int esquivadas;
    }

    private static final class Esq {
        final int proyectil;
        final long hasta;
        final int lado;
        @Nullable final UUID tirador;
        boolean esquivado;

        Esq(int proyectil, long hasta, int lado, @Nullable UUID tirador) {
            this.proyectil = proyectil;
            this.hasta = hasta;
            this.lado = lado;
            this.tirador = tirador;
        }
    }

    private static final Map<UUID, Sesion> SESIONES = new HashMap<>();
    private static final Map<UUID, Deque<Long>> IMPACTOS = new HashMap<>();
    private static final Map<UUID, Long> ULTIMA_LLUVIA = new HashMap<>();
    private static final Map<UUID, Esq> ESQUIVAS = new HashMap<>();

    // ------------------------------------------------------------------ sesiones del cepo
    public static void iniciar(LivingEntity v) {
        SESIONES.put(v.getUUID(), new Sesion());
    }

    /** Se acaba el cepo: el marcador final a la víctima y a todos los que tiraron. */
    public static void terminar(LivingEntity v) {
        Sesion s = SESIONES.remove(v.getUUID());
        ESQUIVAS.remove(v.getUUID());
        if (s == null || s.puntos.isEmpty() || !(v.level() instanceof ServerLevel sl)) return;
        List<Map.Entry<UUID, Integer>> orden = new ArrayList<>(s.puntos.entrySet());
        orden.sort((a, b) -> b.getValue() - a.getValue());
        MutableComponent txt = Component.translatable("msg.miscelanea.cepo.marcador", v.getDisplayName()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        int i = 1;
        for (Map.Entry<UUID, Integer> en : orden) {
            txt.append(Component.literal("\n " + i + ". ").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(s.nombres.getOrDefault(en.getKey(), "?")).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("  " + en.getValue() + " pts").withStyle(ChatFormatting.GREEN));
            if (++i > 8) break;
        }
        if (s.esquivadas > 0)
            txt.append(Component.literal("\n ")).append(Component.translatable("msg.miscelanea.cepo.esquivadas", s.esquivadas).withStyle(ChatFormatting.AQUA));
        for (UUID u : s.puntos.keySet()) {
            ServerPlayer p = sl.getServer().getPlayerList().getPlayer(u);
            if (p != null) p.sendSystemMessage(txt);
        }
        if (v instanceof ServerPlayer sp && !s.puntos.containsKey(sp.getUUID())) sp.sendSystemMessage(txt);
    }

    public static boolean enCepo(Entity v) {
        return v.getVehicle() instanceof CepoEntity;
    }

    // ------------------------------------------------------------------ impactos
    /**
     * Un tomate / huevo ha dado a 't'. parte: dónde (null = no se sabe). lluvia: es de una lluvia de tomates (no cuenta
     * para otra lluvia y da poco).
     */
    public static void impacto(ProyectilPodrido pr, LivingEntity t, @Nullable Parte parte, boolean lluvia) {
        if (!(t.level() instanceof ServerLevel sl)) return;
        long ahora = sl.getGameTime();
        Entity o = pr.getOwner();
        ServerPlayer tirador = o instanceof ServerPlayer sp ? sp : null;
        // marcador (solo en el cepo, y solo si lo tira un jugador que no es la víctima)
        Sesion s = SESIONES.get(t.getUUID());
        if (s != null && tirador != null && tirador != t) {
            UUID u = tirador.getUUID();
            int base = parte == Parte.CABEZA ? 3 : parte != null && parte.esBrazo() ? 2 : 1;
            if (pr.huevo()) base++;
            int combo = 1;
            if (!lluvia) {
                Long ult = s.ultimo.get(u);
                combo = ult != null && ahora - ult <= COMBO_TICKS ? Math.min(5, s.combo.getOrDefault(u, 1) + 1) : 1;
                s.combo.put(u, combo);
                s.ultimo.put(u, ahora);
            } else base = 1;
            int pts = base * combo;
            int total = s.puntos.getOrDefault(u, 0) + pts;
            s.puntos.put(u, total);
            s.nombres.put(u, tirador.getGameProfile().getName());
            String clave = parte == Parte.CABEZA ? "msg.miscelanea.cepo.diana" : parte != null && parte.esBrazo() ? "msg.miscelanea.cepo.manos"
                    : "msg.miscelanea.cepo.cuerpo";
            MutableComponent m = Component.translatable(clave).withStyle(parte == Parte.CABEZA ? ChatFormatting.RED : ChatFormatting.GOLD, ChatFormatting.BOLD)
                    .append(Component.literal("  +" + pts).withStyle(ChatFormatting.GREEN));
            if (combo > 1) m.append(Component.literal("  ")).append(Component.translatable("msg.miscelanea.cepo.combo", combo).withStyle(ChatFormatting.LIGHT_PURPLE));
            m.append(Component.literal("  ")).append(Component.translatable("msg.miscelanea.cepo.total", total).withStyle(ChatFormatting.YELLOW));
            tirador.displayClientMessage(m, true);
        }
        if (lluvia) return;
        // lluvia de tomates: 3 impactos (de quien sea) en 5 s sobre el mismo blanco
        Deque<Long> d = IMPACTOS.computeIfAbsent(t.getUUID(), k -> new ArrayDeque<>());
        d.addLast(ahora);
        while (!d.isEmpty() && ahora - d.peekFirst() > VENTANA_LLUVIA) d.removeFirst();
        Long ultLluvia = ULTIMA_LLUVIA.get(t.getUUID());
        if (d.size() >= 3 && (ultLluvia == null || ahora - ultLluvia > ESPERA_LLUVIA || ultLluvia > ahora)) {
            d.clear();
            ULTIMA_LLUVIA.put(t.getUUID(), ahora);
            lluvia(sl, t, o);
        }
    }

    /** ¡LLUVIA DE TOMATES! Cae un chaparrón de tomates (y algún huevo) desde arriba sobre el blanco. */
    static void lluvia(ServerLevel sl, LivingEntity t, @Nullable Entity tirador) {
        Component titulo = Component.translatable("msg.miscelanea.lluvia.titulo").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
        Component sub = Component.translatable("msg.miscelanea.lluvia.sub", t.getDisplayName()).withStyle(ChatFormatting.GOLD);
        // sin letrero gigante en medio de la pantalla: una línea en el chat para los de alrededor
        Component linea = titulo.copy().append(Component.literal(" ")).append(sub);
        for (ServerPlayer p : sl.players()) {
            if (p.distanceToSqr(t) > 40 * 40) continue;
            p.sendSystemMessage(linea);
        }
        sl.playSound(null, t.getX(), t.getY() + 3, t.getZ(), TorturaRegistro.LLUVIA_TOMATES.get(), SoundSource.PLAYERS, 1.2F, 1F);
        for (int i = 0; i < LLUVIA_N; i++) {
            boolean huevo = t.getRandom().nextInt(5) == 0;
            double a = t.getRandom().nextDouble() * Math.PI * 2, r = 0.3 + t.getRandom().nextDouble() * 1.4;
            double x = t.getX() + Math.cos(a) * r, z = t.getZ() + Math.sin(a) * r, y = t.getY() + t.getBbHeight() + 3.5 + t.getRandom().nextDouble() * 6;
            ProyectilPodrido pr = new ProyectilPodrido(sl, x, y, z, huevo);
            if (tirador != null) pr.setOwner(tirador);
            pr.lluvia = true;
            pr.setDeltaMovement((t.getX() - x) * 0.045, -0.15 - t.getRandom().nextDouble() * 0.2, (t.getZ() - z) * 0.045);
            sl.addFreshEntity(pr);
        }
    }

    // ------------------------------------------------------------------ esquiva
    /** ¿Tiene ya un aviso de esquiva en marcha? */
    public static boolean avisado(LivingEntity v) {
        Esq e = ESQUIVAS.get(v.getUUID());
        return e != null && v.level().getGameTime() <= e.hasta;
    }

    /** ¿Ha esquivado ese proyectil? */
    public static boolean esquivando(LivingEntity v, int proyectil) {
        Esq e = ESQUIVAS.get(v.getUUID());
        return e != null && e.esquivado && e.proyectil == proyectil && v.level().getGameTime() <= e.hasta + 20;
    }

    /** Llega un proyectil al jugador del cepo dentro de 'ticks' ticks, por la izquierda o por la derecha (de su cara). */
    public static void avisarJugador(ServerPlayer v, ProyectilPodrido pr, int ticks, boolean porIzquierda) {
        int ms = ticks * 50;
        float d = QteLogica.esquivaDificultad(ms);
        long semilla = (v.getRandom().nextLong() & ~2L) | 1L | (porIzquierda ? 2L : 0L);
        int lado = porIzquierda ? 1 : -1;
        Entity o = pr.getOwner();
        final int id = pr.getId();
        ESQUIVAS.put(v.getUUID(), new Esq(id, v.level().getGameTime() + ticks + 6, lado, o == null ? null : o.getUUID()));
        Qte.iniciar(v, QteTipo.ESQUIVA, QteParams.de(d).rondas(pr.huevo() ? 2 : 1).semilla(semilla)
                .titulo(Component.translatable("qte.miscelanea.titulo.esquiva")), (j, r) -> {
            if (j == null) return;
            Esq e = ESQUIVAS.get(j.getUUID());
            if (e == null || e.proyectil != id) return;
            if (r.exito() && j.level().getGameTime() <= e.hasta && enCepo(j)) esquivar(j, e);
        });
    }

    /** Los mobs del cepo esquivan a veces solos. */
    public static void avisarMob(LivingEntity v, ProyectilPodrido pr, int ticks, boolean porIzquierda) {
        Entity o = pr.getOwner();
        Esq e = new Esq(pr.getId(), v.level().getGameTime() + ticks + 6, porIzquierda ? 1 : -1, o == null ? null : o.getUUID());
        ESQUIVAS.put(v.getUUID(), e);
        if (v.getRandom().nextFloat() < 0.3F) esquivar(v, e);
    }

    private static void esquivar(LivingEntity v, Esq e) {
        e.esquivado = true;
        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> v), new EsquivaPacket(v.getId(), e.lado));
        v.level().playSound(null, v.getX(), v.getEyeY(), v.getZ(), TorturaRegistro.ESQUIVA.get(), SoundSource.PLAYERS, 0.7F, 1.1F);
        Sesion s = SESIONES.get(v.getUUID());
        if (s != null) s.esquivadas++;
        if (e.tirador != null && v.level() instanceof ServerLevel sl) {
            Player t = sl.getPlayerByUUID(e.tirador);
            if (t != null) t.displayClientMessage(Component.translatable("msg.miscelanea.cepo.esquivado", v.getDisplayName()).withStyle(ChatFormatting.AQUA), true);
        }
    }

    public static void olvidar(LivingEntity v) {
        IMPACTOS.remove(v.getUUID());
        ULTIMA_LLUVIA.remove(v.getUUID());
        ESQUIVAS.remove(v.getUUID());
    }

    public static void limpiarMemoria() {
        SESIONES.clear();
        IMPACTOS.clear();
        ULTIMA_LLUVIA.clear();
        ESQUIVAS.clear();
    }
}
