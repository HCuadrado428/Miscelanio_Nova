package com.miscelanea.client.cuatro;

import com.miscelanea.Miscelanea;
import com.miscelanea.client.juegos.AvisoTurno;
import com.miscelanea.cuatro.CuatroBlock;
import com.miscelanea.cuatro.CuatroBlockEntity;
import com.miscelanea.cuatro.CuatroClicPacket;
import com.miscelanea.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Apuntar a una columna del cuatro en raya (desde cualquiera de los dos lados) y soltar con clic derecho. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class CuatroCliente {
    public static CuatroBlockEntity apuntado;
    public static int columna = -1;

    private CuatroCliente() {}

    public static boolean esMiTurno(CuatroBlockEntity be) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || be.resultado != 0) return false;
        java.util.UUID u = mc.player.getUUID();
        if (be.j.turno == 1) return be.rojo == null || u.equals(be.rojo);
        return !be.contraCpu() && (be.amarillo == null ? !u.equals(be.rojo) : u.equals(be.amarillo));
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        apuntado = null;
        columna = -1;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;
        for (CuatroBlockEntity be : CuatroBlockEntity.Visibles.SET)
            if (!be.isRemoved() && be.getLevel() == mc.level && be.getBlockPos().distToCenterSqr(p.position()) < 400)
                AvisoTurno.revisar(be, be.rojo != null && esMiTurno(be) && be.j.jugadas > 0, "block.miscelanea.cuatro");
        if (mc.screen != null) return;
        Vec3 ojo = p.getEyePosition(), dir = p.getViewVector(1F);
        double mejor = 5.5;
        double tope = mc.hitResult != null && mc.hitResult.getType() != HitResult.Type.MISS ? mc.hitResult.getLocation().distanceTo(ojo) + 0.4 : 99;
        for (CuatroBlockEntity be : CuatroBlockEntity.Visibles.SET) {
            if (be.isRemoved() || be.getLevel() != mc.level || be.getBlockPos().distToCenterSqr(ojo) > 64) continue;
            Direction f = be.getBlockState().getValue(CuatroBlock.FACING), der = f.getClockWise();
            Vec3 n = new Vec3(f.getStepX(), 0, f.getStepZ());
            double dn = dir.dot(n);
            if (Math.abs(dn) < 1e-4) continue;
            Vec3 c = Vec3.atBottomCenterOf(be.getBlockPos());
            double t = c.subtract(ojo).dot(n) / dn;
            if (t <= 0 || t > mejor || t > tope) continue;
            Vec3 h = ojo.add(dir.scale(t));
            double x = (h.x - c.x) * der.getStepX() * 16 + (h.z - c.z) * der.getStepZ() * 16 + 8;
            double y = (h.y - c.y) * 16;
            if (y < 0 || y > 27 || x < CuatroBlockEntity.X0 - 1 || x > CuatroBlockEntity.X0 + 7 * CuatroBlockEntity.CELDA + 1) continue;
            int col = (int) Math.floor((x - CuatroBlockEntity.X0) / CuatroBlockEntity.CELDA);
            mejor = t;
            apuntado = be;
            columna = Math.max(0, Math.min(6, col));
        }
    }

    @SubscribeEvent
    public static void usar(InputEvent.InteractionKeyMappingTriggered e) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (!e.isUseItem() || apuntado == null || columna < 0 || p == null || p.isShiftKeyDown()) return;
        ModNetwork.CHANNEL.sendToServer(new CuatroClicPacket(apuntado.getBlockPos(), columna));
        e.setCanceled(true);
        e.setSwingHand(true);
    }
}
