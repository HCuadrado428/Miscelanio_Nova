package com.miscelanea.armasfuego.gatling;

import com.miscelanea.armasfuego.comun.EquilibrioArmas;
import com.miscelanea.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

/**
 * La Gatling de manivela plegada, para llevarla a cuestas (pesa: no se dispara en la mano). Clic derecho encima de un
 * bloque la planta en su trípode mirando hacia donde miras. Guarda el calor y las balas que le queden.
 */
public class GatlingItem extends Item {
    public static final String CALOR = "Calor", BALAS = "Balas";

    public GatlingItem(Properties p) {
        super(p);
    }

    @Override
    public InteractionResult useOn(UseOnContext c) {
        if (c.getClickedFace() != Direction.UP) return InteractionResult.PASS;
        Level l = c.getLevel();
        Vec3 q = c.getClickLocation();
        GatlingEntity g = ModEntities.GATLING.get().create(l);
        if (g == null) return InteractionResult.FAIL;
        Player p = c.getPlayer();
        float yaw = p != null ? p.getYRot() : 0;
        g.moveTo(q.x, q.y, q.z, yaw, 0);
        if (!l.noCollision(g, g.getBoundingBox())) return InteractionResult.FAIL;
        if (!l.isClientSide) {
            g.desde(c.getItemInHand());
            l.addFreshEntity(g);
            l.playSound(null, q.x, q.y, q.z, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.4F, 1.4F);
            l.gameEvent(p, GameEvent.ENTITY_PLACE, q);
            if (p == null || !p.getAbilities().instabuild) c.getItemInHand().shrink(1);
        }
        return InteractionResult.sidedSuccess(l.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> l, TooltipFlag f) {
        int balas = s.hasTag() ? s.getTag().getInt(BALAS) : 0;
        float calor = s.hasTag() ? s.getTag().getFloat(CALOR) : 0;
        l.add(Component.translatable("item.miscelanea.gatling.balas", balas, EquilibrioArmas.gatlingCinta()).withStyle(ChatFormatting.GOLD));
        if (calor > 1) l.add(Component.translatable("item.miscelanea.gatling.calor", Math.round(calor)).withStyle(ChatFormatting.RED));
        for (int i = 1; i <= 3; i++) l.add(Component.translatable("item.miscelanea.gatling.ayuda" + i).withStyle(ChatFormatting.GRAY));
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public net.minecraft.client.model.HumanoidModel.ArmPose getArmPose(net.minecraft.world.entity.LivingEntity e, net.minecraft.world.InteractionHand h, ItemStack s) {
                return net.minecraft.client.model.HumanoidModel.ArmPose.CROSSBOW_HOLD;
            }
        });
    }
}
