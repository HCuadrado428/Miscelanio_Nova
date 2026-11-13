package com.miscelanea.espera.eventos;

import com.miscelanea.espera.Paciente;
import com.miscelanea.espera.Espera;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Llave de la habitación 404 del Hotel del Videoclip, con su llavero de plástico. Al usarla en el Espera cuelgas el
 * cartel de "No molestar": durante 45 s los habitantes no te oyen (ni hablando por el chat de voz) y los que iban detrás
 * de ti te pierden. Cinco usos; entre uno y otro, 90 s. Fuera de allí, "esta habitación no está en este hotel".
 */
public class LlaveHabitacionItem extends Item {
    public static final int DURA = 900, ESPERA = 1800;

    public LlaveHabitacionItem(Properties p) {
        super(p);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level l, Player p, InteractionHand mano) {
        ItemStack st = p.getItemInHand(mano);
        if (l.isClientSide) return InteractionResultHolder.success(st);
        if (!(p instanceof ServerPlayer sp)) return InteractionResultHolder.pass(st);
        if (!colgar(sp)) return InteractionResultHolder.fail(st);
        st.hurtAndBreak(1, sp, q -> q.broadcastBreakEvent(mano));
        return InteractionResultHolder.consume(st);
    }

    /** Cuelga el cartel (también para la prueba). */
    public static boolean colgar(ServerPlayer sp) {
        if (!Espera.en(sp.level())) {
            sp.displayClientMessage(Component.translatable("item.miscelanea.llave_habitacion.otro"), true);
            return false;
        }
        var l = sp.serverLevel();
        EsperaExtras.ponerNoMolestar(sp, DURA);
        // los que te buscaban se quedan donde están y, al rato, se olvidan
        for (Paciente g : l.getEntitiesOfClass(Paciente.class, sp.getBoundingBox().inflate(40)))
            if (g.cazando()) g.oye(g.position(), false);
        l.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.PLAYERS, 0.5F, 1.5F);
        l.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 0.6F, 1.8F);
        sp.getCooldowns().addCooldown(ExtrasRegistro.LLAVE.get(), ESPERA);
        sp.displayClientMessage(Component.translatable("item.miscelanea.llave_habitacion.cartel").withStyle(ChatFormatting.GOLD), true);
        Logros.dar(sp, "no_molestar", "colgar");
        return true;
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level l, List<Component> lista, TooltipFlag f) {
        lista.add(Component.translatable("item.miscelanea.llave_habitacion.ayuda").withStyle(ChatFormatting.GRAY));
    }
}
