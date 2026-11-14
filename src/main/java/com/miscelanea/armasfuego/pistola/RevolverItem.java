package com.miscelanea.armasfuego.pistola;

import com.miscelanea.registry.ModEnchantments;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

/**
 * Revólver de ruleta de cobre. Controles (con el revólver en la mano principal):
 * <ul>
 *   <li>Clic derecho: dispara la recámara que toca (mirando al suelo del todo, te apuntas a la sien: ruleta rusa).</li>
 *   <li>Agachado + clic derecho con munición en la otra mano: la mete en la siguiente recámara vacía.</li>
 *   <li>Agachado + clic derecho con un panal / un hacha en la otra mano: encerar / rascar el óxido.</li>
 *   <li>Agachado + clic derecho (sin nada de eso) con Abanico: vacía el tambor muy deprisa.</li>
 *   <li>Agachado + clic izquierdo: girar el tambor (ruleta).</li>
 *   <li>Agachado + clic derecho a otro jugador: retarle a un duelo.</li>
 *   <li>En el inventario, como un saco: suelta munición encima para cargar (clic izq.: todas las que quepan; clic der.:
 *       una), clic derecho con la mano vacía para sacar la última bala, y panal / hacha encima para encerar / rascar.</li>
 * </ul>
 */
public class RevolverItem extends Item {
    public RevolverItem(Properties p) {
        super(p);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack s = p.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(s);
        ItemStack otra = p.getOffhandItem();
        if (p.isShiftKeyDown()) {
            if (Carga.de(otra) != null) {
                if (!level.isClientSide) Revolver.cargar((ServerPlayer) p, s, otra);
                p.getCooldowns().addCooldown(this, 4);
                return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
            }
            if (Revolver.esPanal(otra)) {
                if (!level.isClientSide) Revolver.encerar((ServerPlayer) p, s, otra);
                return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
            }
            if (Revolver.esHacha(otra)) {
                if (!level.isClientSide) Revolver.rascar((ServerPlayer) p, s, otra, InteractionHand.OFF_HAND);
                return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
            }
            if (EncantamientoRevolver.nivel(ModEnchantments.REVOLVER_ABANICO, s) > 0 && Tambor.cargadas(s) > 0) {
                if (!level.isClientSide) Revolver.empezarAbanico((ServerPlayer) p, s);
                p.getCooldowns().addCooldown(this, 16);
                return InteractionResultHolder.consume(s);
            }
        }
        if (level.getGameTime() - Tambor.giroT(s) < Tambor.GIRO_TICKS) return InteractionResultHolder.fail(s);
        if (level.isClientSide) {
            if (Tambor.siguiente(s) != Carga.VACIA) com.miscelanea.client.armasfuego.comun.FuegoCliente.patada(2.5F, 3F, 1F);   // retroceso en la mira
        } else {
            Revolver.disparar((ServerPlayer) p, s, false);
        }
        p.getCooldowns().addCooldown(this, com.miscelanea.armasfuego.comun.EquilibrioArmas.revolverEnfriamiento());
        return InteractionResultHolder.consume(s);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack s, Player p, LivingEntity otro, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || !p.isShiftKeyDown() || !(otro instanceof Player)) return InteractionResult.PASS;
        if (!p.level().isClientSide && otro instanceof ServerPlayer b) Duelo.retar((ServerPlayer) p, b);
        return InteractionResult.sidedSuccess(p.level().isClientSide);
    }

    @Override
    public void inventoryTick(ItemStack s, Level level, Entity e, int slot, boolean sel) {
        if (!level.isClientSide && sel && e instanceof ServerPlayer p) Revolver.enMano(p, s);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack s) {
        return UseAnim.NONE;
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack a, ItemStack b, boolean cambio) {
        return cambio || a.getItem() != b.getItem();
    }

    // ------------------------------------------------------------------ en el inventario (como un saco)

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack s, ItemStack otro, Slot slot, ClickAction accion, Player p, SlotAccess cursor) {
        if (!slot.allowModification(p)) return false;
        if (otro.isEmpty()) {
            if (accion != ClickAction.SECONDARY) return false;
            int i = Tambor.ultimaCargada(s);
            if (i < 0) return false;
            if (!p.level().isClientSide) {
                Carga c = Tambor.en(s, i);
                Tambor.poner(s, i, Carga.VACIA);
                cursor.set(new ItemStack(c.item()));
                p.level().playSound(null, p.getX(), p.getY(), p.getZ(), com.miscelanea.registry.ModSounds.REVOLVER_CARGAR.get(), net.minecraft.sounds.SoundSource.PLAYERS, 0.6F, 0.7F);
            }
            return true;
        }
        Carga c = Carga.de(otro);
        if (c != null) {
            int n = accion == ClickAction.SECONDARY ? 1 : Tambor.N;
            boolean algo = false;
            while (n-- > 0 && !otro.isEmpty() && Tambor.primeraVacia(s) >= 0) {
                Tambor.poner(s, Tambor.primeraVacia(s), c);
                otro.shrink(1);
                algo = true;
            }
            if (algo && !p.level().isClientSide) p.level().playSound(null, p.getX(), p.getY(), p.getZ(), com.miscelanea.registry.ModSounds.REVOLVER_CARGAR.get(), net.minecraft.sounds.SoundSource.PLAYERS, 0.6F, 1.0F);
            return true;
        }
        if (p instanceof ServerPlayer sp) {
            if (Revolver.esPanal(otro)) {
                Revolver.encerar(sp, s, otro);
                return true;
            }
            if (Revolver.esHacha(otro)) {
                Revolver.rascar(sp, s, otro, null);
                return true;
            }
        } else if (Revolver.esPanal(otro) || Revolver.esHacha(otro)) {
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ encantar

    @Override
    public boolean isEnchantable(ItemStack s) {
        return true;
    }

    @Override
    public int getEnchantmentValue() {
        return 12;
    }

    // ------------------------------------------------------------------ texto

    @Override
    public Component getName(ItemStack s) {
        int ox = Tambor.oxido(s);
        MutableComponent n = Component.translatable(getDescriptionId(s));
        if (ox > 0) n = Component.translatable("pistola.miscelanea.oxido_nombre." + ox, n);
        if (Tambor.encerado(s)) n = Component.translatable("pistola.miscelanea.encerado_nombre", n);
        return n;
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> l, TooltipFlag flag) {
        MutableComponent t = Component.translatable("pistola.miscelanea.tambor").withStyle(ChatFormatting.GRAY);
        int pos = Tambor.pos(s);
        boolean girado = Tambor.girado(s);
        for (int i = 0; i < Tambor.N; i++) {
            Carga c = Tambor.en(s, i);
            MutableComponent x = Component.literal(" ").append(Component.translatable(c.clave() + ".corto"))
                    .withStyle(c == Carga.VACIA ? ChatFormatting.DARK_GRAY : ChatFormatting.WHITE);
            if (i == pos && !girado) x = x.withStyle(ChatFormatting.UNDERLINE);
            t.append(x);
        }
        l.add(t);
        if (girado) l.add(Component.translatable("pistola.miscelanea.girado").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
        com.miscelanea.armasfuego.comun.Cobre.tooltip(s, l);
        l.add(Component.translatable("pistola.miscelanea.ayuda1").withStyle(ChatFormatting.DARK_GRAY));
        l.add(Component.translatable("pistola.miscelanea.ayuda2").withStyle(ChatFormatting.DARK_GRAY));
        l.add(Component.translatable("pistola.miscelanea.ayuda3").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public net.minecraft.client.model.HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
                return com.miscelanea.client.armasfuego.pistola.PistolaCliente.POSE;
            }

            @Override
            public boolean applyForgeHandTransform(com.mojang.blaze3d.vertex.PoseStack ps, net.minecraft.client.player.LocalPlayer pl,
                                                   net.minecraft.world.entity.HumanoidArm brazo, ItemStack st, float pt, float equip, float swing) {
                // al meter una bala: el revólver se ladea con el tambor abierto (recarga animada)
                if (st.hasTag()) {
                    float t = pl.level().getGameTime() - st.getTag().getLong(Revolver.CARGA_T) + pt;
                    if (t >= 0 && t < 12) {
                        float k = net.minecraft.util.Mth.sin(t / 12F * net.minecraft.util.Mth.PI);
                        int i = brazo == net.minecraft.world.entity.HumanoidArm.RIGHT ? 1 : -1;
                        ps.translate(i * -0.1F * k, 0.04F * k, 0);
                        com.miscelanea.client.armasfuego.comun.FuegoCliente.girar(ps, i, com.mojang.math.Axis.ZP.rotationDegrees(i * 35F * k));
                    }
                }
                com.miscelanea.client.armasfuego.comun.FuegoCliente.culatazo(ps);
                return false;
            }
        });
    }
}
