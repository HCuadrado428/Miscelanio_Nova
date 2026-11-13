package com.miscelanea.espera.eventos;

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
 * Tique de turno de la Sala de Espera (por fin alguien te llama). Al canjearlo en el Espera te da un número y, a
 * los 3 segundos, suena el timbre del turno y apareces delante de la puerta de SALIDA más cercana. Se gasta. Fuera del
 * Espera no vale ("este tique es de otra ventanilla").
 */
public class TiqueTurnoItem extends Item {
    public TiqueTurnoItem(Properties p) {
        super(p);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level l, Player p, InteractionHand mano) {
        ItemStack st = p.getItemInHand(mano);
        if (l.isClientSide) return InteractionResultHolder.success(st);
        if (!(p instanceof ServerPlayer sp)) return InteractionResultHolder.pass(st);
        if (!canjear(sp)) return InteractionResultHolder.fail(st);
        if (!sp.getAbilities().instabuild) st.shrink(1);
        return InteractionResultHolder.consume(st);
    }

    /** Saca número (también para la prueba). false si no vale aquí o ya está esperando. */
    public static boolean canjear(ServerPlayer sp) {
        if (!Espera.en(sp.level())) {
            sp.displayClientMessage(Component.translatable("item.miscelanea.tique_turno.otra"), true);
            return false;
        }
        if (EsperaExtras.TURNOS.containsKey(sp.getUUID())) return false;
        int numero = 1 + sp.getRandom().nextInt(998);
        // el 404, de vez en cuando
        if (sp.getRandom().nextInt(4) == 0) numero = 404;
        EsperaExtras.TURNOS.put(sp.getUUID(), new int[]{sp.tickCount + 60, numero});
        sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.UI_STONECUTTER_TAKE_RESULT, SoundSource.PLAYERS, 0.6F, 1.6F);
        sp.displayClientMessage(Component.translatable("item.miscelanea.tique_turno.numero", String.format("A-%03d", numero)), true);
        return true;
    }

    /** Ya es su turno: el timbre y a la puerta. */
    static void llamar(ServerPlayer sp, int numero) {
        if (!Espera.en(sp.level())) return;
        var l = sp.serverLevel();
        var puerta = Salidas.cercana(l, sp.blockPosition(), true);
        if (puerta == null) {
            sp.displayClientMessage(Component.translatable("item.miscelanea.tique_turno.cerrado"), true);
            return;
        }
        double[] d = Salidas.delante(l, puerta);
        EsperaExtras.soltar(sp);
        sp.fallDistance = 0;
        sp.teleportTo(l, d[0], d[1], d[2], (float) d[3], 0);
        sp.setDeltaMovement(0, 0, 0);
        EsperaExtras.olvidarCamino(sp);
        l.playSound(null, d[0], d[1] + 2, d[2], SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.AMBIENT, 0.9F, 1.19F);
        sp.displayClientMessage(Component.translatable("item.miscelanea.tique_turno.llamada", String.format("A-%03d", numero)).withStyle(ChatFormatting.GREEN), true);
        Logros.dar(sp, "turno", "canjear");
        // el segundo tono del timbre, un poco después
        EsperaExtras.TIMBRES.put(sp.getUUID(), sp.tickCount + 9);
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level l, List<Component> lista, TooltipFlag f) {
        lista.add(Component.translatable("item.miscelanea.tique_turno.ayuda").withStyle(ChatFormatting.GRAY));
    }
}
