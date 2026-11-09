package com.miscelanea.armasfuego.rifle;

import com.miscelanea.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Bala de pintura del rifle (16 colores, el del tinte con el que se fabrica): donde da pinta del todo los bloques
 * teñibles de alrededor (lana, alfombras, hormigón, hormigón en polvo, terracota y cristal tintado; el cristal y la
 * terracota sin teñir también) y a las ovejas las tiñe. Para pintar de lejos sin andamios.
 */
public final class Pintura {
    private Pintura() {}

    public static final String COLOR = "Color";
    private static final String[] SUFIJOS = {"wool", "carpet", "concrete", "concrete_powder", "terracotta", "stained_glass", "stained_glass_pane"};

    public static ItemStack bala(int color) {
        ItemStack s = new ItemStack(ModItems.BALA_PINTURA.get());
        s.getOrCreateTag().putInt(COLOR, color);
        return s;
    }

    public static int color(ItemStack s) {
        return s.hasTag() ? Math.max(0, Math.min(15, s.getTag().getInt(COLOR))) : 0;
    }

    public static int rgb(int color) {
        float[] c = DyeColor.byId(color).getTextureDiffuseColors();
        return 0xFF000000 | (int) (c[0] * 255) << 16 | (int) (c[1] * 255) << 8 | (int) (c[2] * 255);
    }

    public static DustParticleOptions polvo(int color) {
        float[] c = DyeColor.byId(color).getTextureDiffuseColors();
        return new DustParticleOptions(new Vector3f(c[0], c[1], c[2]), 1.6F);
    }

    /** El mismo bloque en el color pedido, o null si no se puede pintar. */
    public static BlockState tenido(BlockState st, int color) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(st.getBlock());
        if (!"minecraft".equals(id.getNamespace())) return null;
        String p = id.getPath(), nuevo = null;
        String c = DyeColor.byId(color).getName();
        if (p.equals("glass")) nuevo = c + "_stained_glass";
        else if (p.equals("glass_pane")) nuevo = c + "_stained_glass_pane";
        else if (p.equals("terracotta")) nuevo = c + "_terracotta";
        else for (DyeColor d : DyeColor.values()) {
            String pre = d.getName() + "_";
            if (!p.startsWith(pre)) continue;
            String suf = p.substring(pre.length());
            for (String s : SUFIJOS) if (s.equals(suf)) nuevo = c + "_" + suf;
            break;
        }
        if (nuevo == null || nuevo.equals(p)) return null;
        Block b = BuiltInRegistries.BLOCK.get(new ResourceLocation("minecraft", nuevo));
        if (b == Blocks.AIR) return null;
        return b.withPropertiesOf(st);
    }

    /** Pinta lo teñible en un radio de 1,5 alrededor de {@code centro}. Devuelve cuántos bloques ha pintado. */
    public static int pintar(ServerLevel l, BlockPos centro, int color, Vec3 donde) {
        int n = 0;
        for (BlockPos q : BlockPos.betweenClosed(centro.offset(-1, -1, -1), centro.offset(1, 1, 1))) {
            if (q.distSqr(centro) > 2.3) continue;
            BlockState st = l.getBlockState(q);
            BlockState nuevo = tenido(st, color);
            if (nuevo == null) continue;
            l.setBlock(q, nuevo, 3);
            n++;
        }
        l.sendParticles(polvo(color), donde.x, donde.y, donde.z, 24, 0.5, 0.5, 0.5, 0);
        return n;
    }
}
