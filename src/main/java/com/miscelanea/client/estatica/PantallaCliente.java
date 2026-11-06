package com.miscelanea.client.estatica;

import com.miscelanea.Miscelanea;
import com.miscelanea.estatica.EstaticaRed;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.Input;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Dentro de las pantallas: la cámara mira desde la pantalla elegida (la rueda del ratón cambia de pantalla), clic para
 * salir por ella y Mayús para volver por donde se entró. Mientras, el cuerpo está escondido y quieto en el servidor.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class PantallaCliente {
    private PantallaCliente() {}

    @Nullable private static BlockPos entrada;
    private static final List<BlockPos> SALIDAS = new ArrayList<>();
    private static final List<Direction> CARAS = new ArrayList<>();
    private static int sel, maxTicks;
    private static long desde, cambio;
    private static boolean pedido;

    static void recibir(EstaticaRed.Pantallas p) {
        var l = Minecraft.getInstance().level;
        SALIDAS.clear();
        CARAS.clear();
        if (p.destinos.isEmpty() && p.maxTicks == 0) {
            entrada = null;
            return;
        }
        entrada = p.entrada;
        // 0 = la propia entrada; luego las demás
        SALIDAS.add(p.entrada);
        CARAS.add(cara(l, p.entrada));
        for (int i = 0; i < p.destinos.size(); i++) {
            SALIDAS.add(p.destinos.get(i));
            CARAS.add(Direction.from3DDataValue(p.caras[i]));
        }
        sel = SALIDAS.size() > 1 ? 1 : 0;
        maxTicks = p.maxTicks;
        desde = l == null ? 0 : l.getGameTime();
        cambio = desde;
        pedido = false;
    }

    private static Direction cara(@Nullable net.minecraft.world.level.Level l, BlockPos bp) {
        if (l == null) return Direction.NORTH;
        var s = l.getBlockState(bp);
        return s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)
                ? s.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING) : Direction.NORTH;
    }

    public static boolean dentro() {
        return entrada != null;
    }

    static void confirmar() {
        if (!dentro() || pedido) return;
        pedido = true;
        EstaticaRed.CANAL.sendToServer(new EstaticaRed.SalirPantalla(sel - 1));
    }

    @SubscribeEvent
    public static void salirMundo(ClientPlayerNetworkEvent.LoggingOut e) {
        entrada = null;
        SALIDAS.clear();
        CARAS.clear();
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void rueda(InputEvent.MouseScrollingEvent e) {
        if (!dentro() || SALIDAS.isEmpty()) return;
        e.setCanceled(true);
        int n = SALIDAS.size();
        sel = Math.floorMod(sel + (e.getScrollDelta() > 0 ? -1 : 1), n);
        // la cara se pasa a la pantalla nueva (la ven los de alrededor)
        EstaticaRed.CANAL.sendToServer(new EstaticaRed.VerPantalla(sel - 1));
        var l = Minecraft.getInstance().level;
        if (l != null) {
            cambio = l.getGameTime();
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    com.miscelanea.estatica.EstaticaRegistro.CRUJIDO.get(), 1.6F, 0.5F));
        }
    }

    @SubscribeEvent
    public static void movimiento(MovementInputUpdateEvent e) {
        if (!dentro()) return;
        Input in = e.getInput();
        boolean volver = in.shiftKeyDown;
        in.up = in.down = in.left = in.right = false;
        in.jumping = false;
        in.shiftKeyDown = false;
        in.forwardImpulse = 0;
        in.leftImpulse = 0;
        var l = Minecraft.getInstance().level;
        if (volver && !pedido && l != null && l.getGameTime() - desde > 10) {
            sel = 0;
            confirmar();
        }
    }

    @SubscribeEvent
    public static void mano(RenderHandEvent e) {
        if (dentro()) e.setCanceled(true);
    }

    /** CameraMixin: la cámara delante de la pantalla elegida, mirando hacia fuera. */
    @Nullable
    public static double[] colocarCamara(Entity entity, float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (!dentro() || entity != mc.player || SALIDAS.isEmpty()) return null;
        BlockPos bp = SALIDAS.get(Mth.clamp(sel, 0, SALIDAS.size() - 1));
        Direction f = CARAS.get(Mth.clamp(sel, 0, CARAS.size() - 1));
        Vec3 c = Vec3.atBottomCenterOf(bp).add(f.getStepX() * 0.62, 1.25, f.getStepZ() * 0.62);
        // la cabeza se puede mover un poco (mirar alrededor desde la pantalla)
        float dyaw = Mth.clamp(Mth.wrapDegrees(mc.player.getViewYRot(pt) - f.toYRot()), -55F, 55F);
        float pitch = Mth.clamp(mc.player.getViewXRot(pt), -40F, 40F);
        return new double[]{c.x, c.y, c.z, f.toYRot() + dyaw, pitch};
    }

    static void hud(GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (!dentro() || mc.level == null) return;
        long t = mc.level.getGameTime();
        // al cambiar de pantalla, un golpe de nieve
        float k = Mth.clamp(1F - (t - cambio + pt) / 8F, 0F, 1F);
        EstaticaHud.ruido(g, w, h, 0.18F + 0.7F * k);
        EstaticaHud.vhs(g, w, h, 0.6F, t, pt);
        EstaticaHud.borde(g, w, h, 0.9F);
        BlockPos bp = SALIDAS.get(Mth.clamp(sel, 0, SALIDAS.size() - 1));
        double d = mc.player == null ? 0 : Math.sqrt(mc.player.blockPosition().distSqr(bp));
        String nom = mc.level.getBlockState(bp).getBlock().getName().getString();
        Component l1 = sel == 0 ? Component.translatable("estatica.miscelanea.pantalla.entrada", nom)
                : Component.translatable("estatica.miscelanea.pantalla.salida", sel, SALIDAS.size() - 1, nom, Math.round(d));
        g.drawCenteredString(mc.font, l1, w / 2, h - 52, 0xFFE8E8E8);
        g.drawCenteredString(mc.font, Component.translatable("estatica.miscelanea.pantalla.ayuda"), w / 2, h - 40, 0xFFA8A8A8);
        float queda = Mth.clamp(1F - (t - desde + pt) / Math.max(1, maxTicks), 0F, 1F);
        int bw = 120;
        g.fill(w / 2 - bw / 2, h - 28, w / 2 + bw / 2, h - 25, 0x80000000);
        g.fill(w / 2 - bw / 2, h - 28, w / 2 - bw / 2 + Math.round(bw * queda), h - 25, 0xFFD8D8D8);
    }
}
