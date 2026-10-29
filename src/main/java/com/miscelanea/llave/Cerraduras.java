package com.miscelanea.llave;

import com.miscelanea.Miscelanea;
import com.miscelanea.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * La Llave Espada abre cerraduras: rayo de luz de la punta al bloque y campanita.
 * - Puertas y trampillas de hierro: se abren/cierran con la mano.
 * - Cofres y barriles: la primera vez que los abres con la llave sueltan un pequeño tesoro.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class Cerraduras {
    private static final String ABIERTO = "MiscelaneaCerradura";

    private Cerraduras() {}

    @SubscribeEvent
    public static void usar(PlayerInteractEvent.RightClickBlock e) {
        if (e.getLevel().isClientSide || !(e.getItemStack().getItem() instanceof LlaveEspadaItem)) return;
        ServerLevel sl = (ServerLevel) e.getLevel();
        BlockPos pos = e.getPos();
        BlockState st = sl.getBlockState(pos);
        boolean cerradura = false;
        if (st.is(Blocks.IRON_DOOR) && st.getBlock() instanceof DoorBlock puerta) {
            puerta.setOpen(e.getEntity(), sl, st, pos, !st.getValue(DoorBlock.OPEN));
            cerradura = true;
            e.setCanceled(true);
        } else if (st.is(Blocks.IRON_TRAPDOOR)) {
            boolean abre = !st.getValue(TrapDoorBlock.OPEN);
            sl.setBlock(pos, st.setValue(TrapDoorBlock.OPEN, abre), Block.UPDATE_ALL);
            sl.playSound(null, pos, abre ? SoundEvents.IRON_TRAPDOOR_OPEN : SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 1F, 1F);
            cerradura = true;
            e.setCanceled(true);
        } else {
            BlockEntity be = sl.getBlockEntity(pos);
            if (be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity) {
                cerradura = true;
                if (!be.getPersistentData().getBoolean(ABIERTO)) {
                    be.getPersistentData().putBoolean(ABIERTO, true);
                    be.setChanged();
                    tesoro(sl, pos);
                }
            }
        }
        if (!cerradura) return;
        // rayo de luz desde la punta de la llave hasta la cerradura
        var p = e.getEntity();
        Vec3 a = p.getEyePosition().add(p.getViewVector(1).scale(1.2)).add(0, -0.3, 0);
        Vec3 b = Vec3.atCenterOf(pos);
        for (int i = 0; i <= 16; i++) {
            Vec3 q = a.lerp(b, i / 16.0);
            sl.sendParticles(ParticleTypes.END_ROD, q.x, q.y, q.z, 1, 0, 0, 0, 0);
        }
        sl.sendParticles(ParticleTypes.FLASH, b.x, b.y, b.z, 1, 0, 0, 0, 0);
        sl.playSound(null, pos, ModSounds.CERRADURA.get(), SoundSource.PLAYERS, 1F, 1F);
    }

    private static void tesoro(ServerLevel sl, BlockPos pos) {
        var r = sl.getRandom();
        BlockPos arriba = pos.above();
        Block.popResource(sl, arriba, new ItemStack(Items.GOLD_NUGGET, 3 + r.nextInt(6)));
        if (r.nextFloat() < 0.6F) Block.popResource(sl, arriba, new ItemStack(Items.EMERALD, 1 + r.nextInt(2)));
        if (r.nextFloat() < 0.3F) Block.popResource(sl, arriba, new ItemStack(Items.LAPIS_LAZULI, 2 + r.nextInt(4)));
        if (r.nextFloat() < 0.15F) Block.popResource(sl, arriba, new ItemStack(Items.DIAMOND));
        sl.addFreshEntity(new ExperienceOrb(sl, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 8 + r.nextInt(8)));
        sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 20, 0.3, 0.3, 0.3, 0.2);
    }
}
