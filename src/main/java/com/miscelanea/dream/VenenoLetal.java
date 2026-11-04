package com.miscelanea.dream;

import com.miscelanea.Miscelanea;
import com.miscelanea.tortura.aparatos.Aparatos;
import com.miscelanea.tortura.muertes.Muertes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.ThrowablePotionItem;
import net.minecraft.world.item.UseAnim;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * El veneno de Dream mata a quien se lo bebe. El frasco lleva la marca {@code VenenoLetal} (la pone
 * {@link VenenoDreamItem#asegurar} y el caldero de mezclas la pasa a los frascos que salen de una mezcla con veneno), y
 * beber CUALQUIER poción con esa marca mata:
 * <ul>
 *   <li>con su cinemática de muerte (TipoMuerte.VENENO: tose, se agarra el cuello, cae de rodillas, convulsiona y se
 *       desploma boca abajo) si {@code muertes_animadas} está activo; si no, en el acto;</li>
 *   <li>al final, con el daño {@code miscelanea:veneno_dream} (atraviesa la armadura, muerte instantánea con
 *       Incapacitated);</li>
 *   <li>con un tótem de la inmortalidad en la mano, el tótem salta y le salva (sin cinemática), como en los aparatos;</li>
 *   <li>en creativo (o espectador) no muere: solo un aviso y los efectos cortos del veneno.</li>
 * </ul>
 * Lanzado (frascos arrojadizos con veneno) no mata: solo salpica los efectos del mordisco.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class VenenoLetal {
    private VenenoLetal() {}

    public static final String TAG = "VenenoLetal";
    public static final ResourceKey<DamageType> DANO = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(Miscelanea.MOD_ID, "veneno_dream"));

    /** ¿Lleva veneno de Dream? (el propio veneno, o un frasco con la marca). */
    public static boolean es(ItemStack st) {
        if (st.isEmpty()) return false;
        if (st.getItem() instanceof VenenoDreamItem) return true;
        CompoundTag t = st.getTag();
        return t != null && t.getBoolean(TAG);
    }

    public static void marcar(ItemStack st) {
        st.getOrCreateTag().putBoolean(TAG, true);
    }

    /** Al terminar de beber (la copia del objeto antes de beberlo: aún tiene la marca). */
    @SubscribeEvent
    public static void alBeber(LivingEntityUseItemEvent.Finish e) {
        LivingEntity v = e.getEntity();
        if (v.level().isClientSide) return;
        ItemStack st = e.getItem();
        if (!(st.getItem() instanceof PotionItem) || st.getItem() instanceof ThrowablePotionItem) return;
        if (st.getUseAnimation() != UseAnim.DRINK || !es(st) || !(v.level() instanceof ServerLevel sl)) return;
        // justo después (cuando el juego ya ha devuelto la botella vacía a la mano)
        var srv = sl.getServer();
        srv.tell(new TickTask(srv.getTickCount(), () -> beber(v)));
    }

    /** Se ha bebido el veneno. */
    public static void beber(LivingEntity v) {
        if (!(v.level() instanceof ServerLevel) || !v.isAlive()) return;
        if (v instanceof Player p) {
            if (p.isSpectator()) return;
            if (p.getAbilities().invulnerable) {
                // creativo: se queda en los efectos cortos del mordisco
                p.displayClientMessage(Component.translatable("miscelanea.veneno.creativo"), true);
                return;
            }
        }
        if (v.getItemInHand(InteractionHand.MAIN_HAND).is(Items.TOTEM_OF_UNDYING) || v.getItemInHand(InteractionHand.OFF_HAND).is(Items.TOTEM_OF_UNDYING)) {
            // el tótem le salva (salta como con cualquier muerte); sin cinemática
            v.invulnerableTime = 0;
            v.hurt(Aparatos.fuente(v.level(), DANO, null), Float.MAX_VALUE);
            return;
        }
        if (Muertes.intentar(v, DANO, null)) {
            // la cinemática se ve limpia: fuera las náuseas y la lentitud (que cambia el campo de visión)
            v.removeEffect(MobEffects.CONFUSION);
            v.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            v.removeEffect(MobEffects.WEAKNESS);
            v.removeEffect(DreamRegistro.DESANGRADO.get());
            return;
        }
        Aparatos.matarYa(v, DANO, null);
    }
}
