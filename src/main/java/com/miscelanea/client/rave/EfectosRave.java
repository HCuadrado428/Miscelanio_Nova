package com.miscelanea.client.rave;

import com.miscelanea.muebles.Muebles;
import com.miscelanea.rave.ConfetiOpciones;
import com.miscelanea.rave.HumoBlock;
import com.miscelanea.rave.HumoOpciones;
import com.miscelanea.rave.LucesRave;
import com.miscelanea.rave.Neon;
import com.miscelanea.rave.RaveRegistro;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Lo que echan en el cliente la máquina de humo, la fuente de chispas y el cañón de confeti. */
public final class EfectosRave {
    private EfectosRave() {}

    private static final int[] CANTIDAD = {1, 2, 4, 7};

    public static void tick(Level l, BlockPos p, BlockState s) {
        RandomSource r = l.random;
        int modo = s.getValue(LucesRave.MODO);
        if (s.getBlock() instanceof HumoBlock h && h.chispas()) {
            // fuente de chispas: un chorro hacia arriba (más alto con más modo)
            float fuerza = 0.35F + modo * 0.1F;
            for (int i = 0; i < 3 + modo * 3; i++)
                l.addParticle(RaveRegistro.CHISPA_PARTICULA.get(), p.getX() + 0.5, p.getY() + 0.6, p.getZ() + 0.5,
                        (r.nextDouble() - 0.5) * 0.07, fuerza * (0.8 + r.nextDouble() * 0.4), (r.nextDouble() - 0.5) * 0.07);
            if (r.nextInt(12) == 0) l.playLocalSound(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, SoundEvents.FIREWORK_ROCKET_TWINKLE_FAR,
                    SoundSource.BLOCKS, 0.25F, 1.6F + r.nextFloat() * 0.3F, false);
            return;
        }
        // máquina de humo: bocanadas por la boquilla de delante, cada vez más con el modo
        if (l.getGameTime() % 2 != 0) return;
        Direction d = s.getValue(HumoBlock.FACING);
        int color = Neon.rgb(s.getValue(Muebles.COLOR));
        double bx = p.getX() + 0.5 + d.getStepX() * 0.55, by = p.getY() + 0.3, bz = p.getZ() + 0.5 + d.getStepZ() * 0.55;
        for (int i = 0; i < CANTIDAD[modo]; i++) {
            double v = 0.12 + r.nextDouble() * 0.12;
            l.addParticle(new HumoOpciones(color), bx, by, bz, d.getStepX() * v + (r.nextDouble() - 0.5) * 0.05, 0.005 + r.nextDouble() * 0.015,
                    d.getStepZ() * v + (r.nextDouble() - 0.5) * 0.05);
        }
        if (r.nextInt(60) == 0) l.playLocalSound(bx, by, bz, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.25F, 1.4F, false);
    }

    /** Disparo del cañón: una nube de confeti hacia donde apunta. */
    public static void confeti(Level l, BlockPos p, Direction d, DyeColor color) {
        RandomSource r = l.random;
        double ox = p.getX() + 0.5 + d.getStepX() * 0.8, oy = p.getY() + 0.5 + d.getStepY() * 0.8, oz = p.getZ() + 0.5 + d.getStepZ() * 0.8;
        DyeColor[] todos = DyeColor.values();
        for (int i = 0; i < 180; i++) {
            double v = 0.5 + r.nextDouble() * 0.6;
            double vx = d.getStepX() * v + (r.nextDouble() - 0.5) * 0.45, vy = d.getStepY() * v + (r.nextDouble() - 0.5) * 0.45 + 0.1,
                    vz = d.getStepZ() * v + (r.nextDouble() - 0.5) * 0.45;
            int c;
            if (color == DyeColor.WHITE) c = Neon.rgb(todos[r.nextInt(todos.length)]);
            else c = r.nextInt(5) == 0 ? 0xFFD54A : Neon.rgb(color);
            l.addParticle(new ConfetiOpciones(c), ox, oy, oz, vx, vy, vz);
        }
        for (int i = 0; i < 25; i++)
            l.addParticle(RaveRegistro.CHISPA_PARTICULA.get(), ox, oy, oz, d.getStepX() * 0.4 + (r.nextDouble() - 0.5) * 0.3,
                    d.getStepY() * 0.4 + (r.nextDouble() - 0.5) * 0.3, d.getStepZ() * 0.4 + (r.nextDouble() - 0.5) * 0.3);
    }
}
