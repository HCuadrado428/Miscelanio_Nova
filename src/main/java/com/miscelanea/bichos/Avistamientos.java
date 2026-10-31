package com.miscelanea.bichos;

import com.miscelanea.Miscelanea;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/** Hace aparecer bichos alrededor de los jugadores según bioma, hora y tiempo (como en Animal Crossing). */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class Avistamientos {
    private static final int MAX_CERCA = 7;

    private Avistamientos() {}

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.getServer().getTickCount() % 30 != 0) return;
        for (ServerPlayer p : e.getServer().getPlayerList().getPlayers()) {
            if (p.isSpectator() || p.level().dimension() != Level.OVERWORLD) continue;
            ServerLevel l = p.serverLevel();
            int hay = l.getEntitiesOfClass(BichoEntity.class, p.getBoundingBox().inflate(36)).size();
            if (hay >= MAX_CERCA) continue;
            for (int intento = 0; intento < 3; intento++) {
                double a = p.getRandom().nextDouble() * Math.PI * 2, r = 8 + p.getRandom().nextDouble() * 18;
                int x = (int) Math.floor(p.getX() + Math.cos(a) * r), z = (int) Math.floor(p.getZ() + Math.sin(a) * r);
                if (!l.hasChunkAt(new BlockPos(x, 64, z))) continue;
                BlockPos suelo = l.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
                if (!l.getFluidState(suelo.below()).isEmpty() || !l.getBlockState(suelo.below()).isSolid()) continue;
                if (Math.abs(suelo.getY() - p.getY()) > 20) continue;
                List<Especie> posibles = new ArrayList<>();
                int total = 0;
                for (Especie s : Especie.values()) {
                    if (!s.esPez() && s.puede(l, suelo)) {
                        posibles.add(s);
                        total += s.peso;
                    }
                }
                if (total == 0) continue;
                int t = p.getRandom().nextInt(total);
                Especie elegida = posibles.get(0);
                for (Especie s : posibles) {
                    t -= s.peso;
                    if (t < 0) {
                        elegida = s;
                        break;
                    }
                }
                double y = suelo.getY() + (elegida.vuela() ? 0.8 + p.getRandom().nextDouble() * 2 : 0.05);
                l.addFreshEntity(BichoEntity.crear(l, elegida, x + 0.5, y, z + 0.5));
                break;
            }
        }
    }
}
