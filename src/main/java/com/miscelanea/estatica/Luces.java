package com.miscelanea.estatica;

import com.miscelanea.ConfigServidor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Apagar luces al pasar: las luces de la etiqueta miscelanea:apagables_estatica (lámparas de redstone, velas, hogueras, la
 * lámpara del mod...) que tengan "lit" se apagan (solo esa propiedad, sin avisar a los vecinos) y se apuntan; cuando ya
 * no queda nadie de estática cerca durante un rato, vuelven a encenderse si siguen siendo el mismo bloque y siguen
 * apagadas. Al parar el servidor se encienden todas. Las antorchas y faroles solo parpadean en el cliente.
 */
public final class Luces {
    private Luces() {}

    private static final class Apagada {
        final ResourceKey<Level> dim;
        final BlockPos pos;
        final BlockState original;
        long ultimo;

        Apagada(ResourceKey<Level> dim, BlockPos pos, BlockState original, long ultimo) {
            this.dim = dim;
            this.pos = pos;
            this.original = original;
            this.ultimo = ultimo;
        }
    }

    private static final Map<String, Apagada> APAGADAS = new HashMap<>();

    private static String clave(ResourceKey<Level> d, BlockPos p) {
        return d.location() + "|" + p.asLong();
    }

    static void apagarCerca(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        int r = ConfigServidor.estaticaLucesRadio();
        long t = l.getGameTime();
        BlockPos c = p.blockPosition();
        int n = 0;
        for (BlockPos bp : BlockPos.betweenClosed(c.offset(-r, -r, -r), c.offset(r, r + 1, r))) {
            String k = clave(l.dimension(), bp);
            Apagada a = APAGADAS.get(k);
            if (a != null) {
                a.ultimo = t;
                continue;
            }
            BlockState s = l.getBlockState(bp);
            if (!s.is(EstaticaRegistro.APAGABLES) || !s.hasProperty(BlockStateProperties.LIT) || !s.getValue(BlockStateProperties.LIT)) continue;
            BlockPos fija = bp.immutable();
            APAGADAS.put(k, new Apagada(l.dimension(), fija, s, t));
            l.setBlock(fija, s.setValue(BlockStateProperties.LIT, false), 2);
            if (n++ < 3) {
                Vec3 q = Vec3.atCenterOf(fija);
                Estatica.sonido(l, q, EstaticaRegistro.APAGON.get(), 0.6F, 0.9F + l.random.nextFloat() * 0.3F);
                l.sendParticles(ParticleTypes.SMOKE, q.x, q.y + 0.3, q.z, 6, 0.15, 0.1, 0.15, 0.01);
                l.sendParticles(EstaticaRegistro.CHISPA.get(), q.x, q.y, q.z, 5, 0.25, 0.25, 0.25, 0.0);
            }
        }
    }

    static void tick(MinecraftServer srv) {
        if (APAGADAS.isEmpty()) return;
        int vuelta = ConfigServidor.estaticaLucesVueltaTicks();
        for (Iterator<Apagada> it = APAGADAS.values().iterator(); it.hasNext(); ) {
            Apagada a = it.next();
            ServerLevel l = srv.getLevel(a.dim);
            if (l == null) {
                it.remove();
                continue;
            }
            long t = l.getGameTime();
            if (!l.isLoaded(a.pos)) {
                if (t - a.ultimo > 20L * 60 * 30) it.remove();   // media hora sin cargar: se olvida
                continue;
            }
            if (t - a.ultimo < vuelta) continue;
            encender(l, a);
            it.remove();
        }
    }

    private static void encender(ServerLevel l, Apagada a) {
        BlockState s = l.getBlockState(a.pos);
        if (s.getBlock() == a.original.getBlock() && s.hasProperty(BlockStateProperties.LIT) && !s.getValue(BlockStateProperties.LIT))
            l.setBlock(a.pos, s.setValue(BlockStateProperties.LIT, true), 3);
    }

    static void restaurarTodo(MinecraftServer srv) {
        for (Apagada a : APAGADAS.values()) {
            ServerLevel l = srv.getLevel(a.dim);
            if (l != null && l.isLoaded(a.pos)) encender(l, a);
        }
        APAGADAS.clear();
    }
}
