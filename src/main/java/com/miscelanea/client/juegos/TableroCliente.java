package com.miscelanea.client.juegos;

import com.miscelanea.Miscelanea;
import com.miscelanea.juegos.TableroBlock;
import com.miscelanea.juegos.TableroBlockEntity;
import com.miscelanea.juegos.TableroClicPacket;
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

/** Apuntar a las casillas de los tableros con la mira y tocarlas con clic derecho. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class TableroCliente {
    public static TableroBlockEntity apuntado;
    public static int casilla = -1;

    private TableroCliente() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        apuntado = null;
        casilla = -1;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;
        for (TableroBlockEntity be : TableroBlockEntity.Visibles.SET)
            if (!be.isRemoved() && be.getLevel() == mc.level && be.getBlockPos().distToCenterSqr(p.position()) < 400)
                AvisoTurno.revisar(be, TableroRenderer.esMiTurno(be), "block.miscelanea.tablero");
        if (mc.screen != null) return;
        Vec3 ojo = p.getEyePosition();
        Vec3 dir = p.getViewVector(1F);
        double mejor = 5.0;
        double tope = mc.hitResult != null && mc.hitResult.getType() != HitResult.Type.MISS ? mc.hitResult.getLocation().distanceTo(ojo) : 99;
        for (TableroBlockEntity be : TableroBlockEntity.Visibles.SET) {
            if (be.isRemoved() || be.getLevel() != mc.level || be.getBlockPos().distToCenterSqr(ojo) > 64) continue;
            double yPlano = be.getBlockPos().getY() + 1.5 / 16;
            if (Math.abs(dir.y) < 1e-4) continue;
            double t = (yPlano - ojo.y) / dir.y;
            if (t <= 0 || t > mejor || t > tope + 0.15) continue;
            Vec3 h = ojo.add(dir.scale(t));
            Direction f = be.getBlockState().getValue(TableroBlock.FACING), der = f.getClockWise();
            double cx = be.getBlockPos().getX() + 0.5, cz = be.getBlockPos().getZ() + 0.5;
            double u = (h.x - cx) * der.getStepX() + (h.z - cz) * der.getStepZ();
            double v = (h.x - cx) * f.getStepX() + (h.z - cz) * f.getStepZ();
            double s = TableroBlockEntity.ZONA / 8;
            int col = (int) Math.floor(u / s + 4), fila = (int) Math.floor(v / s + 4);
            if (col < 0 || col > 7 || fila < 0 || fila > 7) continue;
            mejor = t;
            apuntado = be;
            casilla = fila * 8 + col;
        }
    }

    @SubscribeEvent
    public static void usar(InputEvent.InteractionKeyMappingTriggered e) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (!e.isUseItem() || apuntado == null || casilla < 0 || p == null || p.isShiftKeyDown()) return;
        ModNetwork.CHANNEL.sendToServer(new TableroClicPacket(apuntado.getBlockPos(), casilla));
        e.setCanceled(true);
        e.setSwingHand(true);
    }
}
