package com.miscelanea.client.dream;

import com.miscelanea.Miscelanea;
import com.miscelanea.dream.AuraPacket;
import com.miscelanea.dream.DreamRegistro;
import com.miscelanea.dream.PersecucionPacket;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * Cliente de la caza de Dream (ver dream.Persecucion). Nada flota en el mundo: todo es del HUD o del propio jugador.
 * <ul>
 *   <li>Dream (cazador): las pisadas de su presa se quedan marcadas en el suelo con un brillo verde unos 20 s (solo las ve
 *       él: son partículas de su cliente); oye el latido de la presa dentro de la cabeza, más rápido cuanto más cerca; y
 *       una flechita verde alrededor de la mira apunta hacia ella.</li>
 *   <li>La presa: un ojo verde que late arriba en el HUD mientras la cazan.</li>
 *   <li>Quién es Dream o un infectado (para los ojos que brillan, {@link OjosLayer}).</li>
 * </ul>
 * Va en el tic del cliente con listas cortas (48 pisadas como mucho): no pesa en ordenadores flojos.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class PersecucionCliente {
    private PersecucionCliente() {}

    private static final int MAX_HUELLAS = 48, VIDA_HUELLA = 400;
    private static final DustParticleOptions HUELLA = new DustParticleOptions(new Vector3f(0.42F, 0.95F, 0.25F), 0.6F);
    private static final DustParticleOptions HUELLA_VIEJA = new DustParticleOptions(new Vector3f(0.2F, 0.5F, 0.12F), 0.5F);

    // ---- cazador
    private static int presa = -1, restante, latidoDist, proxLatido;
    private static boolean rastro;
    private static Vec3 presaPos = Vec3.ZERO;
    /** {x, y, z, tic en que se pisó} */
    private static final ArrayDeque<double[]> HUELLAS = new ArrayDeque<>();
    private static Vec3 ultimaHuella;
    private static boolean pieIzq;
    // ---- presa
    private static int cazado;
    // ---- quién es Dream / infectado: id → tic del cliente hasta el que vale
    private static final Map<Integer, Long> AURA = new HashMap<>();
    private static long tic;

    /** Solo para la prueba del CI. */
    public static int pruebaLatidos, pruebaFlechas, pruebaOjo;

    public static void recibir(PersecucionPacket p) {
        switch (p.tipo) {
            case CAZADOR -> {
                if (p.id != presa) {
                    HUELLAS.clear();
                    ultimaHuella = null;
                }
                presa = p.id;
                presaPos = new Vec3(p.x, p.y, p.z);
                restante = p.restante;
                latidoDist = p.latido;
                rastro = p.rastro;
            }
            case PRESA -> cazado = Math.max(1, p.restante);
            case FIN_CAZADOR -> {
                presa = -1;
                HUELLAS.clear();
                ultimaHuella = null;
            }
            case FIN_PRESA -> cazado = 0;
        }
    }

    public static void aura(AuraPacket p) {
        AURA.put(p.id, tic + p.ticks);
    }

    /** Si a este jugador le brillan los ojos (es Dream o está infectado). */
    public static boolean brilla(Player p) {
        Minecraft mc = Minecraft.getInstance();
        if (p == mc.player) return p.hasEffect(DreamRegistro.MORDISCO.get()) || p.hasEffect(DreamRegistro.INFECCION.get());
        Long t = AURA.get(p.getId());
        return t != null && t > tic;
    }

    public static boolean cazando() {
        return presa >= 0;
    }

    public static int presaId() {
        return presa;
    }

    public static int huellas() {
        return HUELLAS.size();
    }

    public static boolean cazado() {
        return cazado > 0;
    }

    /** Solo para la prueba del CI: marca a mano a alguien como Dream (los ojos). */
    public static void pruebaAura(int id) {
        AURA.put(id, tic + 100000);
    }

    private static void limpiar() {
        presa = -1;
        cazado = 0;
        HUELLAS.clear();
        ultimaHuella = null;
        AURA.clear();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        limpiar();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            if (presa >= 0 || !AURA.isEmpty()) limpiar();
            return;
        }
        if (mc.isPaused()) return;
        tic++;
        if (tic % 100 == 0) AURA.values().removeIf(t -> t <= tic);
        if (cazado > 0) cazado--;
        if (presa < 0) return;
        if (restante > 0 && --restante == 0) {
            presa = -1;
            HUELLAS.clear();
            return;
        }
        Entity v = mc.level.getEntity(presa);
        if (v != null) presaPos = v.position();
        long ahora = mc.level.getGameTime();
        // pisadas: cada ~0,9 bloques que avanza por el suelo
        if (rastro && v != null && !v.isPassenger()) {
            Vec3 pos = v.position();
            boolean suelo = !mc.level.getBlockState(BlockPos.containing(pos.x, pos.y - 0.2, pos.z)).isAir();
            if (suelo && (ultimaHuella == null || ultimaHuella.distanceToSqr(pos) > 0.8)) {
                Vec3 dir = ultimaHuella == null ? Vec3.directionFromRotation(0, v.getYRot()) : pos.subtract(ultimaHuella).multiply(1, 0, 1);
                Vec3 lado = dir.lengthSqr() < 1e-4 ? Vec3.ZERO : new Vec3(-dir.z, 0, dir.x).normalize().scale(pieIzq ? 0.14 : -0.14);
                pieIzq = !pieIzq;
                HUELLAS.addLast(new double[]{pos.x + lado.x, pos.y + 0.04, pos.z + lado.z, ahora});
                while (HUELLAS.size() > MAX_HUELLAS) HUELLAS.removeFirst();
                ultimaHuella = pos;
            }
        }
        while (!HUELLAS.isEmpty() && ahora - HUELLAS.peekFirst()[3] > VIDA_HUELLA) HUELLAS.removeFirst();
        // el brillo de las pisadas (solo las de cerca; las viejas, más apagadas y menos veces)
        if (tic % 4 == 0) {
            Vec3 yo = mc.player.position();
            for (double[] h : HUELLAS) {
                if (yo.distanceToSqr(h[0], h[1], h[2]) > 32 * 32) continue;
                long edad = ahora - (long) h[3];
                boolean vieja = edad > VIDA_HUELLA / 2;
                if (vieja && mc.level.random.nextInt(3) != 0) continue;
                mc.level.addParticle(vieja ? HUELLA_VIEJA : HUELLA, h[0], h[1], h[2], 0.0, 0.0, 0.0);
            }
        }
        // el latido de la presa, dentro de la cabeza
        if (latidoDist > 0) {
            double d = mc.player.position().distanceTo(presaPos);
            if (d <= latidoDist && v != null) {
                if (--proxLatido <= 0) {
                    float k = (float) Mth.clamp(d / latidoDist, 0.0, 1.0);
                    proxLatido = Math.round(Mth.lerp(k, 9.0F, 30.0F));
                    mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WARDEN_HEARTBEAT, 1.0F, 0.3F + 0.5F * (1.0F - k)));
                    pruebaLatidos++;
                }
            } else proxLatido = 0;
        }
    }

    // ------------------------------------------------------------------ HUD

    public static void hud(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.options.hideGui) return;
        if (cazado > 0) ojo(g, w, p.tickCount + pt);
        if (presa < 0) return;
        Entity v = mc.level.getEntity(presa);
        Vec3 obj = v != null ? v.getPosition(pt) : presaPos;
        Vec3 yo = p.getPosition(pt);
        double dx = obj.x - yo.x, dz = obj.z - yo.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 1.5) return;
        float yawObj = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        float rel = Mth.wrapDegrees(yawObj - mc.gameRenderer.getMainCamera().getYRot());
        // más visible cuanto más cerca (y late un poco)
        float cerca = 1.0F - (float) Mth.clamp(dist / 64.0, 0.0, 1.0);
        float a = (0.35F + 0.5F * cerca) * (0.85F + 0.15F * Mth.sin((p.tickCount + pt) * 0.4F));
        int al = Mth.clamp((int) (a * 255), 0, 255);
        int col = al << 24 | 0x7CFF4A, sombra = (al / 2) << 24 | 0x0A1A05;
        var ps = g.pose();
        ps.pushPose();
        ps.translate(w / 2.0F, h / 2.0F, 0.0F);
        ps.mulPose(Axis.ZP.rotationDegrees(rel));
        int r = 17;
        // una punta de flecha de 5 filas (la sombra, un píxel por debajo)
        for (int i = 0; i < 5; i++) {
            int y = -r - 5 + i;
            g.fill(-i - 1, y + 1, i + 2, y + 2, sombra);
        }
        for (int i = 0; i < 5; i++) {
            int y = -r - 5 + i;
            g.fill(-i, y, i + 1, y + 1, col);
        }
        ps.popPose();
        pruebaFlechas++;
    }

    /** El ojo que late arriba en el centro: te están cazando. */
    private static void ojo(GuiGraphics g, int w, float t) {
        float a = 0.55F + 0.35F * Mth.sin(t * 0.35F);
        int al = Mth.clamp((int) (a * 255), 0, 255);
        int verde = al << 24 | 0x8FE85A, oscuro = al << 24 | 0x0B1A06, fondo = (al * 2 / 5) << 24 | 0x000000;
        int x = w / 2 - 8, y = 4;
        int[][] filas = {{4, 11}, {2, 13}, {0, 15}, {0, 15}, {2, 13}, {4, 11}};
        for (int i = 0; i < filas.length; i++) g.fill(x + filas[i][0] - 1, y + i - 1, x + filas[i][1] + 1, y + i + 2, fondo);
        for (int i = 0; i < filas.length; i++) g.fill(x + filas[i][0], y + i, x + filas[i][1], y + i + 1, verde);
        // la pupila, una raja (de reptil)
        g.fill(x + 7, y, x + 8, y + 6, oscuro);
        g.fill(x + 6, y + 2, x + 9, y + 4, oscuro);
        pruebaOjo++;
    }
}
