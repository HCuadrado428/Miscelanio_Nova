package com.miscelanea.encantos;

import com.miscelanea.Miscelanea;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Lo que hace cada encantamiento nuevo (lado servidor). Lo que depende de las teclas (Ráfaga, Deslizar, Pegajoso) y
 * Percepción van en el cliente ({@code EncantosCliente}).
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class EfectosEncantos {
    private EfectosEncantos() {}

    private static final UUID ADRENALINA_VEL = UUID.fromString("0f6c1d0a-3a51-4c3e-9e6b-6a3d1e2f9a01");
    private static final UUID ADRENALINA_DANIO = UUID.fromString("0f6c1d0a-3a51-4c3e-9e6b-6a3d1e2f9a02");
    private static final UUID VITALIDAD_VIDA = UUID.fromString("0f6c1d0a-3a51-4c3e-9e6b-6a3d1e2f9a03");
    private static final UUID ZANCADA_VEL = UUID.fromString("0f6c1d0a-3a51-4c3e-9e6b-6a3d1e2f9a04");

    // ------------------------------------------------------------------ armadura (cada medio segundo)
    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 10 != 3) return;
        float falta = 1F - p.getHealth() / Math.max(1F, p.getMaxHealth());
        boolean adr = Encantos.tiene(p, EquipmentSlot.CHEST, Encantos.ADRENALINA);
        modificador(p, Attributes.MOVEMENT_SPEED, ADRENALINA_VEL, "Adrenalina", adr ? 0.35 * falta : 0, AttributeModifier.Operation.MULTIPLY_BASE);
        modificador(p, Attributes.ATTACK_DAMAGE, ADRENALINA_DANIO, "Adrenalina", adr ? 0.3 * falta : 0, AttributeModifier.Operation.MULTIPLY_BASE);
        modificador(p, Attributes.MAX_HEALTH, VITALIDAD_VIDA, "Vitalidad", Encantos.tiene(p, EquipmentSlot.CHEST, Encantos.VITALIDAD) ? 4 : 0, AttributeModifier.Operation.ADDITION);
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
        modificador(p, Attributes.MOVEMENT_SPEED, ZANCADA_VEL, "Zancada", Encantos.tiene(p, EquipmentSlot.FEET, Encantos.ZANCADA) ? 0.15 : 0, AttributeModifier.Operation.MULTIPLY_BASE);
        // Ojo de búho: a oscuras se ve
        if (Encantos.tiene(p, EquipmentSlot.HEAD, Encantos.OJO_BUHO) && p.level().getMaxLocalRawBrightness(BlockPos.containing(p.getEyePosition())) < 7) {
            MobEffectInstance nv = p.getEffect(MobEffects.NIGHT_VISION);
            if (nv == null || nv.getDuration() < 240) p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0, true, false, true));
        }
    }

    private static void modificador(Player p, Attribute a, UUID id, String n, double v, AttributeModifier.Operation op) {
        AttributeInstance ai = p.getAttribute(a);
        if (ai == null) return;
        AttributeModifier m = ai.getModifier(id);
        if (m != null && Math.abs(m.getAmount() - v) < 1e-4) return;
        if (m != null) ai.removeModifier(id);
        if (v != 0) ai.addTransientModifier(new AttributeModifier(id, n, v, op));
    }

    // ------------------------------------------------------------------ golpes
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void golpe(LivingHurtEvent e) {
        LivingEntity v = e.getEntity();
        Entity a = e.getSource().getEntity();
        // Espinazo: por la espalda, la mitad
        if (a != null && Encantos.tiene(v, EquipmentSlot.CHEST, Encantos.ESPINAZO)) {
            Vec3 mira = v.getViewVector(1F).multiply(1, 0, 1), hacia = a.position().subtract(v.position()).multiply(1, 0, 1);
            if (mira.lengthSqr() > 1e-4 && hacia.lengthSqr() > 1e-4 && mira.normalize().dot(hacia.normalize()) < -0.3) {
                e.setAmount(e.getAmount() * 0.5F);
                if (a instanceof LivingEntity la) la.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
            }
        }
        if (!(a instanceof LivingEntity at) || e.getSource().getDirectEntity() != a) return;
        ItemStack arma = at.getMainHandItem();
        if (Encantos.tiene(arma, Encantos.BERSERKER)) {
            float falta = 1F - at.getHealth() / Math.max(1F, at.getMaxHealth());
            e.setAmount(e.getAmount() * (1F + 0.6F * falta));
        }
        if (Encantos.tiene(arma, Encantos.ESCARCHA)) {
            v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
            v.setTicksFrozen(Math.min(v.getTicksRequiredToFreeze() + 40, v.getTicksFrozen() + 60));
        }
        if (Encantos.tiene(arma, Encantos.HENDIDURA)) {
            // atraviesa armadura (más cuanto más lleva) y rompe escudos
            e.setAmount(e.getAmount() * (1F + Math.min(0.4F, v.getArmorValue() / 50F)));
            if (v instanceof Player pv && pv.isBlocking()) pv.disableShield(true);
        }
    }

    /** Vampiro: cura el 15 % de lo que hace (hasta un corazón por golpe). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void vampiro(LivingDamageEvent e) {
        if (e.getSource().getEntity() instanceof LivingEntity at && e.getSource().getDirectEntity() == at
                && Encantos.tiene(at.getMainHandItem(), Encantos.VAMPIRO) && e.getAmount() > 0)
            at.heal(Math.min(2F, e.getAmount() * 0.15F));
    }

    // ------------------------------------------------------------------ Rebote
    @SubscribeEvent
    public static void caida(LivingFallEvent e) {
        LivingEntity v = e.getEntity();
        if (!Encantos.tiene(v, EquipmentSlot.FEET, Encantos.REBOTE) || e.getDistance() < 2) return;
        if (v.isShiftKeyDown()) {
            e.setDamageMultiplier(e.getDamageMultiplier() * 0.5F);
            return;
        }
        e.setDamageMultiplier(0);
        double vy = Math.min(1.3, Math.sqrt(e.getDistance()) * 0.28);
        Vec3 d = v.getDeltaMovement();
        v.setDeltaMovement(d.x, vy, d.z);
        v.hurtMarked = true;
        v.level().playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.SLIME_JUMP, SoundSource.PLAYERS, 0.8F, 1.2F);
    }

    // ------------------------------------------------------------------ herramientas
    @SubscribeEvent
    public static void velocidad(PlayerEvent.BreakSpeed e) {
        ItemStack t = e.getEntity().getMainHandItem();
        if (Encantos.tiene(t, Encantos.PRESTEZA) && t.isCorrectToolForDrops(e.getState())) e.setNewSpeed(e.getNewSpeed() * 2.6F);
    }

    private static boolean rompiendo;

    @SubscribeEvent
    public static void romper(BlockEvent.BreakEvent e) {
        if (rompiendo || !(e.getPlayer() instanceof ServerPlayer p) || p.isShiftKeyDown() || !(e.getLevel() instanceof ServerLevel l)) return;
        ItemStack t = p.getMainHandItem();
        BlockState s = e.getState();
        BlockPos o = e.getPos();
        Set<BlockPos> extra = new HashSet<>();
        if (Encantos.tiene(t, Encantos.TALA) && s.is(BlockTags.LOGS)) extra = conectados(l, o, b -> l.getBlockState(b).is(BlockTags.LOGS), 96, true);
        else if (Encantos.tiene(t, Encantos.VETA) && s.is(Tags.Blocks.ORES)) extra = conectados(l, o, b -> l.getBlockState(b).getBlock() == s.getBlock(), 32, false);
        else if (Encantos.tiene(t, Encantos.EXCAVAR) && t.isCorrectToolForDrops(s)) {
            HitResult hr = p.pick(6, 1F, false);
            Direction cara = hr instanceof BlockHitResult bh ? bh.getDirection() : Direction.UP;
            for (int a = -1; a <= 1; a++)
                for (int b = -1; b <= 1; b++) {
                    if (a == 0 && b == 0) continue;
                    BlockPos q = switch (cara.getAxis()) {
                        case X -> o.offset(0, a, b);
                        case Y -> o.offset(a, 0, b);
                        case Z -> o.offset(a, b, 0);
                    };
                    BlockState qs = l.getBlockState(q);
                    if (!qs.isAir() && t.isCorrectToolForDrops(qs) && qs.getDestroySpeed(l, q) >= 0 && qs.getDestroySpeed(l, q) <= s.getDestroySpeed(l, o) + 1) extra.add(q);
                }
        }
        if (extra.isEmpty()) return;
        rompiendo = true;
        try {
            for (BlockPos q : extra) if (!q.equals(o)) p.gameMode.destroyBlock(q);
        } finally {
            rompiendo = false;
        }
    }

    private static Set<BlockPos> conectados(ServerLevel l, BlockPos o, java.util.function.Predicate<BlockPos> vale, int max, boolean diagonal) {
        Set<BlockPos> v = new HashSet<>();
        ArrayDeque<BlockPos> cola = new ArrayDeque<>();
        cola.add(o);
        v.add(o);
        while (!cola.isEmpty() && v.size() < max) {
            BlockPos b = cola.poll();
            for (int x = -1; x <= 1; x++)
                for (int y = -1; y <= 1; y++)
                    for (int z = -1; z <= 1; z++) {
                        if (!diagonal && Math.abs(x) + Math.abs(y) + Math.abs(z) != 1) continue;
                        BlockPos q = b.offset(x, y, z);
                        if (v.contains(q) || !vale.test(q)) continue;
                        v.add(q);
                        cola.add(q);
                        if (v.size() >= max) return v;
                    }
        }
        return v;
    }

    /** Cosecha: con la azada, clic derecho a un cultivo maduro: recoge los maduros de 3 x 3 y los vuelve a plantar. */
    @SubscribeEvent
    public static void cosecha(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !(e.getLevel() instanceof ServerLevel l) || !Encantos.tiene(e.getItemStack(), Encantos.COSECHA)) return;
        BlockState s0 = l.getBlockState(e.getPos());
        if (!(s0.getBlock() instanceof CropBlock c0) || !c0.isMaxAge(s0)) return;
        int n = 0;
        for (int x = -1; x <= 1; x++)
            for (int z = -1; z <= 1; z++) {
                BlockPos q = e.getPos().offset(x, 0, z);
                BlockState s = l.getBlockState(q);
                if (!(s.getBlock() instanceof CropBlock c) || !c.isMaxAge(s)) continue;
                var drops = Block.getDrops(s, l, q, null, p, e.getItemStack());
                ItemStack semilla = s.getBlock().getCloneItemStack(l, q, s);
                boolean quitada = false;
                for (ItemStack d : drops) {
                    if (!quitada && ItemStack.isSameItem(d, semilla)) {
                        d.shrink(1);
                        quitada = true;
                    }
                    if (!d.isEmpty()) Block.popResource(l, q, d);
                }
                l.setBlock(q, c.getStateForAge(0), 3);
                n++;
            }
        if (n > 0) {
            l.playSound(null, e.getPos(), SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 1F, 1F);
            e.setCanceled(true);
            e.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        }
    }

    // ------------------------------------------------------------------ a distancia
    /** Flechas de Certero: sin gravedad los primeros 3 segundos. */
    private static final Map<AbstractArrow, Integer> RECTAS = new WeakHashMap<>();

    @SubscribeEvent
    public static void proyectil(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof AbstractArrow f) || f instanceof ThrownTrident) return;
        if (!(f.getOwner() instanceof LivingEntity o) || f.tickCount > 1 || f.getPersistentData().getBoolean("miscelanea_vista")) return;
        f.getPersistentData().putBoolean("miscelanea_vista", true);
        ItemStack arma = o.getMainHandItem().getItem() instanceof BowItem || o.getMainHandItem().getItem() instanceof CrossbowItem ? o.getMainHandItem() : o.getOffhandItem();
        if (arma.getItem() instanceof BowItem && Encantos.tiene(arma, Encantos.CERTERO)) {
            f.setNoGravity(true);
            RECTAS.put(f, 60);
        }
        if (arma.getItem() instanceof BowItem && Encantos.tiene(arma, Encantos.RECUPERACION)) f.getPersistentData().putBoolean("miscelanea_vuelve", true);
        if (arma.getItem() instanceof CrossbowItem && Encantos.tiene(arma, Encantos.METRALLA) && !f.getPersistentData().getBoolean("miscelanea_metralla")) {
            // cuatro flechas más, abiertas en abanico (no se pueden recoger)
            Vec3 v = f.getDeltaMovement();
            for (int k = 0; k < 4; k++) {
                var otra = EntityType.ARROW.create(e.getLevel());
                if (otra == null) continue;
                otra.setOwner(o);
                otra.getPersistentData().putBoolean("miscelanea_metralla", true);
                otra.getPersistentData().putBoolean("miscelanea_vista", true);
                otra.setPos(f.getX(), f.getY(), f.getZ());
                double ang = (k < 2 ? -1 : 1) * (k % 2 == 0 ? 0.12 : 0.24);
                Vec3 r = v.yRot((float) ang);
                otra.setDeltaMovement(r.x, r.y + (k % 2 == 0 ? 0.03 : -0.03), r.z);
                otra.setBaseDamage(f.getBaseDamage() * 0.6);
                otra.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
                e.getLevel().addFreshEntity(otra);
            }
        }
    }

    @SubscribeEvent
    public static void rectas(TickEvent.LevelTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.level.isClientSide || RECTAS.isEmpty()) return;
        for (Iterator<Map.Entry<AbstractArrow, Integer>> it = RECTAS.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            AbstractArrow f = en.getKey();
            if (f.level() != e.level) continue;
            int t = en.getValue() - 1;
            if (t <= 0 || f.isRemoved()) {
                f.setNoGravity(false);
                it.remove();
            } else en.setValue(t);
        }
    }

    @SubscribeEvent
    public static void impacto(ProjectileImpactEvent e) {
        if (e.getProjectile().level().isClientSide) return;
        HitResult hr = e.getRayTraceResult();
        if (e.getProjectile() instanceof AbstractArrow f && !(f instanceof ThrownTrident) && hr instanceof EntityHitResult
                && f.getPersistentData().getBoolean("miscelanea_vuelve") && f.pickup == AbstractArrow.Pickup.ALLOWED && f.getOwner() instanceof Player p) {
            ItemStack fl = ((com.miscelanea.mixin.AbstractArrowAccessor) f).miscelanea$recoger();
            if (!fl.isEmpty() && !p.getInventory().add(fl)) p.drop(fl, false);
            f.getPersistentData().putBoolean("miscelanea_vuelve", false);
        }
        if (e.getProjectile() instanceof ThrownTrident tr && hr.getType() != HitResult.Type.MISS && e.getProjectile().level() instanceof ServerLevel l) {
            ItemStack it = ((com.miscelanea.mixin.AbstractArrowAccessor) tr).miscelanea$recoger();
            if (Encantos.tiene(it, Encantos.TORMENTA) && !tr.getPersistentData().getBoolean("miscelanea_rayo")) {
                tr.getPersistentData().putBoolean("miscelanea_rayo", true);
                BlockPos b = BlockPos.containing(hr.getLocation());
                if (l.canSeeSky(b)) {
                    LightningBolt rayo = EntityType.LIGHTNING_BOLT.create(l);
                    if (rayo != null) {
                        rayo.moveTo(Vec3.atBottomCenterOf(b));
                        if (tr.getOwner() instanceof ServerPlayer sp) rayo.setCause(sp);
                        l.addFreshEntity(rayo);
                    }
                }
            }
        }
    }

    /** Arpón: al recoger la caña con el anzuelo clavado en un bloque, tira de ti hacia él; si ha enganchado a alguien, tira fuerte de él. */
    @SubscribeEvent
    public static void arpon(PlayerInteractEvent.RightClickItem e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !e.getItemStack().is(Items.FISHING_ROD) || !Encantos.tiene(e.getItemStack(), Encantos.ARPON)) return;
        FishingHook h = p.fishing;
        if (h == null) return;
        Entity enganchado = h.getHookedIn();
        if (enganchado != null) {
            Vec3 d = p.position().subtract(enganchado.position());
            enganchado.setDeltaMovement(enganchado.getDeltaMovement().add(d.scale(0.18).add(0, 0.25, 0)));
            enganchado.hurtMarked = true;
            return;
        }
        boolean clavado = !h.isInWater() && (h.onGround() || h.horizontalCollision || h.getDeltaMovement().lengthSqr() < 1e-4);
        if (!clavado) return;
        Vec3 d = h.position().subtract(p.position());
        Vec3 v = d.scale(0.16).add(0, 0.35 + Math.max(0, d.y) * 0.05, 0);
        if (v.length() > 1.8) v = v.normalize().scale(1.8);
        p.setDeltaMovement(v);
        p.hurtMarked = true;
        p.fallDistance = 0;
        ((ServerLevel) p.level()).sendParticles(ParticleTypes.CRIT, h.getX(), h.getY(), h.getZ(), 8, 0.2, 0.2, 0.2, 0.1);
    }
}
