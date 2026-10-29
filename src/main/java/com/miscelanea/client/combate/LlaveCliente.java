package com.miscelanea.client.combate;

import com.miscelanea.Miscelanea;
import com.miscelanea.llave.AccionLlave;
import com.miscelanea.llave.AccionPacket;
import com.miscelanea.llave.LlaveEspadaItem;
import com.miscelanea.llave.Magia;
import com.miscelanea.network.ModNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.Comparator;

/**
 * Controles y movimientos de Sora con la Llave Espada (lado cliente): combos con auto-apuntado al enemigo cercano,
 * Slide Dash, combo aéreo flotando, Strike Raid, menú de comandos (magia y especiales) y fijar objetivo.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class LlaveCliente {
    public static final String CATEGORIA = "key.categories." + Miscelanea.MOD_ID;
    public static final KeyMapping COMANDO = new KeyMapping("key.miscelanea.comando", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORIA);
    public static final KeyMapping CAMBIAR = new KeyMapping("key.miscelanea.cambiar_comando", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, CATEGORIA);
    public static final KeyMapping FIJAR = new KeyMapping("key.miscelanea.fijar", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORIA);

    public static final AnimJugador.Estilo ESTILO_ANIM = new AnimJugador.Estilo() {
        public AnimClip reposo() { return ClipsLlave.IDLE; }
        public AnimClip correr() { return ClipsLlave.CORRER; }
        public AnimClip aire() { return ClipsLlave.AIRE; }
    };

    private static int etapa, comando;
    private static long ultimoGolpe, libreEn;
    private static boolean pendiente;
    private static AccionLlave accion;
    private static long inicioAccion;
    private static LivingEntity objetivoAccion;
    private static Vec3 dirAccion = Vec3.ZERO;

    private LlaveCliente() {}

    /** Engancha la llave a la cámara y a las animaciones (desde la preparación del cliente). */
    public static void init() {
        var anim = AnimJugador.ESTILO;
        AnimJugador.ESTILO = p -> p.getMainHandItem().getItem() instanceof LlaveEspadaItem ? ESTILO_ANIM : anim.apply(p);
        var cam = CamaraCombate.ESTILO;
        CamaraCombate.ESTILO = p -> p.getMainHandItem().getItem() instanceof LlaveEspadaItem ? CamaraCombate.KINGDOM_HEARTS : cam.apply(p);
    }

    private static boolean lleva(LocalPlayer p) {
        return p != null && p.getMainHandItem().getItem() instanceof LlaveEspadaItem;
    }

    public static AnimClip clip(AccionLlave a) {
        return switch (a) {
            case COMBO1 -> ClipsLlave.COMBO1;
            case COMBO2 -> ClipsLlave.COMBO2;
            case GUARD_BREAK -> ClipsLlave.GUARD_BREAK;
            case EXPLOSION -> ClipsLlave.EXPLOSION;
            case AEREO1 -> ClipsLlave.AEREO1;
            case AEREO2 -> ClipsLlave.AEREO2;
            case AEREO_REMATE -> ClipsLlave.AEREO_REMATE;
            case EMBESTIDA -> ClipsLlave.EMBESTIDA;
            case STRIKE_RAID -> ClipsLlave.STRIKE_RAID;
            case PIRO, HIELO -> ClipsLlave.HECHIZO;
            case CURA -> ClipsLlave.CURA;
            case SONIC_BLADE -> ClipsLlave.SONIC_BLADE;
            case ARS_ARCANUM -> ClipsLlave.ARS_ARCANUM;
        };
    }

    /** Animación de otro jugador (llega del servidor). */
    public static void animDeOtro(int id, int accion) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getEntity(id) instanceof AbstractClientPlayer p) || p == mc.player) return;
        AccionLlave a = AccionLlave.de(accion);
        AnimJugador.reproducir(p, clip(a), a.aereo);
        if (a == AccionLlave.SONIC_BLADE) sonicOtros = p;
    }

    private static AbstractClientPlayer sonicOtros;

    // ------------------------------------------------------------------------------------------------ entrada

    @SubscribeEvent
    public static void clic(InputEvent.InteractionKeyMappingTriggered e) {
        var mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (!lleva(p)) return;
        if (e.isAttack()) {
            e.setCanceled(true);
            e.setSwingHand(false);
            atacar(p);
        } else if (e.isUseItem()) {
            // sobre un bloque (cofre, puerta...): se deja a vanilla para abrir cerraduras
            if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK) return;
            e.setCanceled(true);
            e.setSwingHand(false);
            if (!LlaveEspadaItem.lanzada(p.getMainHandItem())) hacer(p, AccionLlave.STRIKE_RAID, objetivo(p, 14));
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (sonicOtros != null && !AnimJugador.ocupado(sonicOtros)) sonicOtros = null;
        if (!lleva(p)) {
            accion = null;
            // las mismas teclas las usa el Hacha Leviatán: solo se vacían si no llevas ninguna de las dos
            if (p != null && !(p.getMainHandItem().getItem() instanceof com.miscelanea.hacha.HachaItem)) {
                while (COMANDO.consumeClick()) {}
                while (CAMBIAR.consumeClick()) {}
                while (FIJAR.consumeClick()) {}
            }
            return;
        }
        revisarTiempos(p);
        while (CAMBIAR.consumeClick()) comando = (comando + 1) % AccionLlave.COMANDOS.length;
        while (FIJAR.consumeClick()) alternarFijado(p);
        while (COMANDO.consumeClick()) {
            AccionLlave a = AccionLlave.COMANDOS[comando];
            if (Magia.cliente < a.coste && !p.getAbilities().instabuild) {
                p.displayClientMessage(Component.translatable("msg.miscelanea.sin_pm"), true);
                continue;
            }
            if (p.tickCount < libreEn) continue;
            boolean especial = a == AccionLlave.SONIC_BLADE || a == AccionLlave.ARS_ARCANUM;
            if (especial && LlaveEspadaItem.lanzada(p.getMainHandItem())) continue;
            hacer(p, a, objetivo(p, especial ? 9 : 16));
        }
        if (pendiente && p.tickCount >= libreEn) {
            pendiente = false;
            atacar(p);
        }
        moverDuranteAccion(p);
    }

    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();

    /** Si el jugador es nuevo (respawn, otro mundo) su tickCount vuelve a 0: los tiempos guardados ya no valen. */
    private static void revisarTiempos(LocalPlayer p) {
        if (libreEn > p.tickCount + 100 || ultimoGolpe > p.tickCount) {
            libreEn = 0;
            ultimoGolpe = 0;
            accion = null;
        }
    }

    private static void atacar(LocalPlayer p) {
        revisarTiempos(p);
        if (LlaveEspadaItem.lanzada(p.getMainHandItem())) return;
        if (p.tickCount < libreEn) {
            if (libreEn - p.tickCount <= 5) pendiente = true;   // se guarda el clic para encadenar
            return;
        }
        if (p.tickCount - ultimoGolpe > 18) etapa = 0;
        LivingEntity obj = objetivo(p, 7.5);
        boolean aire = !p.onGround() && !p.isInWater() && !p.onClimbable();
        AccionLlave a;
        if (aire) a = etapa == 0 ? AccionLlave.AEREO1 : etapa == 1 ? AccionLlave.AEREO2 : AccionLlave.AEREO_REMATE;
        else if (etapa == 0 && obj != null && obj.distanceTo(p) > 3.2) a = AccionLlave.EMBESTIDA;
        else if (etapa < 2) a = etapa == 0 ? AccionLlave.COMBO1 : AccionLlave.COMBO2;
        else a = cercanos(p, 4.5) >= 2 ? AccionLlave.EXPLOSION : AccionLlave.GUARD_BREAK;
        etapa = (etapa + 1) % 3;
        ultimoGolpe = p.tickCount;
        hacer(p, a, obj);
    }

    private static void hacer(LocalPlayer p, AccionLlave a, LivingEntity obj) {
        float yaw = obj != null ? (float) (Mth.atan2(obj.getZ() - p.getZ(), obj.getX() - p.getX()) * Mth.RAD_TO_DEG) - 90F : CamaraCombate.yaw();
        p.setYRot(yaw);
        p.yBodyRot = yaw;
        p.yHeadRot = yaw;
        CamaraCombate.bloquear(a.recuperacion + 2, a == AccionLlave.EMBESTIDA || a == AccionLlave.SONIC_BLADE ? 0 : a.recuperacion);
        AnimJugador.reproducir(p, clip(a), a.aereo);
        accion = a;
        inicioAccion = p.tickCount;
        objetivoAccion = obj;
        dirAccion = Vec3.directionFromRotation(0, yaw);
        libreEn = p.tickCount + a.recuperacion;
        ModNetwork.CHANNEL.sendToServer(new AccionPacket(a, yaw, obj != null ? obj.getId() : -1));
        LOG.info("[Llave] accion {} enviada (capa de animacion encontrada: {})", a, AnimJugador.de(p) != null);
    }

    /** Movimiento del propio jugador durante algunas acciones (lo mueve su cliente). */
    private static void moverDuranteAccion(LocalPlayer p) {
        if (accion == null) return;
        long t = p.tickCount - inicioAccion;
        Vec3 v = p.getDeltaMovement();
        switch (accion) {
            case EMBESTIDA -> {
                if (t <= 5) {
                    Vec3 d = hacia(p, objetivoAccion, dirAccion);
                    double dist = objetivoAccion != null ? objetivoAccion.distanceTo(p) : 9;
                    if (dist > 2.2) p.setDeltaMovement(d.x * 1.0, v.y, d.z * 1.0);
                }
            }
            case SONIC_BLADE -> {
                long fase = t % 7;
                if (t < 28 && fase <= 4) {
                    Vec3 d = hacia(p, objetivoAccion, dirAccion);
                    if (fase == 0) {
                        // cada embestida va a por el objetivo y lo atraviesa
                        dirAccion = d;
                        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90F;
                        p.setYRot(yaw);
                        p.yBodyRot = yaw;
                        if (t > 0) AnimJugador.reproducir(p, ClipsLlave.SONIC_BLADE, false);
                    }
                    p.setDeltaMovement(dirAccion.x * 1.25, Math.max(v.y, 0.0), dirAccion.z * 1.25);
                    p.resetFallDistance();
                }
            }
            case ARS_ARCANUM -> {
                if (t < 30) p.setDeltaMovement(dirAccion.x * 0.08, v.y, dirAccion.z * 0.08);
            }
            case GUARD_BREAK -> {
                if (t >= 4 && t <= 5) p.setDeltaMovement(dirAccion.x * 0.55, v.y, dirAccion.z * 0.55);
            }
            case AEREO1, AEREO2, AEREO_REMATE -> {
                // Sora se queda flotando mientras encadena golpes en el aire
                if (t < accion.recuperacion + 2) p.setDeltaMovement(v.x * 0.5, Math.max(v.y, accion == AccionLlave.AEREO_REMATE && t > 8 ? -0.6 : 0.02), v.z * 0.5);
                p.resetFallDistance();
            }
            default -> {}
        }
        if (t > 45) accion = null;
    }

    private static Vec3 hacia(LocalPlayer p, LivingEntity obj, Vec3 por) {
        if (obj == null || !obj.isAlive()) return por;
        Vec3 d = obj.position().subtract(p.position());
        d = new Vec3(d.x, 0, d.z);
        return d.lengthSqr() < 1e-4 ? por : d.normalize();
    }

    // ------------------------------------------------------------------------------------------------ objetivos

    private static boolean valido(LocalPlayer p, LivingEntity e) {
        return e != p && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand) && !e.isInvisible();
    }

    /** Enemigo al que apuntar: el fijado, o el más cercano delante de la cámara. */
    static LivingEntity objetivo(LocalPlayer p, double rango) {
        LivingEntity f = CamaraCombate.fijado();
        if (f != null && f.distanceTo(p) <= rango + 4) return f;
        Vec3 delante = Vec3.directionFromRotation(0, CamaraCombate.yaw());
        return p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(rango), e -> valido(p, e) && e.distanceTo(p) <= rango)
                .stream().filter(e -> {
                    Vec3 d = e.position().subtract(p.position());
                    Vec3 h = new Vec3(d.x, 0, d.z);
                    return h.lengthSqr() < 4 || h.normalize().dot(delante) > 0.2;
                })
                .min(Comparator.comparingDouble(e -> {
                    Vec3 d = e.position().subtract(p.position());
                    Vec3 h = new Vec3(d.x, 0, d.z);
                    double ang = h.lengthSqr() < 1e-4 ? 0 : Math.acos(Mth.clamp(h.normalize().dot(delante), -1, 1));
                    return e.distanceTo(p) + ang * 3;
                })).orElse(null);
    }

    private static int cercanos(LocalPlayer p, double r) {
        return p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r), e -> valido(p, e) && e.distanceTo(p) <= r).size();
    }

    private static void alternarFijado(LocalPlayer p) {
        if (CamaraCombate.fijado() != null) {
            CamaraCombate.fijar(null);
            return;
        }
        CamaraCombate.fijar(objetivo(p, 18));
    }

    // ------------------------------------------------------------------------------------------------ HUD (estilo KH)

    public static void hud(ForgeGui gui, GuiGraphics g, float partial, int w, int h) {
        var mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (!lleva(p) || mc.options.hideGui) return;
        var f = mc.font;
        // Menú de comandos (abajo a la izquierda)
        int x = 8, y = h - 16 - AccionLlave.COMANDOS.length * 13 - 16;
        g.fill(x - 2, y - 2, x + 118, y + 12, 0xC0101830);
        g.drawString(f, Component.translatable("hud.miscelanea.comandos"), x + 3, y + 1, 0xFFE8D9A0, false);
        y += 15;
        for (int i = 0; i < AccionLlave.COMANDOS.length; i++) {
            AccionLlave a = AccionLlave.COMANDOS[i];
            boolean sel = i == comando;
            boolean puede = Magia.cliente >= a.coste || p.getAbilities().instabuild;
            int fondo = sel ? 0xE02C6CC8 : 0xB0142040;
            g.fill(x, y, x + 116, y + 11, fondo);
            if (sel) g.fill(x, y, x + 116, y + 1, 0xFFA8D8FF);
            String nombre = Component.translatable("comando.miscelanea." + a.name().toLowerCase()).getString();
            g.drawString(f, (sel ? "▶ " : "  ") + nombre, x + 3, y + 2, puede ? 0xFFFFFFFF : 0xFF707888, false);
            g.drawString(f, String.valueOf((int) a.coste), x + 104 - f.width(String.valueOf((int) a.coste)), y + 2, puede ? 0xFF8FD4FF : 0xFF506070, false);
            y += 13;
        }
        // Barra de PM (abajo a la derecha, como en KH)
        float pm = Magia.cliente / Magia.MAX;
        int bw = 110, bx = w - bw - 10, by = h - 22;
        g.fill(bx - 2, by - 2, bx + bw + 2, by + 9, 0xC0101830);
        g.fill(bx, by, bx + (int) (bw * pm), by + 7, 0xFF2F7CFF);
        g.fill(bx, by, bx + (int) (bw * pm), by + 2, 0xFF8FC4FF);
        g.drawString(f, "PM " + (int) Magia.cliente, bx, by - 11, 0xFF8FC4FF, true);
    }
}
