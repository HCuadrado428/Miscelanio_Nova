package com.miscelanea.armas;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.miscelanea.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

/**
 * Mazo de Wemmbu: un martillo del trueno enorme y pesado.
 * - Golpe en caída (como el mazo de la 1.21): más daño cuanto más caes, sin daño de caída y con ráfaga que te sube.
 * - En el suelo, mantén clic derecho y suelta: lo lanzas, golpea a todo lo que atraviesa y vuelve solo a tu mano.
 * - En el aire, mantén clic derecho: caes en picado y al tocar el suelo haces un golpe sísmico en área.
 * - Carga de trueno: cada golpe que das suma una carga (máx. 5). Llena, el siguiente golpe sísmico o lanzamiento
 *   llama a un rayo sobre el impacto y gasta las cargas.
 */
public class MazoItem extends Item implements GeoItem {
    public static final String LANZADO = "Lanzado", PICADO = "Picado", TRUENO = "Trueno", LISTO = "Listo";
    public static final int MAX_TRUENO = 5, CARGA_LANZAR = 10;
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.mazo_wemmbu.idle");
    private static final RawAnimation CARGADO = RawAnimation.begin().thenLoop("animation.mazo_wemmbu.cargado");
    private static final RawAnimation OCULTO = RawAnimation.begin().thenLoop("animation.mazo_wemmbu.oculto");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Multimap<Attribute, AttributeModifier> atributos;

