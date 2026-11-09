package com.miscelanea.armasfuego.oeste;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.miscelanea.armasfuego.comun.ArmaFuegoItem;
import com.miscelanea.armasfuego.comun.Balistica;
import com.miscelanea.armasfuego.comun.EquilibrioArmas;
import com.miscelanea.logros.LogrosMiscelanea;
import com.miscelanea.registry.ModItems;
import com.miscelanea.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Mosquete de chispa con la bayoneta calada (tipo Brown Bess). Un solo tiro de mucho daño: al apretar el gatillo el
 * pedernal enciende la cazoleta y un instante después sale el tiro con una nube de humo. La recarga es lenta
 * (pólvora, bola y baqueta, con su animación) y con lluvia o metido en el agua la pólvora se moja y a veces falla.
 * Cuerpo a cuerpo, la bayoneta hace de lanza (más alcance y daño, pero lenta).
 */
public class MosqueteItem extends ArmaFuegoItem {
    public static final ResourceKey<DamageType> DANO = Balistica.clave("mosquete_disparo");
    /** Hora del mundo en que sale el tiro (tras la chispa en la cazoleta). */
    public static final String CHISPA = "Chispa";
    public static final int RETARDO = 5;
    private static final UUID ALCANCE_ID = UUID.fromString("5c1b1e2a-7a2d-4f43-9d3e-6b9a2a0f4b11");

    public MosqueteItem(Properties p) {
        super(p);
    }

    @Override
    public int capacidad(ItemStack s) {
        return 1;
    }

    @Override
    public int ticksRecarga(ItemStack s) {
        return Math.max(10, Math.round(EquilibrioArmas.mosqueteRecarga() * (1 - 0.2F * nivel(com.miscelanea.registry.ModEnchantments.MOSQUETE_BAQUETA, s))));
    }

    @Override
    protected boolean acepta(ItemStack m) {
        return m.is(ModItems.BOLA_MOSQUETE.get()) || m.is(ModItems.METRALLA.get()) || m.is(ModItems.BOLA_CADENA.get());
    }

    @Override
    protected net.minecraft.world.item.Item municionNormal() {
        return ModItems.BOLA_MOSQUETE.get();
    }

    @Override
    protected int polvora() {
        return 1;
    }

    @Override
    protected boolean femenino() {
        return false;
    }

    @Override
    protected void empezarRecarga(ServerPlayer p, ItemStack s) {
        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.MOSQUETE_BAQUETA.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
    }

    @Override
    public void onUseTick(Level level, LivingEntity e, ItemStack s, int quedan) {
        super.onUseTick(level, e, s, quedan);
        // la baqueta suena a mitad de la recarga
        int t = getUseDuration(s) - quedan;
        if (!level.isClientSide && modo(s) == MODO_RECARGA && t == ticksRecarga(s) / 2)
            level.playSound(null, e.getX(), e.getY(), e.getZ(), ModSounds.MOSQUETE_BAQUETA.get(), SoundSource.PLAYERS, 0.8F, 1.3F);
    }


    @Override
    protected SoundEvent sonidoRecarga() {
        return ModSounds.MOSQUETE_BAQUETA.get();
    }

    @Override
    protected int enfriamiento(Player p, ItemStack s) {
        return RETARDO + 10;
    }

    /** El pedernal en la cazoleta también puede fallar (fogonazo en la cazoleta): el triple de lo normal. */
    @Override
    protected float probAtasco(Player p, ItemStack s) {
        return EquilibrioArmas.encasquilladoBase() * 3;
    }

    @Override
    protected InteractionResultHolder<ItemStack> apretarGatillo(Level level, Player p, ItemStack s) {
        if (p.getCooldowns().isOnCooldown(this) || s.hasTag() && s.getTag().contains(CHISPA)) return InteractionResultHolder.fail(s);
        if (balas(s) <= 0 || atascada(s)) {
            intentar(level, p, s);   // clic
            return InteractionResultHolder.fail(s);
        }
        // el pedernal pica: la cazoleta se enciende y el tiro sale RETARDO ticks después
        s.getOrCreateTag().putLong(CHISPA, level.getGameTime() + RETARDO);
        if (!level.isClientSide) {
            ServerLevel l = (ServerLevel) level;
            Vec3 c = boca(p, 0.45, 0.3, 0.2);
            l.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.MOSQUETE_CAZOLETA.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            l.sendParticles(ParticleTypes.SMALL_FLAME, c.x, c.y, c.z, 3, 0.03, 0.03, 0.03, 0.01);
            l.sendParticles(ParticleTypes.SMOKE, c.x, c.y + 0.05, c.z, 4, 0.04, 0.04, 0.04, 0.01);
        }
        p.getCooldowns().addCooldown(this, enfriamiento(p, s));
        return InteractionResultHolder.consume(s);
    }

