package com.miscelanea.client.tortura;

import com.miscelanea.Miscelanea;
import com.miscelanea.client.tortura.qte.Dibujo;
import com.miscelanea.tortura.Lazo;
import com.miscelanea.tortura.LazoItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Set;

/**
 * Cliente del lazo: el aro girando en primera persona y la mano que lo voltea, las posturas de los mobs tumbados
 * (90º, de lado), a hombros o atravesados en un caballo, el bloqueo del movimiento del derribado / maniatado y el aviso
 * en pantalla. La cuerda del tira y afloja la dibuja {@link TorturaCliente}; las cuerdas sobre el cuerpo,
 * {@link TorturaLayer}; la postura de los jugadores, {@link AnimLazo}.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class LazoCliente {
    private LazoCliente() {}

    /** Entre AFTER_SKY y AFTER_ENTITIES: se están dibujando las entidades del mundo (no las de un inventario). */
    private static boolean enMundo;
    /** Entidades a las que se les ha hecho pushPose en el Pre (se deshace en el Post). */
    private static final Set<Integer> EMPUJADOS = new HashSet<>();

    // ------------------------------------------------------------------ consultas
    /** ¿Está volteando el lazo (y no tirando de alguien)? */
    public static boolean girando(LivingEntity e) {
        return e instanceof Player && LazoItem.usando(e) && TorturaCliente.lazo(e.getId()) == 0 && !TorturaCliente.esCaptorTirando(e.getId());
    }

    public static HumanoidArm brazoDelLazo(LivingEntity e) {
        HumanoidArm principal = e.getMainArm();
        return e.getUsedItemHand() == InteractionHand.MAIN_HAND ? principal : principal.getOpposite();
    }

    public static float carga(LivingEntity e, float pt) {
        return Mth.clamp((e.getTicksUsingItem() + pt) / Lazo.CARGA_TICKS, 0F, 1F);
    }

    /** Ángulo del aro (radianes): arranca despacio y acelera con la carga. */
    public static float anguloGiro(LivingEntity e, float pt) {
        float s = e.getTicksUsingItem() + pt;
        return s * (0.42F + 0.3F * Math.min(1F, s / Lazo.CARGA_TICKS));
    }

    /** ¿Lleva a alguien maniatado a hombros? */
    public static boolean cargando(Entity p) {
        for (Entity e : p.getPassengers()) if (TorturaCliente.lazo(e.getId()) == Lazo.MANIATADO) return true;
        return false;
    }

    /** Durante el tira y afloja (con su minijuego), clic derecho con el lazo sigue sirviendo para tirar. */
    public static boolean puedeTirar(InteractionHand mano) {
        LocalPlayer p = Minecraft.getInstance().player;
        return p != null && TorturaCliente.esCaptorTirando(p.getId()) && p.getItemInHand(mano).getItem() instanceof LazoItem;
    }

    /** Igual que Lazo.interactuar / Lazo.caballo del servidor, con el estado sincronizado. */
    public static boolean interactuaria(Player p, LivingEntity t, ItemStack st, InteractionHand mano) {
        if (TorturaCliente.lazo(p.getId()) != 0) return false;
        if (t instanceof AbstractHorse caballo && TorturaCliente.lazo(caballo.getId()) == 0) {
            if (mano != InteractionHand.MAIN_HAND) return false;
            if (cargando(p)) return true;
            if (caballo.getPassengers().isEmpty()) return false;
            for (Entity e : caballo.getPassengers()) if (TorturaCliente.lazo(e.getId()) != Lazo.MANIATADO) return false;
            if (p.isShiftKeyDown()) return st.isEmpty() && p.getPassengers().isEmpty();
            return caballo.isTamed() && !p.isPassenger();
        }
        return Lazo.interactuaria(p, TorturaCliente.lazo(t.getId()), t, st, mano);
    }

    // ------------------------------------------------------------------ primera persona
    /** Transformación del lazo en la mano en primera persona (IClientItemExtensions). */
    public static boolean manoPrimeraPersona(PoseStack ps, LocalPlayer p, HumanoidArm brazo, ItemStack st, float pt, float equip) {
        boolean tirando = TorturaCliente.esCaptorTirando(p.getId());
        boolean usa = LazoItem.usando(p) && brazoDelLazo(p) == brazo;
        if (!usa && !tirando) return false;
        int i = brazo == HumanoidArm.RIGHT ? 1 : -1;
        ps.translate(i * 0.56F, -0.52F + equip * -0.6F, -0.72F);
        float t = p.tickCount + pt;
        if (tirando) {
            // tirando de la cuerda: la mano hacia el pecho, a tirones
            float k = usa ? 1F : 0.35F;
            float tiron = Math.max(0F, Mth.sin(t * 0.55F)) * k;
            ps.translate(-i * 0.14F, -0.06F + Mth.sin(t * 2.1F) * 0.012F, 0.12F + tiron * 0.14F);
            ps.mulPose(Axis.XP.rotationDegrees(-20F * k - tiron * 12F));
            ps.mulPose(Axis.YP.rotationDegrees(i * 15F));
            return true;
        }
        // volteándolo: la mano sube y describe círculos
        float s = p.getTicksUsingItem() + pt;
        float k = Math.min(1F, s / 6F);
        k = k * k * (3 - 2 * k);
        float a = anguloGiro(p, pt);
        ps.translate(-i * 0.1F * k + Mth.cos(a) * 0.07F * k, 0.3F * k + Mth.sin(a * 2F) * 0.025F * k, -0.05F * k + Mth.sin(a) * 0.07F * k);
        ps.mulPose(Axis.ZP.rotationDegrees(i * 18F * k));
        ps.mulPose(Axis.XP.rotationDegrees(-28F * k));
        ps.mulPose(Axis.YP.rotationDegrees(-a * Mth.RAD_TO_DEG * 0.15F * k));
        return true;
    }

    @SubscribeEvent
    public static void nivel(RenderLevelStageEvent e) {
        if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            enMundo = true;
            EMPUJADOS.clear();
            return;
        }
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        enMundo = false;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || !mc.options.getCameraType().isFirstPerson() || !girando(p)) return;
        // el aro girando por encima, delante y algo a la derecha (en tercera persona lo dibuja TorturaLayer)
        float pt = e.getPartialTick();
        Vec3 cam = e.getCamera().getPosition();
        Vec3 mira = p.getViewVector(pt);
        Vec3 h = new Vec3(mira.x, 0, mira.z);
        h = h.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : h.normalize();
        int i = brazoDelLazo(p) == HumanoidArm.RIGHT ? 1 : -1;
        Vec3 der = new Vec3(-h.z, 0, h.x).scale(i);
        float carga = carga(p, pt), r = 0.42F + 0.22F * carga, a = anguloGiro(p, pt);
        Vec3 ojo = p.getEyePosition(pt);
        Vec3 c = ojo.add(h.scale(1.0)).add(der.scale(0.3)).add(0, 0.5, 0).add(Math.cos(a) * 0.06, 0, Math.sin(a) * 0.06);
        Vec3 mano = TorturaCliente.manoMundo(p, pt);
        if (i < 0) mano = mano.subtract(der.scale(-0.84));   // manoMundo da la derecha: la izquierda es el espejo
        PoseStack ps = e.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        int luz = LevelRenderer.getLightColor(mc.level, BlockPos.containing(c));
        Vector3f vc = rel(c, cam), vm = rel(mano, cam);
        Vector3f u = new Vector3f(1, Mth.sin(a * 0.5F) * 0.12F, 0).normalize(), w = new Vector3f(0, Mth.cos(a * 0.5F) * 0.12F, 1).normalize();
        Cuerdas.anillo(ps, buffers.getBuffer(Cuerdas.TIPO), vc, u, w, r, r, luz, 0.045F, a);
        Vector3f honda = new Vector3f(vc).add(new Vector3f(u).mul(Mth.cos(a) * r)).add(new Vector3f(w).mul(Mth.sin(a) * r));
        Cuerdas.dibujar(ps, buffers.getBuffer(Cuerdas.TIPO), vm, honda, new Vector3f(0, -1, 0), 0.03F, luz, 0.04F, a * 0.3F);
        buffers.endBatch(Cuerdas.TIPO);
    }

    private static Vector3f rel(Vec3 v, Vec3 cam) {
        return new Vector3f((float) (v.x - cam.x), (float) (v.y - cam.y), (float) (v.z - cam.z));
    }

    // ------------------------------------------------------------------ posturas (mobs tumbados, a hombros, en el caballo)
    private static Vec3 frente(float yaw) {
        float y = yaw * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(y), 0, Mth.cos(y));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void antes(RenderLivingEvent.Pre<?, ?> e) {
        if (!enMundo) return;
        LivingEntity le = e.getEntity();
        int est = TorturaCliente.lazo(le.getId());
        if (est < Lazo.DERRIBADO) return;
        float pt = e.getPartialTick();
        boolean jugador = le instanceof Player;
        Entity veh = le.getVehicle();
        Vec3 destino = null;
        float yaw;
        if (est == Lazo.MANIATADO && veh instanceof Player h) {
            // a hombros: los jugadores boca abajo con la cabeza a la espalda; los mobs de lado, atravesados
            float hy = TorturaCliente.yawCuerpo(h, pt);
            yaw = jugador ? hy + 180F : hy;
            Vec3 der = new Vec3(-Mth.cos(hy * Mth.DEG_TO_RAD), 0, -Mth.sin(hy * Mth.DEG_TO_RAD));
            destino = h.getPosition(pt).add(der.scale(jugador ? 0.22 : 0.0)).add(0, h.getBbHeight() * (jugador ? 0.7 : 0.78), 0);
            // en primera persona, más a la espalda: que no tape la vista (las piernas asoman por abajo)
            Minecraft mc = Minecraft.getInstance();
            if (h == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson()) destino = destino.add(frente(hy).scale(-0.6));
        } else if (est == Lazo.MANIATADO && veh instanceof AbstractHorse c) {
            // atravesado en el lomo, detrás de la silla
            float cy = TorturaCliente.yawCuerpo(c, pt);
            yaw = jugador ? cy + 90F : cy;
            destino = c.getPosition(pt).add(frente(cy).scale(-0.42)).add(0, c.getBbHeight() * 0.82, 0);
        } else if (!jugador) {
            yaw = TorturaCliente.yawCuerpo(le, pt);
        } else return;   // jugador tumbado en el suelo: lo hace todo AnimLazo
        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        EMPUJADOS.add(le.getId());
        if (destino != null) {
            Vec3 pos = le.getPosition(pt);
            ps.translate(destino.x - pos.x, destino.y - pos.y, destino.z - pos.z);
            le.yBodyRot = le.yBodyRotO = yaw;
            le.yHeadRot = le.yHeadRotO = yaw;
        }
        if (!jugador) {
            // de lado: 90º sobre su eje delantero, apoyado en el suelo y centrado donde está. Se aplica dentro del giro
            // del cuerpo (R·M·R⁻¹) porque el renderizador gira al mob después.
            float w = le.getBbWidth(), hh = le.getBbHeight(), piv = w * 0.5F;
            float giro = 180F - yaw;
            ps.mulPose(Axis.YP.rotationDegrees(giro));
            ps.translate(Math.max(0F, hh * 0.5F - piv), piv, 0);
            ps.mulPose(Axis.ZP.rotationDegrees(90F));
            ps.translate(0, -piv, 0);
            ps.mulPose(Axis.YP.rotationDegrees(-giro));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void despues(RenderLivingEvent.Post<?, ?> e) {
        if (EMPUJADOS.remove(e.getEntity().getId())) e.getPoseStack().popPose();
    }

    // ------------------------------------------------------------------ sin moverse
    @SubscribeEvent
    public static void movimiento(MovementInputUpdateEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || TorturaCliente.lazo(mc.player.getId()) < Lazo.DERRIBADO) return;
        Input in = e.getInput();
        in.up = in.down = in.left = in.right = false;
        in.jumping = false;
        in.shiftKeyDown = false;   // ni bajarse del hombro / del caballo
        in.forwardImpulse = 0;
        in.leftImpulse = 0;
    }

    @SubscribeEvent
    public static void salir(ClientPlayerNetworkEvent.LoggingOut e) {
        EMPUJADOS.clear();
        enMundo = false;
    }

    // ------------------------------------------------------------------ aviso en pantalla
    public static void hud(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        if (p == null || mc.options.hideGui) return;
        TorturaCliente.Estado est = TorturaCliente.estado(p.getId());
        Component c = null;
        int borde = 0xFF8A5A20;
        if (est != null && est.lazo == Lazo.ENLAZADO) {
            c = Component.translatable("hud.miscelanea.lazo.enlazado");
            borde = 0xFF8A2020;
        } else if (est != null && est.lazo == Lazo.DERRIBADO) {
            c = Component.translatable("hud.miscelanea.lazo.derribado", (est.lazoT + 19) / 20);
            borde = 0xFF8A2020;
        } else if (est != null && est.lazo == Lazo.MANIATADO) {
            int seg = (est.lazoT + 19) / 20;
            int forcejeo = (est.lazoT % Lazo.ESCAPE_CADA + 19) / 20;
            c = Component.translatable("hud.miscelanea.lazo.maniatado", String.format("%d:%02d", seg / 60, seg % 60), forcejeo);
            borde = 0xFF8A2020;
        } else if (TorturaCliente.esCaptorTirando(p.getId())) {
            c = Component.translatable("hud.miscelanea.lazo.tirando", mc.options.keyUse.getTranslatedKeyMessage(), mc.options.keyJump.getTranslatedKeyMessage());
        } else if (cargando(p)) {
            c = Component.translatable("hud.miscelanea.lazo.cargando", mc.options.keyShift.getTranslatedKeyMessage());
        }
        if (c == null) return;
        int y = h - 86;
        int tw = mc.font.width(c);
        g.fill(w / 2 - tw / 2 - 6, y - 3, w / 2 + tw / 2 + 6, y + 11, 0x90000000);
        g.fill(w / 2 - tw / 2 - 6, y + 11, w / 2 + tw / 2 + 6, y + 12, borde);
        Dibujo.texto(g, mc.font, c, w / 2F, y, 1F, 0xFFF2E2C8, true);
    }
}
