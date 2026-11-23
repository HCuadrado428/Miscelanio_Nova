package com.miscelanea.rave;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/** El foco móvil (modelo de GeckoLib; el barrido lo pone el modelo según la hora del mundo, ver LucesRave). */
public class FocoBlockEntity extends BlockEntity implements GeoBlockEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public FocoBlockEntity(BlockPos p, BlockState s) {
        super(RaveRegistro.FOCO_BE.get(), p, s);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar c) {}

    /** Se dibuja aunque el bloque no esté a la vista (la luz que echa llega lejos). */
    @Override
    public AABB getRenderBoundingBox() {
        return INFINITE_EXTENT_AABB;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