    @Override
    public void inventoryTick(ItemStack s, Level level, Entity e, int slot, boolean sel) {
        super.inventoryTick(s, level, e, slot, sel);
        if (!(e instanceof Player p) || !s.hasTag() || !s.getTag().contains(CHISPA)) return;
        if (level.getGameTime() < s.getTag().getLong(CHISPA)) return;
        s.getTag().remove(CHISPA);
        if (!sel || p.getMainHandItem() != s) return;   // si se ha guardado, la cazoleta se apaga sola
        if (!level.isClientSide && mojada(p) && nivel(com.miscelanea.registry.ModEnchantments.MOSQUETE_POLVORA_SECA, s) == 0) {
            ponerBalas(s, 0);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8F, 1.2F);
            p.displayClientMessage(Component.translatable("msg.miscelanea.armafuego.mojada").withStyle(ChatFormatting.AQUA), true);
            LogrosMiscelanea.dar((ServerPlayer) p, "polvora_mojada");
            return;
        }
        intentar(level, p, s);
    }

    /** Con lluvia o en el agua, la pólvora a veces no prende. */
    public static boolean mojada(Player p) {
        return p.isInWaterOrRain() && p.getRandom().nextFloat() < EquilibrioArmas.polvoraMojada();
    }

    @Override
    protected void disparar(ServerPlayer p, ItemStack s) {
        ServerLevel l = p.serverLevel();
        var tipo = tipoActual(s);
        if (tipo == ModItems.METRALLA.get()) {
            // metralla: siete perdigones en cono, de corto alcance (la de los piratas de Assassin's Creed IV)
            for (int i = 0; i < 7; i++) tiro(p, 2.5F, 24, 6, 0.3F, 0.09, 0.2F, DANO, true, null);
        } else if (tipo == ModItems.BOLA_CADENA.get()) {
            cadena(p);
        } else {
            tiro(p, EquilibrioArmas.mosqueteDano(), 64, 30, 0.6F, 0.012, 0.35F, DANO, false, null);
        }
        Vec3 b = boca(p, 1.4, 0.25, 0.15);
        efectos(l, p, b, ModSounds.MOSQUETE_DISPARO.get(), 5.0F, 0.95F, 0, false);
        // la nube de humo de la pólvora negra
        Vec3 m = p.getViewVector(1F);
        for (int i = 0; i < 9; i++) {
            Vec3 q = b.add(m.scale(0.3 + i * 0.28));
            l.sendParticles(i % 3 == 0 ? ParticleTypes.CAMPFIRE_COSY_SMOKE : ParticleTypes.LARGE_SMOKE, q.x, q.y, q.z, 1, 0.12 + i * 0.03, 0.1, 0.12 + i * 0.03, 0.004);
        }
        l.sendParticles(ParticleTypes.POOF, b.x, b.y, b.z, 6, 0.1, 0.1, 0.1, 0.03);
        // culatazo
        p.setDeltaMovement(p.getDeltaMovement().add(m.scale(-0.18)));
        p.connection.send(new ClientboundSetEntityMotionPacket(p));
        LogrosMiscelanea.dar(p, "mosquete_disparo");
    }

    /**
     * Bola encadenada (la de Sea of Thieves para los mástiles): dos bolas unidas por una cadena que barren todo lo
     * que haya cerca de la línea del tiro (hasta tres), lo ralentizan y lo derriban.
     */
    private void cadena(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        Vec3 ojo = p.getEyePosition(), dir = p.getViewVector(1F);
        var fin = l.clip(new net.minecraft.world.level.ClipContext(ojo, ojo.add(dir.scale(40)), net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, p)).getLocation();
        double largo = ojo.distanceTo(fin);
        java.util.List<LivingEntity> dados = new java.util.ArrayList<>();
        for (LivingEntity v : l.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(ojo, fin).inflate(1.3), e -> e.isAlive() && e != p && !e.isSpectator())) {
            Vec3 c = v.getBoundingBox().getCenter().subtract(ojo);
            double a = c.dot(dir);
            if (a < 0 || a > largo || c.subtract(dir.scale(a)).length() > 1.3 + v.getBbWidth() / 2) continue;
            dados.add(v);
        }
        dados.sort(java.util.Comparator.comparingDouble(v -> v.distanceToSqr(p)));
        int n = 0;
        for (LivingEntity v : dados) {
            if (n++ >= 3) break;
            Balistica.herir(v, p, 9F, 0.35F, DANO, true);
            v.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 60, 1), p);
            v.knockback(0.6, -dir.x, -dir.z);
        }
        for (int k = 1; k <= 8; k++) {
            Vec3 q = ojo.lerp(fin, k / 9.0);
            l.sendParticles(ParticleTypes.CRIT, q.x, q.y, q.z, 2, 0.4, 0.05, 0.4, 0);
        }
        l.playSound(null, p.getX(), p.getY(), p.getZ(), net.minecraft.sounds.SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.2F, 0.7F);
        if (n >= 2) LogrosMiscelanea.dar(p, "mosquete_cadena");
    }

    // ------------------------------------------------------------------ la bayoneta

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack s) {
        if (slot != EquipmentSlot.MAINHAND) return super.getAttributeModifiers(slot, s);
        ImmutableMultimap.Builder<Attribute, AttributeModifier> b = ImmutableMultimap.builder();
        b.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Bayoneta", EquilibrioArmas.mosqueteBayoneta() - 1 + 2 * nivel(com.miscelanea.registry.ModEnchantments.MOSQUETE_CARGA, s), AttributeModifier.Operation.ADDITION));
        b.put(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Bayoneta", -3.0, AttributeModifier.Operation.ADDITION));
        b.put(net.minecraftforge.common.ForgeMod.ENTITY_REACH.get(), new AttributeModifier(ALCANCE_ID, "Bayoneta", 1.5, AttributeModifier.Operation.ADDITION));
        return b.build();
    }

    @Override
    public boolean hurtEnemy(ItemStack s, LivingEntity v, LivingEntity a) {
        s.hurtAndBreak(1, a, x -> x.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        if (!v.isAlive() && a instanceof ServerPlayer p) LogrosMiscelanea.dar(p, "mosquete_bayoneta");
        return true;
    }

    @Override
    public boolean canAttackBlock(net.minecraft.world.level.block.state.BlockState st, Level l, net.minecraft.core.BlockPos pos, Player p) {
        return !p.isCreative();
    }
}
