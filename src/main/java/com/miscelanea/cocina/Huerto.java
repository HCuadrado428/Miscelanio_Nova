package com.miscelanea.cocina;

import com.miscelanea.Miscelanea;
import com.miscelanea.registry.ModItems;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Al arrancar hierba a veces salen semillas de tomate o granos de arroz. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class Huerto {
    private Huerto() {}

    @SubscribeEvent
    public static void hierba(BlockEvent.BreakEvent e) {
        if (!(e.getLevel() instanceof Level l) || l.isClientSide || e.getPlayer().isCreative()) return;
        var s = e.getState();
        if (!s.is(Blocks.GRASS) && !s.is(Blocks.TALL_GRASS) && !s.is(Blocks.FERN)) return;
        if (e.getPlayer().getMainHandItem().is(Items.SHEARS)) return;
        float r = l.random.nextFloat();
        if (r < 0.05F) Block.popResource(l, e.getPos(), new ItemStack(ModItems.SEMILLAS_TOMATE.get()));
        else if (r < 0.10F) Block.popResource(l, e.getPos(), new ItemStack(ModItems.ARROZ.get()));
    }
}
