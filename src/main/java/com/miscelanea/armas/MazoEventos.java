package com.miscelanea.armas;

import com.miscelanea.Miscelanea;
import com.miscelanea.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Golpes del Mazo de Wemmbu: golpe en caída con ráfaga, golpe sísmico al aterrizar en picado y el rayo. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class MazoEventos {
    private MazoEventos() {}

    /** Mientras el propio mazo reparte daño en área no cuenta como golpe cuerpo a cuerpo. */
    private static boolean interno;

    /** Con el mazo lanzado lo que queda en la mano está oculto: no puede hacer daño cuerpo a cuerpo. */
    @SubscribeEvent
    public static void ataque(AttackEntityEvent e) {
        ItemStack s = MazoItem.enMano(e.getEntity());
        if (s != null && MazoItem.lanzado(s)) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void golpe(LivingHurtEvent e) {
        if (interno || !(e.getSource().getEntity() instanceof Player p) || e.getSource().getDirectEntity() != p) return;
        ItemStack s = MazoItem.enMano(p);
        if (s == null || MazoItem.lanzado(s) || !MazoItem.esDueno(p, s) || !(p.level() instanceof ServerLevel sl)) return;
        MazoItem.sumarTrueno(p, s);
        float mult = com.miscelanea.ConfigServidor.mazoMult();
        if (p.isFallFlying() || p.fallDistance <= 1.5F) {
            if (mult != 1F) e.setAmount(e.getAmount() * mult);
            DanoArma.repartir(e, DanoArma.Arma.MAZO);
            return;
        }
        float caida = p.fallDistance;
        LivingEntity objetivo = e.getEntity();
        // golpe en caída del mazo + Densidad V (0,5 por nivel y bloque caído)
        float dano = e.getAmount() + MazoItem.extraCaida(caida) + MazoItem.DENSIDAD * 0.5F * caida;
        // Brecha IV: la armadura del objetivo protege un 15 % menos por nivel
        float armadura = objetivo.getArmorValue(), dureza = (float) objetivo.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS);
        if (armadura > 0) {
            float normal = net.minecraft.world.damagesource.CombatRules.getDamageAfterAbsorb(dano, armadura, dureza);
            float conBrecha = net.minecraft.world.damagesource.CombatRules.getDamageAfterAbsorb(dano, armadura * Math.max(0, 1 - 0.15F * MazoItem.BRECHA), dureza);
            if (normal > 0.01F) dano *= conBrecha / normal;
        }
        e.setAmount(dano * mult);
        DanoArma.repartir(e, DanoArma.Arma.MAZO);
        Vec3 c = objetivo.position();
        BlockState suelo = sl.getBlockState(objetivo.blockPosition().below());
        if (!suelo.isAir()) sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, suelo), c.x, c.y + 0.1, c.z, 60, 1.4, 0.1, 1.4, 0.2);
        sl.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.5, c.z, 1, 0, 0, 0, 0);
        sl.playSound(null, c.x, c.y, c.z, caida > 5 ? ModSounds.MAZO_GOLPE_FUERTE.get() : ModSounds.MAZO_GOLPE.get(), SoundSource.PLAYERS, 1.6F, 1F);
        empujar(sl, c, 3.5, p, objetivo, 0.35);
        TemblorPacket.enviar(sl, c, 12, Math.min(2.5F, caida * 0.2F), 12);
        // Ráfaga de viento III: te devuelve muy alto para encadenar otro golpe
        p.resetFallDistance();
        Vec3 v = p.getDeltaMovement();
        p.setDeltaMovement(v.x * 0.5, 0.95 + 0.5 * MazoItem.RAFAGA, v.z * 0.5);
        s.getOrCreateTag().putInt(MazoItem.ESTELA, 24);
        p.hurtMarked = true;
        sl.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.VIENTO_RAFAGA.get(), SoundSource.PLAYERS, 1.0F, 1.2F);
    }

    /** Aterrizaje en picado: golpe sísmico. Sin daño de caída. */
    @SubscribeEvent
    public static void caida(LivingFallEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        ItemStack s = MazoItem.enMano(p);
        if (s == null || !s.hasTag() || !s.getTag().getBoolean(MazoItem.PICADO) || !MazoItem.esDueno(p, s)) return;
        s.getTag().remove(MazoItem.PICADO);
        e.setDamageMultiplier(0);
        if (p.level() instanceof ServerLevel sl) sismo(sl, p, s, e.getDistance());
        p.stopUsingItem();
    }

    static void sismo(ServerLevel sl, Player p, ItemStack s, float caida) {
        Vec3 c = p.position();
        double radio = Math.min(8, 3 + caida / 4);
        float dano = (6 + Math.min(18, caida * 0.8F)) * com.miscelanea.ConfigServidor.mazoMult();
        BlockState suelo = sl.getBlockState(p.blockPosition().below());
        // onda de choque que se abre por el suelo
        sl.addFreshEntity(new OndaEntity(sl, c.x, c.y, c.z, (float) radio + 1));
        sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        sl.playSound(null, c.x, c.y, c.z, ModSounds.MAZO_SISMO.get(), SoundSource.PLAYERS, 2.5F, 1F);
        TemblorPacket.enviar(sl, c, radio * 3, 2.5F, 20);
        for (LivingEntity o : sl.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(radio, 2, radio), x -> x != p && x.isAlive() && !x.isSpectator() && afectable(p, x))) {
            double d = o.position().distanceTo(c);
            if (d > radio) continue;
            float f = (float) (1 - 0.6 * d / radio);
            interno = true;
            try {
                DanoArma.golpear(o, sl.damageSources().playerAttack(p), dano * f, DanoArma.Arma.MAZO);
            } finally {
                interno = false;
            }
            Vec3 emp = o.position().subtract(c);
            emp = emp.horizontalDistanceSqr() < 1e-4 ? Vec3.ZERO : new Vec3(emp.x, 0, emp.z).normalize();
            o.setDeltaMovement(o.getDeltaMovement().add(emp.scale(0.6 * f)).add(0, 0.9 * f, 0));
            o.hurtMarked = true;
            o.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
            MazoItem.sumarTrueno(p, s);
        }
        // rompe lo blando de alrededor: plantas, flores, nieve, hojas (respetando mobGriefing, modo aventura y protecciones)
        BlockPos b = p.blockPosition();
        int r = (int) Math.ceil(radio * 0.6);
        if (sl.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING) && p.mayBuild()) {
            for (BlockPos q : BlockPos.betweenClosed(b.offset(-r, -1, -r), b.offset(r, 1, r))) {
                if (q.distSqr(b) > r * r) continue;
                BlockState st = sl.getBlockState(q);
                if (!(st.is(BlockTags.REPLACEABLE_BY_TREES) || st.is(BlockTags.FLOWERS) || st.is(Blocks.SNOW) || st.is(BlockTags.LEAVES))) continue;
                BlockPos fija = q.immutable();
                if (!p.mayInteract(sl, fija) || MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(sl, fija, st, p))) continue;
                sl.destroyBlock(fija, true, p);
            }
        }
        if (MazoItem.trueno(s) >= MazoItem.MAX_TRUENO) {
            s.getOrCreateTag().putInt(MazoItem.TRUENO, 0);
            rayo(sl, c, p);
        }
        p.getCooldowns().addCooldown(s.getItem(), 40);
    }

    /** El rayo del mazo: cae en el punto, daña a los de alrededor (no al dueño ni a sus aliados) y los prende. */
    static void rayo(ServerLevel sl, Vec3 c, Player p) {
        LightningBolt l = EntityType.LIGHTNING_BOLT.create(sl);
        if (l != null) {
            l.moveTo(c.x, c.y, c.z);
            l.setVisualOnly(true); // solo visual: el daño lo aplicamos a mano a objetivos válidos
            if (p instanceof ServerPlayer sp) l.setCause(sp);
            sl.addFreshEntity(l);
        }
        sl.playSound(null, c.x, c.y, c.z, net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 4F, 1F);
        // daño de rayo atribuido al jugador (las muertes cuentan como suyas)
        DamageSource fuente = new DamageSource(sl.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.LIGHTNING_BOLT), p);
        for (LivingEntity o : sl.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(c, c).inflate(3), x -> x != p && x.isAlive() && !x.isSpectator() && afectable(p, x))) {
            o.invulnerableTime = 0;
            interno = true;
            try {
                DanoArma.golpear(o, fuente, 12F * com.miscelanea.ConfigServidor.mazoMult(), DanoArma.Arma.MAZO);
            } finally {
                interno = false;
            }
            o.setSecondsOnFire(4);
        }
        TemblorPacket.enviar(sl, c, 20, 2F, 15);
    }

    /** El daño en área del mazo no toca a las mascotas del dueño, ni aldeanos ni gólems. */
    static boolean afectable(Player p, LivingEntity o) {
        if (o instanceof OwnableEntity ow && p.getUUID().equals(ow.getOwnerUUID())) return false;
        return !(o instanceof AbstractVillager || o instanceof IronGolem || o instanceof SnowGolem);
    }

    static void empujar(ServerLevel sl, Vec3 c, double radio, Player p, LivingEntity aparte, double sube) {
        for (LivingEntity o : sl.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(c, c).inflate(radio), x -> x != p && x != aparte && x.isAlive())) {
            Vec3 d = o.position().subtract(c);
            double dist = d.length();
            if (dist > radio) continue;
            Vec3 emp = dist < 1e-3 ? Vec3.ZERO : d.normalize();
            double f = (radio - dist) * 0.25 + 0.3;
            o.setDeltaMovement(o.getDeltaMovement().add(emp.scale(f)).add(0, sube + f * 0.2, 0));
            o.hurtMarked = true;
        }
    }
}
