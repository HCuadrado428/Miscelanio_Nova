package com.miscelanea.armasfuego.escopeta;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

/**
 * Escopeta de presión: funciona con aire comprimido.
 * <ul>
 *   <li>Mantener clic derecho: bombear (la aguja del manómetro sube por la zona verde, la amarilla y la roja). Al soltar,
 *       el aire se queda en el depósito (se va escapando poco a poco, más con el cobre oxidado).</li>
 *   <li>Clic izquierdo: dispara con la presión que haya (cuanta más, más fuerte y más lejos). Con cartuchos en la tolva
 *       salen perdigones; con la tolva vacía sale un soplido.</li>
 *   <li>Agachado + mantener clic derecho: la válvula al revés, aspira.</li>
 *   <li>Si se bombea por encima del tope, revienta.</li>
 * </ul>
 * La tolva se carga como un saco: clic derecho con los cartuchos sobre la escopeta en el inventario (o con la escopeta
 * sobre los cartuchos); clic derecho con la mano vacía la vacía. Un panal la encera y un hacha la rasca.
 */
public class EscopetaPresionItem extends Item {
    /** Ticks entre bombeos. */
    public static final int RITMO = 6;

    public EscopetaPresionItem(Properties p) {
        super(p);
    }

    // ---------------------------------------------------------------- bombear / aspirar

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack s = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND || Presion.reventada(s, level) > 0) return InteractionResultHolder.pass(s);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(s);
    }

    @Override
    public int getUseDuration(ItemStack s) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack s) {
        return UseAnim.CROSSBOW;
    }

    @Override
    public void onUseTick(Level level, LivingEntity e, ItemStack s, int quedan) {
        if (!(e instanceof Player p) || level.isClientSide) return;
        int t = getUseDuration(s) - quedan;
        if (p.isShiftKeyDown()) DisparoPresion.aspirar((ServerPlayer) p, s, t);
        else if (t % RITMO == RITMO / 2) DisparoPresion.bombear((ServerPlayer) p, s);
    }

    @Override
    public void releaseUsing(ItemStack s, Level level, LivingEntity e, int quedan) {
        // el aire se queda dentro: se dispara con el clic izquierdo
    }

    @Override
    public void inventoryTick(ItemStack s, Level level, Entity e, int slot, boolean sel) {
        if (level.isClientSide || !(e instanceof ServerPlayer p) || !s.hasTag()) return;
        // humo tras reventar
        if (sel && Presion.reventada(s, level) > 0 && level.getGameTime() % 3 == 0) {
            var mira = p.getViewVector(1.0F);
            var der = mira.cross(new net.minecraft.world.phys.Vec3(0, 1, 0));
            der = der.lengthSqr() < 1e-4 ? new net.minecraft.world.phys.Vec3(1, 0, 0) : der.normalize();
            var m = p.getEyePosition().add(mira.scale(0.7)).add(der.scale(0.35)).add(0, -0.35, 0);
            ((ServerLevel) level).sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE, m.x, m.y, m.z, 2, 0.06, 0.04, 0.06, 0.01);
            if (level.getGameTime() % 24 == 0)
                level.playSound(null, m.x, m.y, m.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.15F, 1.8F);
        }
        // la junta pierde un poco de aire (más cuanto más oxidado está el cobre)
        float pr = Presion.presion(s);
        if (pr > 0 && !(p.isUsingItem() && p.getUseItem() == s)) {
            int cada = 40 / (1 + Presion.oxido(s));
            if (level.getGameTime() % cada == 0) {
                Presion.ponerPresion(s, pr < 1 ? 0 : pr - 1);
                if (Presion.presion(s) < Presion.MINIMA) s.getOrCreateTag().remove(Presion.DISPAROS);
            }
        }
    }

    // ---------------------------------------------------------------- tolva, panal y hacha (como un saco)

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack s, ItemStack otro, Slot slot, ClickAction a, Player p, SlotAccess cursor) {
        if (a != ClickAction.SECONDARY || !slot.allowModification(p)) return false;
        return usarEnInventario(s, otro, p, cursor);
    }

    @Override
    public boolean overrideStackedOnOther(ItemStack s, Slot slot, ClickAction a, Player p) {
        if (a != ClickAction.SECONDARY) return false;
        ItemStack otro = slot.getItem();
        TipoCartucho t = TipoCartucho.de(otro.getItem());
        if (t != null) {
            TipoCartucho dentro = Presion.tolva(s);
            if (dentro != null && dentro != t) return true;
            int cabe = Presion.TOLVA_MAX - Presion.tolvaN(s);
            if (cabe <= 0) return true;
            ItemStack cogido = slot.safeTake(otro.getCount(), cabe, p);
            if (!cogido.isEmpty()) {
                Presion.ponerTolva(s, t, Presion.tolvaN(s) + cogido.getCount());
                p.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 0.8F + p.level().getRandom().nextFloat() * 0.4F);
            }
            return true;
        }
        if (otro.isEmpty() && Presion.tolvaN(s) > 0) {
            TipoCartucho dentro = Presion.tolva(s);
            ItemStack fuera = new ItemStack(dentro.item(), Presion.tolvaN(s));
            ItemStack resto = slot.safeInsert(fuera);
            Presion.ponerTolva(s, dentro, resto.getCount());
            p.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8F, 0.8F + p.level().getRandom().nextFloat() * 0.4F);
            return true;
        }
        return false;
    }

    /**
     * Clic derecho con {@code otro} (lo que se lleva en el cursor) sobre la escopeta: cartuchos a la tolva, mano vacía
     * la vacía, panal la encera y hacha la rasca (quita la cera o un paso de óxido). Público para la prueba del CI.
     */
    public static boolean usarEnInventario(ItemStack s, ItemStack otro, Player p, @Nullable SlotAccess cursor) {
        var r = p.level().getRandom();
        if (otro.isEmpty()) {
            TipoCartucho dentro = Presion.tolva(s);
            if (dentro == null || cursor == null) return false;
            cursor.set(new ItemStack(dentro.item(), Presion.tolvaN(s)));
            Presion.ponerTolva(s, null, 0);
            p.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8F, 0.8F + r.nextFloat() * 0.4F);
            return true;
        }
        TipoCartucho t = TipoCartucho.de(otro.getItem());
        if (t != null) {
            TipoCartucho dentro = Presion.tolva(s);
            if (dentro != null && dentro != t) {
                p.playSound(SoundEvents.DISPENSER_FAIL, 0.4F, 1.6F);
                return true;
            }
            int n = Math.min(otro.getCount(), Presion.TOLVA_MAX - Presion.tolvaN(s));
            if (n > 0) {
                Presion.ponerTolva(s, t, Presion.tolvaN(s) + n);
                otro.shrink(n);
                p.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 0.8F + r.nextFloat() * 0.4F);
            }
            return true;
        }
        if (otro.is(Items.HONEYCOMB)) {
            if (Presion.encerada(s)) return false;
            s.getOrCreateTag().putBoolean(Presion.ENCERADA, true);
            otro.shrink(1);
            p.playSound(SoundEvents.HONEYCOMB_WAX_ON, 1.0F, 1.0F);
            return true;
        }
        if (otro.getItem() instanceof AxeItem) {
            if (Presion.encerada(s)) {
                s.getOrCreateTag().remove(Presion.ENCERADA);
                p.playSound(SoundEvents.AXE_WAX_OFF, 1.0F, 1.0F);
            } else if (Presion.oxido(s) > 0) {
                Presion.ponerOxido(s, Presion.oxido(s) - 1);
                p.playSound(SoundEvents.AXE_SCRAPE, 1.0F, 1.0F);
            } else return false;
            otro.hurtAndBreak(1, p, x -> {});
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- varios

    @Override
    public int getEnchantmentValue() {
        return 12;
    }

    @Override
    public boolean isValidRepairItem(ItemStack s, ItemStack material) {
        return material.is(Items.COPPER_INGOT);
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack a, ItemStack b, boolean cambio) {
        return cambio || a.getItem() != b.getItem();
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> l, TooltipFlag f) {
        int pr = Math.round(Presion.presion(s)), tope = Math.round(Presion.tope(s));
        ChatFormatting zc = switch (Presion.zona(s)) {
            case 1 -> ChatFormatting.GREEN;
            case 2 -> ChatFormatting.YELLOW;
            case 3 -> ChatFormatting.RED;
            default -> ChatFormatting.GRAY;
        };
        l.add(Component.translatable("item.miscelanea.escopeta_presion.presion", pr, tope).withStyle(zc));
        TipoCartucho t = Presion.tolva(s);
        l.add(t == null ? Component.translatable("item.miscelanea.escopeta_presion.tolva_vacia").withStyle(ChatFormatting.GRAY)
                : Component.translatable("item.miscelanea.escopeta_presion.tolva", Presion.tolvaN(s), Component.translatable(t.item().getDescriptionId()))
                .withStyle(ChatFormatting.GRAY));
        String[] ox = {"normal", "expuesta", "erosionada", "oxidada"};
        Component estado = Component.translatable("item.miscelanea.escopeta_presion.oxido." + ox[Presion.oxido(s)]);
        l.add((Presion.encerada(s) ? Component.translatable("item.miscelanea.escopeta_presion.encerada", estado) : estado.copy())
                .withStyle(ChatFormatting.DARK_AQUA));
        l.add(Component.translatable("item.miscelanea.escopeta_presion.ayuda1").withStyle(ChatFormatting.DARK_GRAY));
        l.add(Component.translatable("item.miscelanea.escopeta_presion.ayuda2").withStyle(ChatFormatting.DARK_GRAY));
        l.add(Component.translatable("item.miscelanea.escopeta_presion.ayuda3").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public net.minecraft.client.model.HumanoidModel.ArmPose getArmPose(LivingEntity e, InteractionHand h, ItemStack st) {
                return com.miscelanea.client.armasfuego.escopeta.EscopetaCliente.pose(e, h);
            }

            @Override
            public boolean applyForgeHandTransform(com.mojang.blaze3d.vertex.PoseStack ps, net.minecraft.client.player.LocalPlayer pl,
                                                   net.minecraft.world.entity.HumanoidArm brazo, ItemStack s, float pt, float equip, float swing) {
                return com.miscelanea.client.armasfuego.escopeta.EscopetaCliente.mano(ps, pl, brazo, s, pt, equip);
            }
        });
    }
}