    public MazoItem(Properties p) {
        super(p);
        atributos = ImmutableMultimap.<Attribute, AttributeModifier>builder()
                .put(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Mazo", 9.0, AttributeModifier.Operation.ADDITION))
                .put(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Mazo", -3.2, AttributeModifier.Operation.ADDITION)).build();
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        return slot == EquipmentSlot.MAINHAND ? atributos : super.getDefaultAttributeModifiers(slot);
    }

    @Override
    public boolean canAttackBlock(BlockState s, Level l, BlockPos p, Player pl) {
        return !pl.isCreative();
    }

    public static boolean lanzado(ItemStack s) {
        return s.hasTag() && s.getTag().getLong(LANZADO) != 0;
    }

    public static int trueno(ItemStack s) {
        return s.hasTag() ? s.getTag().getInt(TRUENO) : 0;
    }

    /** Suma una carga de trueno; avisa al llenarse. */
    public static void sumarTrueno(Player p, ItemStack s) {
        int t = trueno(s);
        if (t >= MAX_TRUENO) return;
        s.getOrCreateTag().putInt(TRUENO, t + 1);
        if (t + 1 == MAX_TRUENO) {
            p.displayClientMessage(Component.translatable("item.miscelanea.mazo_wemmbu.cargado").withStyle(ChatFormatting.AQUA), true);
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.MAZO_CARGADO.get(), SoundSource.PLAYERS, 1F, 1F);
        }
    }

    /** Daño extra del golpe en caída según la altura (como el mazo de la 1.21). */
    public static float extraCaida(float caida) {
        if (caida <= 1.5F) return 0;
        float d = Math.min(caida, 3) * 4;
        if (caida > 3) d += Math.min(caida - 3, 5) * 2;
        if (caida > 8) d += caida - 8;
        return d;
    }

    @Override
    public int getUseDuration(ItemStack s) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack s) {
        return s.hasTag() && s.getTag().getBoolean(PICADO) ? UseAnim.NONE : UseAnim.SPEAR;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack s = p.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND || lanzado(s) || p.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(s);
        boolean aire = !p.onGround() && !p.isInWater() && !p.getAbilities().flying;
        s.getOrCreateTag().putBoolean(PICADO, aire);
        if (aire && !level.isClientSide) level.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.VIENTO_RAFAGA.get(), SoundSource.PLAYERS, 1F, 0.6F);
        p.startUsingItem(hand);
        return InteractionResultHolder.consume(s);
    }

    @Override
    public void onUseTick(Level level, LivingEntity e, ItemStack s, int restante) {
        if (!s.getTag().getBoolean(PICADO)) return;
        // en picado: cae recto y rápido hacia el suelo
        Vec3 v = e.getDeltaMovement();
        e.setDeltaMovement(v.x * 0.6, Math.min(v.y, -1.6), v.z * 0.6);
        if (level.isClientSide && e.tickCount % 2 == 0)
            level.addParticle(net.minecraft.core.particles.ParticleTypes.CLOUD, e.getX(), e.getY() + 1, e.getZ(), 0, 0.3, 0);
        if (e.onGround() || e.isInWater()) e.stopUsingItem();
    }

    @Override
    public void releaseUsing(ItemStack s, Level level, LivingEntity e, int restante) {
        if (!(e instanceof Player p) || s.getTag() == null || s.getTag().getBoolean(PICADO)) return;
        if (getUseDuration(s) - restante < CARGA_LANZAR || level.isClientSide) return;
        MazoLanzado m = new MazoLanzado(level, p);
        Vec3 mira = p.getViewVector(1);
        m.setPos(p.getX() + mira.x * 0.5, p.getEyeY() - 0.2, p.getZ() + mira.z * 0.5);
        m.setDeltaMovement(mira.scale(1.9));
        level.addFreshEntity(m);
        s.getOrCreateTag().putLong(LANZADO, level.getGameTime());
        level.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.MAZO_LANZAR.get(), SoundSource.PLAYERS, 1.3F, 0.9F);
    }

    @Override
    public void inventoryTick(ItemStack s, Level level, Entity e, int slot, boolean sel) {
        if (level.isClientSide || !s.hasTag()) return;
        var t = s.getTag();
        // por si el mazo lanzado se perdió (descargado, dimensión...): vuelve solo a los 15 s
        if (t.getLong(LANZADO) != 0 && level.getGameTime() - t.getLong(LANZADO) > 300) t.remove(LANZADO);
        if (t.getBoolean(PICADO) && e instanceof LivingEntity le && !le.isUsingItem() && (le.onGround() || le.isInWater())) t.remove(PICADO);
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> l, TooltipFlag f) {
        for (int i = 1; i <= 4; i++) l.add(Component.translatable("item.miscelanea.mazo_wemmbu.ayuda" + i).withStyle(ChatFormatting.GRAY));
        l.add(Component.translatable("item.miscelanea.mazo_wemmbu.trueno", "⚡".repeat(trueno(s)) + "·".repeat(MAX_TRUENO - trueno(s))).withStyle(ChatFormatting.AQUA));
        l.add(Component.translatable("item.miscelanea.solo_creativo").withStyle(ChatFormatting.DARK_PURPLE));
    }

    @Override
    public boolean isFoil(ItemStack s) {
        return trueno(s) >= MAX_TRUENO;
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack a, ItemStack b, boolean cambio) {
        return cambio || a.getItem() != b.getItem() || lanzado(a) != lanzado(b);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private BlockEntityWithoutLevelRenderer r;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (r == null) r = new com.miscelanea.client.armas.ArmaGeoRenderer<>("mazo_wemmbu", true);
                return r;
            }
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar c) {
        c.add(new AnimationController<>(this, "mazo", 0, st -> {
            ItemStack s = st.getData(DataTickets.ITEMSTACK);
            if (s != null && lanzado(s)) return st.setAndContinue(OCULTO);
            if (s != null && trueno(s) >= MAX_TRUENO) return st.setAndContinue(CARGADO);
            return st.setAndContinue(IDLE);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    /** Busca el mazo lanzado en el inventario del jugador y lo marca como de vuelta. */
    public static void devolver(Player p) {
        for (ItemStack s : p.getInventory().items)
            if (s.getItem() instanceof MazoItem && lanzado(s)) s.getTag().remove(LANZADO);
        if (p instanceof ServerPlayer sp) sp.getCooldowns().addCooldown(com.miscelanea.registry.ModItems.MAZO_WEMMBU.get(), 10);
    }

    @Nullable
    public static ItemStack enMano(Player p) {
        ItemStack s = p.getMainHandItem();
        return s.getItem() instanceof MazoItem ? s : null;
    }
}
