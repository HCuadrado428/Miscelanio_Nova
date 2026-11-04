package com.miscelanea.client.estatica;

import com.miscelanea.Miscelanea;
import com.miscelanea.estatica.Estatica;
import com.miscelanea.estatica.EstaticaRed;
import com.miscelanea.estatica.EstaticaRegistro;
import com.miscelanea.estatica.Poder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cliente del hombre de estática: quién lo es (lo manda el servidor), sus texturas de estática, el sonido de televisión
 * que se oye al acercarse, las gotas, los microcortes, el nombre que parpadea, esconderlo (dentro de una pantalla o
 * fundido en la oscuridad: solo los ojos), las antorchas que parpadean y los controles (sin armas, rueda de poderes y
 * clic derecho con la mano vacía para usar el poder elegido).
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class EstaticaCliente {
    private EstaticaCliente() {}

    static final ResourceLocation OJOS = new ResourceLocation(Miscelanea.MOD_ID, "textures/entity/estatica_ojos.png");

    static final class Info {
        int semilla, banderas;
        long visto;
    }

    private static final Map<Integer, Info> INFO = new HashMap<>();
    private static final Map<Integer, Mascara> MASCARAS = new HashMap<>();
    private static final Map<Integer, EstaticaSonidos.Bucle> BUCLES = new HashMap<>();
    @Nullable private static EstaticaSonidos.Propio propio;
    /** Poder elegido en la rueda. */
    static Poder elegido = Poder.NINGUNO;
    /** Microcorte del jugador local (hasta qué tick). */
    static long cortoHasta;
    private static final Set<Integer> EMPUJADOS = new HashSet<>();

    // ------------------------------------------------------------------ estado
    static void recibir(EstaticaRed.Estado p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (p.banderas == 0) {
            INFO.remove(p.id);
            return;
        }
        Info i = INFO.computeIfAbsent(p.id, k -> new Info());
        if (i.semilla != p.semilla) {
            Mascara m = MASCARAS.remove(p.id);
            if (m != null) m.cerrar();
        }
        i.semilla = p.semilla;
        i.banderas = p.banderas;
        i.visto = mc.level.getGameTime();
    }

    @Nullable
    static Info info(int id) {
        return INFO.isEmpty() ? null : INFO.get(id);
    }

    public static boolean es(int id) {
        return info(id) != null;
    }

    static boolean bandera(int id, int b) {
        Info i = info(id);
        return i != null && (i.banderas & b) != 0;
    }

    /** ¿El jugador local es de estática? (sus propios efectos sí los manda vanilla). */
    public static boolean yo() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.hasEffect(EstaticaRegistro.EFECTO.get());
    }

    /** Semilla de la disposición de alguien (de su transformación en curso o de su estado). */
    static int semilla(int id) {
        EstaticaCine.Activa a = EstaticaCine.de(id);
        if (a != null) return a.semilla;
        Info i = info(id);
        return i == null ? 0 : i.semilla;
    }

    /** ¿Se le dibuja la estática? (con el efecto o en plena transformación). */
    static boolean conEstatica(int id) {
        return EstaticaCine.activa(id) || es(id);
    }

    static Mascara mascara(int id) {
        int s = semilla(id);
        Mascara m = MASCARAS.get(id);
        if (m == null || m.semilla != s) {
            if (m != null) m.cerrar();
            m = new Mascara(id, s);
            MASCARAS.put(id, m);
        }
        var l = Minecraft.getInstance().level;
        m.usada = l == null ? 0 : l.getGameTime();
        return m;
    }

    // ------------------------------------------------------------------ gotas
    /** Gotas que caen de las partes de estática (puntos de la máscara llevados al mundo con el giro del cuerpo). */
    static void gotear(LivingEntity e, int n) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Mascara m = mascara(e.getId());
        if (m.puntos.isEmpty()) return;
        float cob = Float.isNaN(EstaticaCine.cobertura(e.getId(), 0)) ? 1F : EstaticaCine.cobertura(e.getId(), 0);
        RandomSource r = mc.level.getRandom();
        float yaw = e.yBodyRot * Mth.DEG_TO_RAD;
        Vec3 der = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw)), fr = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        double esc = 0.9375 / 16 * (e.isCrouching() ? 0.9 : 1);
        for (int i = 0; i < n * 3 && n > 0; i++) {
            float[] p = m.puntos.get(r.nextInt(m.puntos.size()));
            if (p[3] > cob) continue;
            n--;
            Vec3 w = e.position().add(der.scale(p[0] * esc)).add(0, p[1] * esc, 0).add(fr.scale(-p[2] * esc));
            mc.level.addParticle(EstaticaRegistro.GOTA.get(), w.x, w.y, w.z, r.nextGaussian() * 0.004, -0.01, r.nextGaussian() * 0.004);
        }
    }

    // ------------------------------------------------------------------ tick
    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            if (!INFO.isEmpty() || !MASCARAS.isEmpty()) limpiar();
            return;
        }
        if (mc.isPaused()) return;
        long t = mc.level.getGameTime();
        RandomSource r = mc.level.getRandom();
        // olvidar a quien no se ha refrescado (el servidor repite cada 2 s)
        INFO.entrySet().removeIf(en -> t - en.getValue().visto > 120 || t < en.getValue().visto - 20);
        for (Iterator<Map.Entry<Integer, Mascara>> it = MASCARAS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Mascara> en = it.next();
            if (t - en.getValue().usada > 200 && !conEstatica(en.getKey())) {
                en.getValue().cerrar();
                it.remove();
            }
        }
        // sonido de televisión de los demás, más fuerte cuanto más cerca
        for (Player o : mc.level.players()) {
            if (o == mc.player || !conEstatica(o.getId()) || o.distanceToSqr(mc.player) > 18 * 18) continue;
            EstaticaSonidos.Bucle b = BUCLES.get(o.getId());
            if (b == null || b.isStopped() || !mc.getSoundManager().isActive(b)) {
                b = new EstaticaSonidos.Bucle(o);
                BUCLES.put(o.getId(), b);
                mc.getSoundManager().play(b);
            }
        }
        BUCLES.values().removeIf(EstaticaSonidos.Bucle::isStopped);
        // el propio: un zumbido flojito
        boolean yo = yo();
        if (yo && (propio == null || propio.isStopped() || !mc.getSoundManager().isActive(propio))) {
            propio = new EstaticaSonidos.Propio();
            mc.getSoundManager().play(propio);
        }
        if (!yo && elegido != Poder.NINGUNO) elegido = Poder.NINGUNO;
        // gotas, microcortes y antorchas que parpadean
        for (Player o : mc.level.players()) {
            if (!conEstatica(o.getId()) || o.distanceToSqr(mc.player) > 32 * 32 || bandera(o.getId(), EstaticaRed.Estado.DENTRO)) continue;
            if (!EstaticaCine.activa(o.getId()) && r.nextFloat() < 0.3F && !bandera(o.getId(), EstaticaRed.Estado.FUNDIDO)) gotear(o, 1);
            if (t % 10 == (o.getId() & 7)) antorchas(mc, o, r);
        }
        if (yo && !EstaticaCine.localActiva() && t > cortoHasta + 60 && r.nextInt(260) == 0) {
            cortoHasta = t + 3 + r.nextInt(4);
            mc.level.playLocalSound(mc.player.getX(), mc.player.getEyeY(), mc.player.getZ(), EstaticaRegistro.CRUJIDO.get(), SoundSource.PLAYERS, 0.25F, 1.5F, false);
        }
        ImitarVoz.tick(yo && Poder.IMITAR.activo() && com.miscelanea.client.voz.VozCliente.hayVoicechat());
        // la rueda: mantener la tecla
        if (yo && mc.screen == null && EstaticaClienteMod.RUEDA.isDown() && !EstaticaCine.localActiva() && !PantallaCliente.dentro())
            mc.setScreen(new RuedaPoderes());
    }

    private static void antorchas(Minecraft mc, Player o, RandomSource r) {
        BlockPos c = o.blockPosition();
        int n = 0;
        for (BlockPos bp : BlockPos.betweenClosed(c.offset(-4, -2, -4), c.offset(4, 3, 4))) {
            if (!mc.level.getBlockState(bp).is(EstaticaRegistro.PARPADEAN)) continue;
            if (r.nextFloat() > 0.45F) continue;
            double x = bp.getX() + 0.5, y = bp.getY() + 0.7, z = bp.getZ() + 0.5;
            mc.level.addParticle(ParticleTypes.SMOKE, x + r.nextGaussian() * 0.05, y, z + r.nextGaussian() * 0.05, 0, 0.02, 0);
            mc.level.addParticle(EstaticaRegistro.CHISPA.get(), x + r.nextGaussian() * 0.15, y, z + r.nextGaussian() * 0.15, 0, 0, 0);
            if (n++ < 1 && r.nextInt(3) == 0) mc.level.playLocalSound(x, y, z, EstaticaRegistro.APAGON.get(), SoundSource.BLOCKS, 0.15F, 1.6F, false);
        }
    }

    private static void limpiar() {
        INFO.clear();
        for (Mascara m : MASCARAS.values()) m.cerrar();
        MASCARAS.clear();
        Mascara.olvidarPieles();
        BUCLES.clear();
        EMPUJADOS.clear();
        propio = null;
        elegido = Poder.NINGUNO;
    }

    @SubscribeEvent
    public static void salir(ClientPlayerNetworkEvent.LoggingOut e) {
        limpiar();
    }

    // ------------------------------------------------------------------ dibujo: esconder, fundido (solo ojos), microcortes
    /** Microcorte visible de alguien (los demás): unos ticks cada pocos segundos, al azar pero igual para todos. */
    static boolean corte(int id, long t) {
        long ventana = t / 50;
        return Mascara.hash(id, (int) ventana, 3, 991) < 0.35F && (t % 50) < 3;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void antes(RenderLivingEvent.Pre<?, ?> e) {
        LivingEntity le = e.getEntity();
        int id = le.getId();
        if (EfectosMundo.oculto(id)) {
            e.setCanceled(true);
            return;
        }
        if (!(le instanceof AbstractClientPlayer p) || !conEstatica(id)) return;
        if (bandera(id, EstaticaRed.Estado.DENTRO)) {
            e.setCanceled(true);
            return;
        }
        if (bandera(id, EstaticaRed.Estado.FUNDIDO) && !EstaticaCine.activa(id)) {
            e.setCanceled(true);
            soloOjos(p, e.getPoseStack(), e.getMultiBufferSource(), e.getPartialTick(), e.getRenderer().getModel());
            return;
        }
        var l = Minecraft.getInstance().level;
        if (l != null && corte(id, l.getGameTime())) {
            float h = Mascara.hash(id, (int) l.getGameTime(), 5, 13) - 0.5F;
            e.getPoseStack().pushPose();
            EMPUJADOS.add(id);
            e.getPoseStack().translate(h * 0.16, (Mascara.hash(id, (int) l.getGameTime(), 6, 13) - 0.5F) * 0.06, h * -0.1);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void despues(RenderLivingEvent.Post<?, ?> e) {
        if (!EMPUJADOS.isEmpty() && EMPUJADOS.remove(e.getEntity().getId())) e.getPoseStack().popPose();
    }

    /** Fundido en la oscuridad: solo los ojos blancos, flotando donde está la cabeza. */
    private static void soloOjos(AbstractClientPlayer p, PoseStack ps, net.minecraft.client.renderer.MultiBufferSource buf, float pt,
                                 net.minecraft.client.model.EntityModel<?> modelo) {
        if (!(modelo instanceof PlayerModel<?> pm)) return;
        float by = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot), hy = Mth.rotLerp(pt, p.yHeadRotO, p.yHeadRot), xr = Mth.lerp(pt, p.xRotO, p.getXRot());
        ModelPart cab = pm.head, gorro = pm.hat;
        float ox = cab.xRot, oy = cab.yRot, oz = cab.zRot, px = cab.x, py = cab.y, pz = cab.z;
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(180F - by));
        ps.scale(-1F, -1F, 1F);
        ps.scale(0.9375F, 0.9375F, 0.9375F);
        ps.translate(0, -1.501, 0);
        cab.setPos(0, p.isCrouching() ? 4.2F : 0F, 0);
        cab.xRot = xr * Mth.DEG_TO_RAD;
        cab.yRot = Mth.wrapDegrees(hy - by) * Mth.DEG_TO_RAD;
        cab.zRot = 0;
        float parp = 0.85F * EstaticaLayer.parpadeoOjos(p.getId(), p.tickCount + pt);
        Mascara mk = mascara(p.getId());
        mk.adaptar(p);
        var vc = buf.getBuffer(RenderType.eyes(mk.rlOjos));
        cab.render(ps, vc, 0xF000F0, OverlayTexture.NO_OVERLAY, parp, parp, parp, 1F);
        gorro.copyFrom(cab);
        gorro.render(ps, vc, 0xF000F0, OverlayTexture.NO_OVERLAY, parp * 0.6F, parp * 0.6F, parp * 0.6F, 1F);
        ps.popPose();
        cab.xRot = ox;
        cab.yRot = oy;
        cab.zRot = oz;
        cab.setPos(px, py, pz);
        gorro.copyFrom(cab);
    }

    /** En primera persona, el brazo también lleva su estática (y los ojos no se ven, claro). */
    private static boolean dibujandoBrazo;

    @SubscribeEvent
    public static void brazo(net.minecraftforge.client.event.RenderArmEvent e) {
        // renderRightHand/LeftHand vuelve a lanzar este mismo evento: sin esta guarda se llamaba a sí mismo sin fin
        // (StackOverflow → el juego se cerraba al ver el brazo en primera persona con la estática)
        if (dibujandoBrazo) return;
        AbstractClientPlayer p = e.getPlayer();
        if (!conEstatica(p.getId()) || !(Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(p)
                instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer pr)) return;
        e.setCanceled(true);
        boolean der = e.getArm() == net.minecraft.world.entity.HumanoidArm.RIGHT;
        dibujandoBrazo = true;
        try {
            if (der) pr.renderRightHand(e.getPoseStack(), e.getMultiBufferSource(), e.getPackedLight(), p);
            else pr.renderLeftHand(e.getPoseStack(), e.getMultiBufferSource(), e.getPackedLight(), p);
        } finally {
            dibujandoBrazo = false;
        }
        float cob = EstaticaCine.cobertura(p.getId(), 0F);
        Mascara mk = mascara(p.getId());
        mk.adaptar(p);
        mk.actualizar(Float.isNaN(cob) ? 1F : cob, net.minecraft.Util.getMillis() + p.getId() * 37L);
        PlayerModel<AbstractClientPlayer> m = pr.getModel();
        var vc = e.getMultiBufferSource().getBuffer(RenderType.entityTranslucent(mk.rl));
        ModelPart brazo = der ? m.rightArm : m.leftArm, manga = der ? m.rightSleeve : m.leftSleeve;
        brazo.render(e.getPoseStack(), vc, 0xF000F0, OverlayTexture.NO_OVERLAY);
        if (manga.visible) manga.render(e.getPoseStack(), vc, 0xF000F0, OverlayTexture.NO_OVERLAY);
    }

    /** El nombre parpadea: a ratos no sale y a ratos sale con letras cambiadas. */
    @SubscribeEvent
    public static void nombre(RenderNameTagEvent e) {
        Entity en = e.getEntity();
        if (!(en instanceof Player) || !conEstatica(en.getId())) return;
        var l = Minecraft.getInstance().level;
        if (l == null) return;
        long t = l.getGameTime();
        float h = Mascara.hash(en.getId(), (int) (t / 3), 9, 41);
        if (h < 0.18F || bandera(en.getId(), EstaticaRed.Estado.FUNDIDO)) e.setResult(Event.Result.DENY);
        else if (h < 0.5F) e.setContent(Component.literal(EstaticaHud.revolver(e.getContent().getString(), (int) (t / 3) + en.getId())));
    }

    /** Microcorte propio: la cámara da un tirón de un par de grados. */
    @SubscribeEvent
    public static void camara(ViewportEvent.ComputeCameraAngles e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level.getGameTime() > cortoHasta || !yo()) return;
        float k = Mascara.hash((int) mc.level.getGameTime(), 1, 2, 3) - 0.5F;
        e.setRoll(e.getRoll() + k * 4F);
        e.setYaw(e.getYaw() + k * 1.5F);
    }

    // ------------------------------------------------------------------ controles: sin armas y poderes con clic derecho
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void interaccion(InputEvent.InteractionKeyMappingTriggered e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !yo() || EstaticaCine.localActiva()) return;
        if (PantallaCliente.dentro()) {
            if (e.isAttack() || e.isUseItem()) PantallaCliente.confirmar();
            e.setCanceled(true);
            e.setSwingHand(false);
            return;
        }
        if ((e.isAttack() || e.isUseItem()) && Estatica.esArma(mc.player.getItemInHand(e.getHand()))) {
            e.setCanceled(true);
            e.setSwingHand(false);
            mc.player.displayClientMessage(Component.translatable("estatica.miscelanea.sin_armas"), true);
            return;
        }
        if (!e.isUseItem() || e.getHand() != InteractionHand.MAIN_HAND || !mc.player.getMainHandItem().isEmpty() || elegido == Poder.NINGUNO) return;
        if (usarPoder(mc, elegido)) {
            e.setCanceled(true);
            e.setSwingHand(true);
        }
    }

    private static boolean usarPoder(Minecraft mc, Poder pd) {
        HitResult hr = mc.hitResult;
        int obj = hr instanceof EntityHitResult eh ? eh.getEntity().getId() : -1;
        BlockPos bp = hr instanceof BlockHitResult bh && hr.getType() == HitResult.Type.BLOCK ? bh.getBlockPos() : BlockPos.ZERO;
        switch (pd) {
            case PANTALLA -> {
                if (bp == BlockPos.ZERO) {
                    mc.player.displayClientMessage(Component.translatable("estatica.miscelanea.no_pantalla"), true);
                    return true;
                }
            }
            case IMITAR -> {
                HitResult lejos = mc.player.pick(24, 0, false);
                BlockPos donde = lejos instanceof BlockHitResult bh && lejos.getType() == HitResult.Type.BLOCK ? bh.getBlockPos() : null;
                if (donde == null) {
                    mc.player.displayClientMessage(Component.translatable("estatica.miscelanea.imitar_bloque"), true);
                    return true;
                }
                ImitarVoz.usar(donde);
                return true;
            }
            case ARRASTRAR, ATRAER, LANZAR, ESTAMPAR -> {
                if (obj < 0) {
                    // el objetivo puede estar más lejos que el alcance de la mano: el más cercano en la mirada
                    Entity m = EfectosMundo.mirado(mc, pd == Poder.ARRASTRAR ? 5 : 12);
                    if (m != null) obj = m.getId();
                }
            }
            default -> {}
        }
        EstaticaRed.CANAL.sendToServer(new EstaticaRed.Usar(pd.ordinal(), bp, obj));
        return true;
    }

    /** Poderes que salen en la rueda (los activados en el servidor; Imitar solo con el voicechat). */
    static List<Poder> poderesRueda() {
        List<Poder> l = new ArrayList<>();
        for (Poder p : Poder.V) {
            if (p == Poder.NINGUNO || !p.activo()) continue;
            if (p == Poder.IMITAR && !com.miscelanea.client.voz.VozCliente.hayVoicechat()) continue;
            l.add(p);
        }
        return l;
    }
}
