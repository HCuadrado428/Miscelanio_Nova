package com.miscelanea.cocina;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.CropBlock;

import java.util.function.Supplier;

/** Cultivo del huerto (tomatera, arroz): como el trigo, con su propia semilla. */
public class Cultivo extends CropBlock {
    private final Supplier<Item> semilla;

    public Cultivo(Properties p, Supplier<Item> semilla) {
        super(p);
        this.semilla = semilla;
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return semilla.get();
    }
}
