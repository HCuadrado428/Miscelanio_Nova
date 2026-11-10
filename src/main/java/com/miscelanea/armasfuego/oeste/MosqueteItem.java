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
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Mosquete de chispa con la bayoneta calada (tipo Brown Bess). Un solo tiro de mucho daño: al apretar el gatillo el
 * pedernal enciende la cazoleta y un instante después sale el tiro con una nube de humo. La recarga es lenta
 * (pólvora, bola y baqueta, con su animación) y con lluvia o metido en el agua la pólvora se moja y a veces falla.
 * Cuerpo a cuerpo, la bayoneta hace de lanza (más alcance y daño, pero lenta).
 * <p>
 * La bayoneta se cambia (1.1.0): cualquier espada (de madera a netherita, y las de otros mods) se cala combinándola
 * con el mosquete en la mesa de trabajo o en el inventario, como un saco (la espada encima del mosquete, clic
 * derecho); el daño y la durabilidad son los de la espada (se gasta ella) y en el modelo se ve la hoja de su
 * material. Con la mano vacía (clic derecho en el inventario) o sola en la mesa, se quita y se recupera la espada. La
 * recarga tiene tres pasos que enseña el HUD: pólvora, bola y baqueta.
 */
public class MosqueteItem extends ArmaFuegoItem {
    public static final ResourceKey<DamageType> DANO = Balistica.clave("mosquete_disparo");
    /** Hora del mundo en que sale el tiro (tras la chispa en la cazoleta). */
    public static final String CHISPA = "Chispa";
    public static final int RETARDO = 5;
    /** NBT: la espada calada de bayoneta (si no hay ninguna, la de hierro de siempre) o que no lleva. */
    public static final String BAYONETA = "Bayoneta", SIN_BAYONETA = "SinBayoneta";
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
        // cada paso suena: la pólvora al entrar, la bola al tercio y la baqueta a los dos tercios
        int t = getUseDuration(s) - quedan;
        int r = ticksRecarga(s);
        if (!level.isClientSide && modo(s) == MODO_RECARGA) {
            if (t == r / 3) level.playSound(null, e.getX(), e.getY(), e.getZ(), net.minecraft.sounds.SoundEvents.STONE_HIT, SoundSource.PLAYERS, 0.6F, 1.6F);
            if (t == r * 2 / 3) level.playSound(null, e.getX(), e.getY(), e.getZ(), ModSounds.MOSQUETE_BAQUETA.get(), SoundSource.PLAYERS, 0.8F, 1.3F);
            if (t == 2) level.playSound(null, e.getX(), e.getY(), e.getZ(), net.minecraft.sounds.SoundEvents.SAND_PLACE, SoundSource.PLAYERS, 0.5F, 1.4F);
        }
    }


    @Override
    protected SoundEvent sonidoRecarga() {
        return ModSounds.MOSQUETE_BAQUETA.get();
    }

    @Override
    protected int enfriamiento(Player p, ItemStack s) {
        return retardo(s) + 10;
    }

    /** El pedernal en la cazoleta también puede fallar (fogonazo en la cazoleta): el triple de lo normal (Pedernal fino: menos). */
    @Override
    protected float probAtasco(Player p, ItemStack s) {
        return EquilibrioArmas.encasquilladoBase() * 3 * (1 - 0.3F * nivel(com.miscelanea.registry.ModEnchantments.MOSQUETE_PEDERNAL, s));
    }

    /** Lo que tarda la chispa en prender la pólvora (Pedernal fino: menos). */
    public static int retardo(ItemStack s) {
        return Math.max(1, RETARDO - 2 * nivel(com.miscelanea.registry.ModEnchantments.MOSQUETE_PEDERNAL, s));
    }

    /** El paso de la recarga (0 pólvora, 1 bola, 2 baqueta) según lo que se lleva de ella (0..1). */
    public static int pasoRecarga(float fr) {
        return fr < 1F / 3F ? 0 : fr < 2F / 3F ? 1 : 2;
    }

    @Override
    protected InteractionResultHolder<ItemStack> apretarGatillo(Level level, Player p, ItemStack s) {
        if (p.getCooldowns().isOnCooldown(this) || s.hasTag() && s.getTag().contains(CHISPA)) return InteractionResultHolder.fail(s);
        if (balas(s) <= 0 || atascada(s)) {
            intentar(level, p, s);   // clic
            return InteractionResultHolder.fail(s);
        }
        // el pedernal pica: la cazoleta se enciende y el tiro sale RETARDO ticks después
        s.getOrCreateTag().putLong(CHISPA, level.getGameTime() + retardo(s));
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
        } else if (tipo == ModItems.BOLA_ARPON.get()) {
            arpon(p);
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

    /**
     * Bola arpón (el arpón de Sea of Thieves): una cuerda que tira de lo que alcanza hacia ti (criaturas, barcos,
     * vagonetas, objetos) o, si da en un bloque, tira de ti hacia él.
     */
    private void arpon(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        Vec3 ojo = p.getEyePosition(), dir = p.getViewVector(1F);
        double alcance = 32;
        var bh = l.clip(new net.minecraft.world.level.ClipContext(ojo, ojo.add(dir.scale(alcance)), net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, p));
        Vec3 fin = bh.getLocation();
        Entity montura = p.getVehicle();
        var eh = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(l, p, ojo, fin, new net.minecraft.world.phys.AABB(ojo, fin).inflate(1.0),
                e -> e != p && e != montura && !e.isSpectator() && e.isPickable());
        float f = EquilibrioArmas.mosqueteArpon();
        Vec3 hasta = eh != null ? eh.getLocation() : fin;
        if (eh != null) {
            Entity e = eh.getEntity();
            if (e instanceof LivingEntity v) Balistica.herir(v, p, 5F, 0.2F, DANO, false);
            Vec3 hacia = p.position().subtract(e.position());
            Vec3 vel = hacia.normalize().scale(Math.min(f, hacia.length() * 0.25 + 0.4)).add(0, 0.3, 0);
            e.setDeltaMovement(vel);
            e.hurtMarked = true;
            LogrosMiscelanea.dar(p, "mosquete_arpon");
        } else if (bh.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
            Vec3 hacia = fin.subtract(p.position());
            p.setDeltaMovement(hacia.normalize().scale(Math.min(f * 1.1, hacia.length() * 0.22 + 0.5)).add(0, 0.35, 0));
            p.connection.send(new ClientboundSetEntityMotionPacket(p));
            p.fallDistance = 0;
            LogrosMiscelanea.dar(p, "mosquete_arpon");
        }
        var cuerda = new net.minecraft.core.particles.ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.LEAD));
        int n = (int) Math.min(16, ojo.distanceTo(hasta) / 1.5);
        for (int k = 1; k <= n; k++) {
            Vec3 q = ojo.lerp(hasta, k / (double) (n + 1));
            l.sendParticles(cuerda, q.x, q.y - 0.2, q.z, 1, 0, 0, 0, 0);
        }
        l.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.ARPON.get(), SoundSource.PLAYERS, 1.2F, 1.0F);
    }

    // ------------------------------------------------------------------ la bayoneta (cambiable)

    /** ¿Vale como bayoneta? (cualquier espada, también las de otros mods) */
    public static boolean esEspada(ItemStack x) {
        return !x.isEmpty() && x.getItem() instanceof SwordItem;
    }

    public static boolean tieneBayoneta(ItemStack s) {
        return !(s.hasTag() && s.getTag().getBoolean(SIN_BAYONETA));
    }

    /** La espada calada (la de hierro si es un mosquete de los de antes), o vacío si no lleva. */
    public static ItemStack bayoneta(ItemStack s) {
        if (!tieneBayoneta(s)) return ItemStack.EMPTY;
        if (s.hasTag() && s.getTag().contains(BAYONETA)) return ItemStack.of(s.getTag().getCompound(BAYONETA));
        return new ItemStack(Items.IRON_SWORD);
    }

    public static void ponerBayoneta(ItemStack s, ItemStack espada) {
        CompoundTag t = s.getOrCreateTag();
        if (espada.isEmpty()) {
            t.remove(BAYONETA);
            t.putBoolean(SIN_BAYONETA, true);
        } else {
            t.put(BAYONETA, espada.copyWithCount(1).save(new CompoundTag()));
            t.remove(SIN_BAYONETA);
        }
    }

    /** El material de la hoja (para el modelo): 0 sin bayoneta, 1 madera, 2 piedra, 3 hierro (y las raras), 4 oro, 5 diamante, 6 netherita. */
    public static int material(ItemStack s) {
        if (!tieneBayoneta(s)) return 0;
        // se mira cada fotograma (el modelo en la mano): solo el id, sin rehacer el objeto entero
        net.minecraft.world.item.Item it = Items.IRON_SWORD;
        if (s.hasTag() && s.getTag().contains(BAYONETA)) {
            var rl = net.minecraft.resources.ResourceLocation.tryParse(s.getTag().getCompound(BAYONETA).getString("id"));
            if (rl != null) it = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl);
        }
        if (it instanceof SwordItem sw) {
            var t = sw.getTier();
            if (t == Tiers.WOOD) return 1;
            if (t == Tiers.STONE) return 2;
            if (t == Tiers.GOLD) return 4;
            if (t == Tiers.DIAMOND) return 5;
            if (t == Tiers.NETHERITE) return 6;
        }
        return 3;
    }

    /** El daño de la espada (lo que añade al puño). */
    public static double danoEspada(ItemStack espada) {
        double d = 0;
        for (AttributeModifier m : espada.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE))
            if (m.getOperation() == AttributeModifier.Operation.ADDITION) d += m.getAmount();
        return d;
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack s) {
        if (slot != EquipmentSlot.MAINHAND) return super.getAttributeModifiers(slot, s);
        ImmutableMultimap.Builder<Attribute, AttributeModifier> b = ImmutableMultimap.builder();
        ItemStack bay = bayoneta(s);
        if (bay.isEmpty()) {
            // sin bayoneta: un culatazo
            b.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Culata", 2.0, AttributeModifier.Operation.ADDITION));
            b.put(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Culata", -3.0, AttributeModifier.Operation.ADDITION));
            return b.build();
        }
        double d = danoEspada(bay) + EquilibrioArmas.mosqueteBayoneta() + 2 * nivel(com.miscelanea.registry.ModEnchantments.MOSQUETE_CARGA, s);
        b.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Bayoneta", d, AttributeModifier.Operation.ADDITION));
        b.put(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Bayoneta", -3.0, AttributeModifier.Operation.ADDITION));
        b.put(net.minecraftforge.common.ForgeMod.ENTITY_REACH.get(), new AttributeModifier(ALCANCE_ID, "Bayoneta", 1.5, AttributeModifier.Operation.ADDITION));
        return b.build();
    }

    @Override
    public boolean hurtEnemy(ItemStack s, LivingEntity v, LivingEntity a) {
        ItemStack bay = bayoneta(s);
        if (bay.isEmpty()) s.hurtAndBreak(1, a, x -> x.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        else if (bay.isDamageableItem() && !(a instanceof Player pl && pl.getAbilities().instabuild)) {
            // se gasta la espada calada; si se rompe, el mosquete se queda sin bayoneta
            if (bay.hurt(1, a.getRandom(), a instanceof ServerPlayer sp ? sp : null)) {
                ponerBayoneta(s, ItemStack.EMPTY);
                a.level().playSound(null, a.getX(), a.getY(), a.getZ(), net.minecraft.sounds.SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.8F, 1.2F);
            } else ponerBayoneta(s, bay);
        }
        if (!v.isAlive() && a instanceof ServerPlayer p && !bay.isEmpty()) LogrosMiscelanea.dar(p, "mosquete_bayoneta");
        return true;
    }

    /** Calar una espada (devuelve la que había). */
    public static ItemStack calar(Player p, ItemStack s, ItemStack espada) {
        ItemStack vieja = tieneBayoneta(s) ? bayoneta(s) : ItemStack.EMPTY;
        ponerBayoneta(s, espada);
        p.playSound(net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_IRON, 0.8F, 1.3F);
        if (p instanceof ServerPlayer sp && material(s) == 6) LogrosMiscelanea.dar(sp, "mosquete_netherita");
        return vieja;
    }

    /** En el inventario, como un saco: una espada encima la cala; con la mano vacía se quita (y el cobre, como siempre). */
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack s, ItemStack otro, net.minecraft.world.inventory.Slot slot, net.minecraft.world.inventory.ClickAction a,
                                            Player p, net.minecraft.world.entity.SlotAccess cursor) {
        if (a == net.minecraft.world.inventory.ClickAction.SECONDARY && slot.allowModification(p)) {
            if (esEspada(otro) && otro.getCount() == 1) {
                ItemStack vieja = calar(p, s, otro);
                cursor.set(vieja);
                return true;
            }
            if (otro.isEmpty() && tieneBayoneta(s)) {
                cursor.set(bayoneta(s));
                ponerBayoneta(s, ItemStack.EMPTY);
                p.playSound(net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_IRON, 0.8F, 0.9F);
                return true;
            }
        }
        return super.overrideOtherStackedOnMe(s, otro, slot, a, p, cursor);
    }

    @Override
    public Component getName(ItemStack s) {
        if (tieneBayoneta(s)) return super.getName(s);
        return com.miscelanea.armasfuego.comun.Cobre.nombre(Component.translatable("item.miscelanea.mosquete.sin_bayoneta"), s, false);
    }

    @Override
    public void appendHoverText(ItemStack s, @javax.annotation.Nullable Level level, java.util.List<Component> l, net.minecraft.world.item.TooltipFlag f) {
        ItemStack b = bayoneta(s);
        l.add(b.isEmpty() ? Component.translatable("item.miscelanea.mosquete.bayoneta_no").withStyle(ChatFormatting.GRAY)
                : Component.translatable("item.miscelanea.mosquete.bayoneta", b.getHoverName()).withStyle(ChatFormatting.GRAY));
        super.appendHoverText(s, level, l, f);
        l.add(Component.translatable("item.miscelanea.mosquete.ayuda_bayoneta").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public boolean canAttackBlock(net.minecraft.world.level.block.state.BlockState st, Level l, net.minecraft.core.BlockPos pos, Player p) {
        return !p.isCreative();
    }
}
