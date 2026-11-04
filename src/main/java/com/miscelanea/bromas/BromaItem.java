package com.miscelanea.bromas;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Objeto de broma que se usa con clic derecho sobre otro jugador. Lo que hace cada broma está en {@link Bromas#aplicar}.
 * Comprueba la configuración (cada broma se puede desactivar), el enfriamiento de quien la gasta (el del juego, el que
 * se ve en la barra) y que la víctima no sea espectadora. El cartel y el espejo se gastan (salvo en creativo).
 */
public class BromaItem extends Item {
    public enum Broma {UWU, CARTEL, DUENDE, REVES}

    private final Broma broma;

    public BromaItem(Broma broma, Properties p) {
        super(p);
        this.broma = broma;
    }

    public Broma broma() {
        return broma;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack st, Player p, LivingEntity t, InteractionHand mano) {
        if (!(t instanceof Player) || t == p) return InteractionResult.PASS;
        if (p.level().isClientSide) return InteractionResult.SUCCESS;
        if (!(t instanceof ServerPlayer victima) || !(p instanceof ServerPlayer autor)) return InteractionResult.PASS;
        if (!Bromas.activa(broma)) {
            autor.displayClientMessage(Component.translatable("msg.miscelanea.broma.desactivada").withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.FAIL;
        }
        if (autor.getCooldowns().isOnCooldown(this)) return InteractionResult.FAIL;
        if (victima.isSpectator()) {
            autor.displayClientMessage(Component.translatable("msg.miscelanea.broma.inmune", victima.getDisplayName()).withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.FAIL;
        }
        if (!Bromas.aplicar(broma, victima, autor)) return InteractionResult.FAIL;
        int enfriar = com.miscelanea.ConfigServidor.bromaEnfriamientoTicks();
        if (enfriar > 0) autor.getCooldowns().addCooldown(this, enfriar);
        if ((broma == Broma.CARTEL || broma == Broma.REVES) && !autor.getAbilities().instabuild) st.shrink(1);
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isFoil(ItemStack st) {
        return broma == Broma.UWU || super.isFoil(st);
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> l, TooltipFlag f) {
        l.add(Component.translatable(getDescriptionId() + ".ayuda").withStyle(ChatFormatting.GRAY));
        l.add(Component.translatable(getDescriptionId() + ".ayuda2").withStyle(ChatFormatting.DARK_GRAY));
        l.add(Component.translatable("item.miscelanea.solo_creativo").withStyle(ChatFormatting.DARK_PURPLE));
    }
}
