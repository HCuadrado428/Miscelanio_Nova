package com.miscelanea.rave;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/** Un altavoz grande: suma alcance a la mesa de DJ que tenga a menos de 24 bloques, y su cono late con la música. */
public class AltavozBlockEntity extends BlockEntity implements GeoBlockEntity {
    /** Altavoces cargados en el servidor (para que la mesa los cuente) y en el cliente (para que latan). */
    public static final Set<AltavozBlockEntity> SERVIDOR = Collections.newSetFromMap(new WeakHashMap<>());
    public static final Set<AltavozBlockEntity> CLIENTE = Collections.newSetFromMap(new WeakHashMap<>());
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public AltavozBlockEntity(BlockPos p, BlockState s) {
        super(RaveRegistro.ALTAVOZ_BE.get(), p, s);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null) (level.isClientSide ? CLIENTE : SERVIDOR).add(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        SERVIDOR.remove(this);
        CLIENTE.remove(this);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar c) {}

    /** El modelo ocupa tres bloques de alto. */
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).expandTowards(0, 2, 0).inflate(0.5);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
