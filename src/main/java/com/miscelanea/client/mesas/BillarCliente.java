package com.miscelanea.client.mesas;

import com.miscelanea.Miscelanea;
import com.miscelanea.mesas.BillarBlock;
import com.miscelanea.mesas.BillarBlockEntity;
import com.miscelanea.mesas.BillarPartida;
import com.miscelanea.mesas.BillarTiroPacket;
import com.miscelanea.mesas.MesaGeo;
import com.miscelanea.mesas.MesaGrandeBlock;
import com.miscelanea.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Apuntar y tirar en el billar: clic derecho en la mesa cuando es tu turno empieza a apuntar (miras al punto de la mesa
 * hacia el que quieres mandar la blanca) y la fuerza sube y baja sola; el segundo clic tira. Agachado se cancela.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class BillarCliente {
    public static BillarBlockEntity apuntando;
    public static long inicio;
    public static float angulo;
    private static final java.util.Map<BillarBlockEntity, Boolean> TURNO = new java.util.WeakHashMap<>();

    private BillarCliente() {}

    public static float fuerza(float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return 0;
        float t = mc.level.getGameTime() - inicio + pt;
        return (float) (1 - Math.cos(t * Math.PI / 40)) / 2 * 0.95F + 0.05F;
    }

    static boolean miTurno(BillarBlockEntity be, LocalPlayer p) {
        BillarPartida b = be.p;
        return b.fase == BillarPartida.APUNTA && !be.esCpu(b.turno) && p.getUUID().equals(be.jug[b.turno]);
    }

    @SubscribeEvent
    public static void clic(InputEvent.InteractionKeyMappingTriggered e) {
        if (!e.isUseItem()) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;
        if (apuntando != null) {
            if (miTurno(apuntando, p)) {
                ModNetwork.CHANNEL.sendToServer(new BillarTiroPacket(apuntando.getBlockPos(), angulo, fuerza(0)));
                p.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            }
            apuntando = null;
            e.setCanceled(true);
            e.setSwingHand(false);
            return;
        }
        if (!(mc.hitResult instanceof BlockHitResult bh) || bh.getType() != HitResult.Type.BLOCK || p.isShiftKeyDown()) return;
        BlockState s = mc.level.getBlockState(bh.getBlockPos());
        if (!(s.getBlock() instanceof BillarBlock bb)) return;
        BlockPos pr = bb.principal(s, bh.getBlockPos());
        if (mc.level.getBlockEntity(pr) instanceof BillarBlockEntity be && miTurno(be, p)) {
            apuntando = be;
            inicio = mc.level.getGameTime();
            e.setCanceled(true);
            e.setSwingHand(false);
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) {
            apuntando = null;
            return;
        }
        for (BillarBlockEntity be : com.miscelanea.mesas.Visibles.BILLAR) {
            if (be.isRemoved() || be.getLevel() != mc.level) continue;
            com.miscelanea.client.juegos.AvisoTurno.revisar(be, miTurno(be, p), "block.miscelanea.billar");
        }
        if (apuntando == null) return;
        BillarBlockEntity be = apuntando;
        if (be.isRemoved() || !miTurno(be, p) || p.isShiftKeyDown() || p.distanceToSqr(Vec3.atCenterOf(be.getBlockPos())) > 64) {
            apuntando = null;
            return;
        }
        angulo = calcularAngulo(be, p, 1F);
        int n = Math.round(fuerza(0) * 10);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) sb.append(i < n ? '▮' : '▯');
        p.displayClientMessage(Component.translatable("billar.miscelanea.fuerza", sb.toString()).withStyle(n > 7 ? ChatFormatting.RED : n > 4 ? ChatFormatting.GOLD : ChatFormatting.GREEN), true);
    }

    /** Dirección desde la blanca hasta el punto de la mesa al que mira el jugador. */
    public static float calcularAngulo(BillarBlockEntity be, LocalPlayer p, float pt) {
        BillarPartida b = be.p;
        Vec3 ojo = p.getEyePosition(pt), dir = p.getViewVector(pt);
        double y = be.getBlockPos().getY() + 12 / 16.0 + BillarPartida.R;
        var f = be.getBlockState().getValue(MesaGrandeBlock.FACING);
        double[] c;
        if (dir.y < -0.02) {
            double t = (y - ojo.y) / dir.y;
            c = MesaGeo.campo(be.getBlockPos(), f, ojo.add(dir.scale(t)));
        } else c = MesaGeo.campo(be.getBlockPos(), f, ojo.add(dir.scale(6)));
        return (float) Math.atan2(c[1] - b.y[0], c[0] - b.x[0]);
    }
}
