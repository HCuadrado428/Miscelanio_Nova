package com.miscelanea.armasfuego.gatling;

import com.miscelanea.armasfuego.comun.ArmaFuegoItem;
import com.miscelanea.armasfuego.comun.ArmasFuegoRegistro;
import com.miscelanea.armasfuego.comun.Balistica;
import com.miscelanea.armasfuego.comun.EquilibrioArmas;
import com.miscelanea.logros.LogrosMiscelanea;
import com.miscelanea.registry.ModItems;
import com.miscelanea.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

/**
 * La Gatling plantada en su trípode. Clic derecho: ponerse detrás (el jugador queda de pie a los mandos, con el giro
 * limitado a ±55° a los lados y de -35° a 30° arriba y abajo); mantener el clic derecho gira la manivela: los seis
 * cañones giran cada vez más deprisa y disparan. Se calienta con cada bala (humea y los cañones se ponen al rojo); si
 * llega al tope se atasca un rato y quema al que la gira. Se carga con cintas de balas (clic derecho con la cinta; o
 * sola, de la mochila del que la usa, al vaciarse), se enfría de golpe con un cubo de agua, y agachado + clic derecho
 * (o a golpes) se recoge.
 * <p>
 * Utilidad (1.1.0): una tolva encima o al lado le mete las cintas sola cuando se vacía; con señal de redstone y nadie a
 * los mandos, la manivela gira sola y dispara hacia donde se dejó apuntando (con Torreta, busca ella sola lo hostil que
 * tenga delante); y se puede montar en una vagoneta (clic derecho con la Gatling en la mano sobre una vagoneta vacía:
 * te subes a la Gatling y disparas desde la vagoneta) o en un barco (en el asiento de atrás: el que lleva el barco
 * dispara manteniendo el clic derecho). Todo se mira cada 10-20 ticks, nunca cada tick sin necesidad.
 */
public class GatlingEntity extends Entity {
    public static final ResourceKey<DamageType> DANO = Balistica.clave("gatling_disparo");
    private static final EntityDataAccessor<Boolean> GIRANDO = SynchedEntityData.defineId(GatlingEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> CALOR = SynchedEntityData.defineId(GatlingEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> BALAS = SynchedEntityData.defineId(GatlingEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ATASCO = SynchedEntityData.defineId(GatlingEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> RECARGA = SynchedEntityData.defineId(GatlingEntity.class, EntityDataSerializers.INT);
    /** Fase del cobre (0..3), para dibujarla. */
    private static final EntityDataAccessor<Integer> OXIDO = SynchedEntityData.defineId(GatlingEntity.class, EntityDataSerializers.INT);
    /** Hacia dónde apunta sin nadie a los mandos (giro y cabeceo, en grados), para la torreta y para dibujarla. */
    private static final EntityDataAccessor<Float> APUNTA_Y = SynchedEntityData.defineId(GatlingEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> APUNTA_X = SynchedEntityData.defineId(GatlingEntity.class, EntityDataSerializers.FLOAT);
    /** El que la plantó (a quien se le cuentan los tiros de la torreta). */
    private java.util.UUID dueno;
    /** Señal de redstone (se mira cada 10 ticks) y lo hostil que sigue la torreta. */
    private boolean encendida;
    private LivingEntity blanco;
    /** Para dibujarla: el apuntado del tick anterior. */
    public float apuntaYO, apuntaXO;
    /** La cinta que lleva puesta (normal, incendiaria o de palomitas). */
    private String cinta = "";

    public static final float GIRO_LADO = 55F, ARRIBA = -35F, ABAJO = 30F;
    private static final net.minecraft.core.particles.DustParticleOptions GUISANTE = new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.35F, 0.8F, 0.2F), 1.2F);
    public static final int T_ATASCO = 100, T_RECARGA = 30;
    /** Ángulos (grados) de la manivela y de los cañones, con los del tick anterior para interpolar al dibujar. */
    public float manivela, manivelaO, canones, canonesO;
    /** Velocidad de la manivela (0 parada .. 1 a tope). */
    public float velocidad;
    private float acumulado, golpes;
    private int rafaga, ultimoClic;
    private ItemStack arma = ItemStack.EMPTY;

    public GatlingEntity(EntityType<? extends GatlingEntity> t, Level l) {
        super(t, l);
        blocksBuilding = true;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(GIRANDO, false);
        entityData.define(CALOR, 0F);
        entityData.define(BALAS, 0);
        entityData.define(ATASCO, 0);
        entityData.define(RECARGA, 0);
        entityData.define(OXIDO, 0);
        entityData.define(APUNTA_Y, 0F);
        entityData.define(APUNTA_X, 0F);
    }

    public boolean girando() { return entityData.get(GIRANDO); }
    public float calor() { return entityData.get(CALOR); }
    public int balas() { return entityData.get(BALAS); }
    public int atasco() { return entityData.get(ATASCO); }
    public int recarga() { return entityData.get(RECARGA); }
    public int oxido() { return entityData.get(OXIDO); }
    public float apuntaY() { return entityData.get(APUNTA_Y); }
    public float apuntaX() { return entityData.get(APUNTA_X); }
    public boolean encendida() { return encendida; }

    public void ponerDueno(@javax.annotation.Nullable Player p) {
        dueno = p == null ? null : p.getUUID();
    }

    /** Apuntar sin nadie a los mandos (lo usa la prueba y el que la deja apuntada al bajarse). */
    public void apuntar(float y, float x) {
        entityData.set(APUNTA_Y, y);
        entityData.set(APUNTA_X, Mth.clamp(x, ARRIBA, ABAJO));
    }

    private int nivel(net.minecraftforge.registries.RegistryObject<net.minecraft.world.item.enchantment.Enchantment> e) {
        return arma.isEmpty() ? 0 : ArmaFuegoItem.nivel(e, arma);
    }

    /** Balas de una cinta (Cinta larga: la mitad más por nivel). */
    public int balasCinta() {
        return Math.round(EquilibrioArmas.gatlingCinta() * (1 + 0.5F * nivel(com.miscelanea.registry.ModEnchantments.GATLING_CINTA_LARGA)));
    }

    public static boolean esCinta(ItemStack s) {
        return s.is(ModItems.CINTA_GATLING.get()) || s.is(ModItems.CINTA_INCENDIARIA.get()) || s.is(ModItems.CINTA_PALOMITAS.get()) || s.is(ModItems.CINTA_GUISANTES.get());
    }

    private void ponerCinta(ItemStack s) {
        cinta = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(s.getItem()).toString();
    }

    private boolean cintaEs(net.minecraftforge.registries.RegistryObject<net.minecraft.world.item.Item> i) {
        return cinta.equals(i.getId().toString());
    }

    /** Para la prueba del CI: poner la fase del cobre. */
    public void ponerOxido(int o) {
        if (arma.isEmpty()) arma = new ItemStack(ModItems.GATLING.get());
        com.miscelanea.armasfuego.comun.Cobre.ponerOxido(arma, o);
        entityData.set(OXIDO, com.miscelanea.armasfuego.comun.Cobre.oxido(arma));
    }

    /** Para la prueba del CI: poner las balas de la cinta. */
    public void ponerBalasPrueba(int n) {
        entityData.set(BALAS, n);
        entityData.set(RECARGA, 0);
        if (cinta.isEmpty()) ponerCinta(new ItemStack(ModItems.CINTA_GATLING.get()));
    }

    /** Para la prueba del CI (y comandos): poner el calor. */
    public void ponerCalor(float c) {
        entityData.set(CALOR, Math.max(0, Math.min(100, c)));
    }

    public void ponerGirando(boolean g) {
        entityData.set(GIRANDO, g);
        if (!g) rafaga = 0;
    }

    /** Pone el arma (el objeto) que se ha plantado: guarda su desgaste, nombre y estado. */
    public void desde(ItemStack s) {
        arma = s.copyWithCount(1);
        CompoundTag t = s.getTag();
        if (t != null) {
            entityData.set(CALOR, t.getFloat(GatlingItem.CALOR));
            entityData.set(BALAS, t.getInt(GatlingItem.BALAS));
            cinta = t.getString(GatlingItem.CINTA);
        }
        entityData.set(OXIDO, com.miscelanea.armasfuego.comun.Cobre.oxido(arma));
    }

    /** El objeto con el estado de ahora (para recogerla). */
    public ItemStack comoObjeto() {
        ItemStack s = arma.isEmpty() ? new ItemStack(ModItems.GATLING.get()) : arma.copy();
        s.getOrCreateTag().putFloat(GatlingItem.CALOR, calor());
        s.getOrCreateTag().putInt(GatlingItem.BALAS, balas());
        s.getOrCreateTag().putString(GatlingItem.CINTA, cinta);
        return s;
    }

    /**
     * El que la dispara: el que va a los mandos o, si va montada en un barco (o lo que sea), el jugador que va con
     * ella (el que lleva el barco).
     */
    public Player tirador() {
        if (getFirstPassenger() instanceof Player p) return p;
        Entity v = getVehicle();
        if (v != null) for (Entity e : v.getPassengers()) if (e instanceof Player p) return p;
        return null;
    }

    /** La Gatling que maneja un jugador: la que monta, o la que va en su mismo barco o vagoneta. */
    @javax.annotation.Nullable
    public static GatlingEntity deJugador(Entity p) {
        Entity v = p.getVehicle();
        if (v instanceof GatlingEntity g) return g;
        if (v != null) for (Entity e : v.getPassengers()) if (e instanceof GatlingEntity g) return g;
        return null;
    }

    /** ¿El tirador va detrás de ella a los mandos (y no en el barco, al lado)? */
    public boolean aLosMandos() {
        return getFirstPassenger() instanceof Player;
    }

    @javax.annotation.Nullable
    private Player duenoJugador() {
        if (dueno == null || !(level() instanceof ServerLevel l)) return null;
        return l.getServer().getPlayerList().getPlayer(dueno);
    }

    // ------------------------------------------------------------------ tick

    @Override
    public void tick() {
        super.tick();
        // cae si no hay suelo debajo (y no se mueve por nada más)
        if (!isNoGravity()) {
            Vec3 v = getDeltaMovement();
            setDeltaMovement(v.x * 0.5, onGround() ? 0 : v.y - 0.04, v.z * 0.5);
            if (!onGround() || v.lengthSqr() > 1e-6) move(MoverType.SELF, getDeltaMovement());
        }
        Player p = tirador();
        if (!level().isClientSide) cadaRato(p);
        else {
            apuntaYO = apuntaY();
            apuntaXO = apuntaX();
        }
        Entity veh = getVehicle();
        if (veh != null) {
            // montada: mira hacia donde va la vagoneta o el barco
            float vy = veh instanceof net.minecraft.world.entity.vehicle.AbstractMinecart ? veh.getYRot() + 90F : veh.getYRot();
            setYRot(vy);
            yRotO = vy;
        }
        boolean gira = girando() && (level().isClientSide || p != null || encendida) && atasco() == 0 && recarga() == 0;
        velocidad = Mth.clamp(velocidad + (gira ? (nivel(com.miscelanea.registry.ModEnchantments.GATLING_ENGRASADA) > 0 ? 0.2F : 0.1F) : -0.07F), 0F, 1F);
        manivelaO = manivela;
        canonesO = canones;
        manivela += velocidad * 40F;
        canones += velocidad * 30F;
        if (manivela > 360F) { manivela -= 360F; manivelaO -= 360F; }
        if (canones > 360F) { canones -= 360F; canonesO -= 360F; }
        if (level().isClientSide) {
            humo();
            return;
        }
        ServerLevel l = (ServerLevel) level();
        // atasco y recarga
        if (atasco() > 0) {
            entityData.set(ATASCO, atasco() - 1);
            if (atasco() % 5 == 0) {
                Vec3 b = boca(1.2F);
                l.sendParticles(ParticleTypes.CLOUD, b.x, b.y + 0.1, b.z, 2, 0.15, 0.1, 0.15, 0.02);
            }
        }
        if (recarga() > 0) {
            entityData.set(RECARGA, recarga() - 1);
            if (recarga() == 0) {
                entityData.set(BALAS, balasCinta());
                playSound(ModSounds.GATLING_CINTA.get(), 0.9F, 1.2F);
            }
        }
        // la manivela suena con cada vuelta
        if (velocidad > 0.05F && (int) (manivela / 90F) != (int) (manivelaO / 90F))
            l.playSound(null, getX(), getY(), getZ(), ModSounds.GATLING_MANIVELA.get(), SoundSource.PLAYERS, 0.5F, 0.8F + velocidad * 0.5F);
        // disparos: con la manivela a tope, uno cada "cadencia" ticks
        boolean disparando = false;
        if (gira && velocidad > 0.35F) {
            acumulado += velocidad;
            int cad = EquilibrioArmas.gatlingCadencia();
            while (acumulado >= cad) {
                acumulado -= cad;
                disparando = true;
                disparar(l, p);
                if (isRemoved()) return;
            }
        } else acumulado = 0;
        // calor
        float c = calor();
        float enf = EquilibrioArmas.gatlingEnfriado() * (disparando ? 0.5F : 1F) * (isInWater() ? 3F : isInWaterOrRain() ? 1.5F : 1F);
        c = Math.max(0, c - enf);
        if (c >= 100F && atasco() == 0) {
            // al rojo: se atasca y quema al que la gira
            entityData.set(ATASCO, T_ATASCO);
            ponerGirando(false);
            playSound(ModSounds.GATLING_VAPOR.get(), 1.2F, 0.8F);
            playSound(ModSounds.ARMAFUEGO_ATASCO.get(), 1.0F, 0.6F);
            Vec3 b = boca(1.0F);
            l.sendParticles(ParticleTypes.LARGE_SMOKE, b.x, b.y, b.z, 12, 0.3, 0.2, 0.3, 0.03);
            if (p != null) {
                p.setSecondsOnFire(3);
                p.displayClientMessage(Component.translatable("msg.miscelanea.gatling.al_rojo").withStyle(ChatFormatting.RED), true);
                if (p instanceof ServerPlayer sp) LogrosMiscelanea.dar(sp, "gatling_calor");
            }
        }
        entityData.set(CALOR, Math.min(100F, c));
    }

    /** Humo de los cañones calientes (solo en el cliente: no gasta red). */
    private void humo() {
        float c = calor();
        if (c < 40F) return;
        int cada = c > 85 ? 1 : c > 65 ? 3 : 6;
        if (tickCount % cada != 0) return;
        Vec3 b = boca(0.9F + random.nextFloat() * 0.4F);
        level().addParticle(c > 85 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE, b.x, b.y + 0.05, b.z, 0, 0.03, 0);
    }

    /**
     * Lo que se mira cada 10 ticks (no cada tick): la señal de redstone y la torreta. Cada 20, la tolva si está vacía.
     * Con alguien a los mandos, el apuntado sigue su vista (así, al bajarse, se queda apuntando ahí).
     */
    private void cadaRato(@javax.annotation.Nullable Player p) {
        if (p != null) {
            Vec3 m = mira();
            apuntar((float) Math.toDegrees(Math.atan2(-m.x, m.z)), (float) Math.toDegrees(-Math.asin(Mth.clamp(m.y, -1, 1))));
        }
        int t = tickCount + getId();
        if (t % 10 == 0) {
            boolean antes = encendida;
            BlockPos b = blockPosition();
            encendida = p == null && EquilibrioArmas.gatlingRedstone() && (level().hasNeighborSignal(b) || level().hasNeighborSignal(b.below()));
            if (encendida != antes || encendida && !girando() && atasco() == 0) ponerGirando(encendida);
            if (encendida && nivel(com.miscelanea.registry.ModEnchantments.GATLING_TORRETA) > 0) buscarBlanco();
            else blanco = null;
        }
        if (encendida && blanco != null) {
            // la torreta sigue a su blanco (Portal: «¿sigues ahí?»)
            if (!blanco.isAlive() || blanco.distanceToSqr(this) > Mth.square(EquilibrioArmas.gatlingTorretaAlcance() + 4)) blanco = null;
            else {
                Vec3 d = blanco.getBoundingBox().getCenter().subtract(pivote());
                float y = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
                float x = (float) Math.toDegrees(-Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
                apuntar(getYRot() + Mth.clamp(Mth.wrapDegrees(y - getYRot()), -GIRO_LADO, GIRO_LADO), x);
            }
        }
        if (t % 20 == 0 && balas() <= 0 && recarga() == 0) tolva();
        // en un barco: el que lo lleva tiene que ir delante (si se subió después, la Gatling se pasa atrás)
        if (t % 10 == 5 && getVehicle() instanceof net.minecraft.world.entity.vehicle.Boat barco && barco.getPassengers().size() > 1
                && barco.getPassengers().get(0) == this) {
            stopRiding();
            startRiding(barco, true);
        }
    }

    /** Torreta: lo hostil más cerca que tenga delante (dentro del giro del trípode) y a la vista. */
    private void buscarBlanco() {
        double r = EquilibrioArmas.gatlingTorretaAlcance();
        Vec3 o = pivote();
        LivingEntity mejor = null;
        double md = Double.MAX_VALUE;
        for (LivingEntity v : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(r),
                e -> e instanceof net.minecraft.world.entity.monster.Enemy && e.isAlive() && !e.isInvisible())) {
            Vec3 d = v.getBoundingBox().getCenter().subtract(o);
            double dd = d.lengthSqr();
            if (dd > r * r || dd >= md) continue;
            float y = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
            if (Math.abs(Mth.wrapDegrees(y - getYRot())) > GIRO_LADO) continue;
            var hit = level().clip(new net.minecraft.world.level.ClipContext(o, v.getBoundingBox().getCenter(), net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, this));
            if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) continue;
            mejor = v;
            md = dd;
        }
        blanco = mejor;
    }

    /** Una tolva encima o al lado (o debajo) con cintas: mete una (cuando se ha vaciado). */
    private boolean tolva() {
        BlockPos b = blockPosition();
        for (BlockPos q : new BlockPos[]{b.above(), b.north(), b.south(), b.east(), b.west(), b.below(), b.above().north(), b.above().south(), b.above().east(), b.above().west()}) {
            if (!(level().getBlockEntity(q) instanceof net.minecraft.world.level.block.entity.HopperBlockEntity h)) continue;
            for (int i = 0; i < h.getContainerSize(); i++) {
                ItemStack c = h.getItem(i);
                if (!esCinta(c)) continue;
                ponerCinta(c);
                h.removeItem(i, 1);
                h.setChanged();
                entityData.set(RECARGA, T_RECARGA);
                playSound(ModSounds.GATLING_CINTA.get(), 0.9F, 0.9F);
                if (duenoJugador() instanceof ServerPlayer sp) LogrosMiscelanea.dar(sp, "gatling_tolva");
                return true;
            }
        }
        return false;
    }

    /** Hacia dónde apunta: la vista del tirador (limitada al giro del trípode) o, sin nadie, donde se dejó. */
    public Vec3 mira() {
        Player p = tirador();
        if (p == null) return Vec3.directionFromRotation(apuntaX(), apuntaY());
        if (aLosMandos()) return p.getViewVector(1.0F);
        // desde el barco: la vista del que lo lleva, sin pasarse del giro del trípode
        float y = getYRot() + Mth.clamp(Mth.wrapDegrees(p.getYRot() - getYRot()), -GIRO_LADO, GIRO_LADO);
        return Vec3.directionFromRotation(Mth.clamp(p.getXRot(), ARRIBA, ABAJO), y);
    }

    /** El eje del giro (donde se apoya en el trípode). */
    public Vec3 pivote() {
        return position().add(0, 1.05, 0);
    }

    public Vec3 boca(float adelante) {
        return pivote().add(mira().scale(adelante));
    }

    private void disparar(ServerLevel l, @javax.annotation.Nullable Player p) {
        if (balas() <= 0) {
            // se acabó la cinta: primero de la tolva; si no, de la mochila del que la gira
            if (tolva()) return;
            if (p == null) {
                if (tickCount - ultimoClic > 10) {
                    ultimoClic = tickCount;
                    playSound(ModSounds.REVOLVER_VACIO.get(), 0.8F, 0.7F);
                }
                return;
            }
            // de la misma que llevaba si hay; si no, cualquiera
            String antes = cinta;
            ItemStack c = ArmaFuegoItem.buscar(p, x -> esCinta(x) && net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(x.getItem()).toString().equals(antes));
            if (c.isEmpty()) c = ArmaFuegoItem.buscar(p, GatlingEntity::esCinta);
            if (!c.isEmpty() || p.getAbilities().instabuild) {
                if (!c.isEmpty()) ponerCinta(c);
                if (!p.getAbilities().instabuild) c.shrink(1);
                entityData.set(RECARGA, T_RECARGA);
                playSound(ModSounds.GATLING_CINTA.get(), 0.9F, 0.9F);
                return;
            }
            if (tickCount - ultimoClic > 10) {
                ultimoClic = tickCount;
                playSound(ModSounds.REVOLVER_VACIO.get(), 0.8F, 0.7F);
                p.displayClientMessage(Component.translatable("msg.miscelanea.gatling.sin_cinta").withStyle(ChatFormatting.GRAY), true);
            }
            return;
        }
        boolean creativo = p != null && p.getAbilities().instabuild;
        // el que dispara: el de los mandos o, en la torreta, el que la plantó (si está) o la propia Gatling
        Player duenoP = p != null ? p : duenoJugador();
        Entity quien = duenoP != null ? duenoP : this;
        if (!creativo) entityData.set(BALAS, balas() - 1);
        float c = calor();
        entityData.set(CALOR, c + EquilibrioArmas.gatlingCalor() * (1 - 0.2F * nivel(com.miscelanea.registry.ModEnchantments.GATLING_REFRIGERACION)));
        double disp = 0.025 + (c > 70 ? 0.03 : 0) + com.miscelanea.armasfuego.comun.Cobre.dispersion(oxido());
        boolean fuego = cintaEs(ModItems.CINTA_INCENDIARIA), palomitas = cintaEs(ModItems.CINTA_PALOMITAS), guisantes = cintaEs(ModItems.CINTA_GUISANTES);
        Vec3 dir = Balistica.dispersar(mira(), disp, random);
        Vec3 boca = boca(1.45F);
        rafaga++;
        net.minecraft.core.particles.ParticleOptions estela = palomitas ? new net.minecraft.core.particles.ItemParticleOption(ParticleTypes.ITEM, new ItemStack(ModItems.PALOMITAS.get()))
                : guisantes ? GUISANTE : fuego ? ParticleTypes.FLAME : rafaga % 4 == 0 ? ParticleTypes.CRIT : null;
        Balistica.Tiro t = Balistica.trazar(l, p != null ? p : this, pivote(), dir, EquilibrioArmas.gatlingAlcance(), boca, palomitas || fuego || guisantes || rafaga % 2 == 0 ? estela : null);
        if (t.entidad() != null) {
            LivingEntity v = t.entidad();
            if (palomitas) {
                // palomitas para el espectáculo: no hacen daño y al que le dan, le dan de comer
                if (v instanceof Player pj) pj.getFoodData().eat(1, 0.1F);
                l.sendParticles(new net.minecraft.core.particles.ItemParticleOption(ParticleTypes.ITEM, new ItemStack(ModItems.PALOMITAS.get())), t.pos().x, t.pos().y, t.pos().z, 4, 0.15, 0.15, 0.15, 0.08);
                if (duenoP instanceof ServerPlayer sp) LogrosMiscelanea.dar(sp, "gatling_palomitas");
            } else {
                float d = EquilibrioArmas.gatlingDano() * Balistica.caida(t.distancia(), 20, EquilibrioArmas.gatlingAlcance(), 0.6F)
                        * com.miscelanea.armasfuego.comun.Cobre.dano(oxido());
                boolean zombi = v instanceof net.minecraft.world.entity.monster.Zombie;
                // la cinta de guisantes (la Gatling Pea de Plantas contra zombis): poco daño y mucho empujón... salvo a los zombis
                if (guisantes) d *= zombi ? 2.5F : 0.5F;
                Balistica.herir(v, quien, d, Math.min(1F, 0.1F + com.miscelanea.armasfuego.comun.Cobre.perforacion(oxido())), DANO, true);
                if (fuego) v.setSecondsOnFire(3);
                if (guisantes) {
                    Vec3 m0 = mira();
                    v.push(m0.x * 0.25, 0.05, m0.z * 0.25);
                    v.hurtMarked = true;
                    l.sendParticles(GUISANTE, t.pos().x, t.pos().y, t.pos().z, 4, 0.1, 0.1, 0.1, 0.05);
                    if (zombi && !v.isAlive() && duenoP instanceof ServerPlayer sp) LogrosMiscelanea.dar(sp, "gatling_guisantes");
                }
                // la torreta (sin nadie a los mandos) le ha dado a algo: «¿sigues ahí?»
                if (p == null && duenoP instanceof ServerPlayer sp && v instanceof net.minecraft.world.entity.monster.Enemy) LogrosMiscelanea.dar(sp, "gatling_torreta");
            }
        }
        Balistica.impacto(l, quien, t);
        // fogonazo, casquillo (sale por abajo a la derecha) y ruido
        l.sendParticles(ArmasFuegoRegistro.FOGONAZO.get(), boca.x, boca.y, boca.z, 1, 0, 0, 0, 0);
        Vec3 m = mira();
        Vec3 der = new Vec3(-m.z, 0, m.x).normalize();
        Vec3 sale = pivote().add(der.scale(0.25)).add(0, -0.1, 0);
        Vec3 v = der.scale(0.14).add(0, 0.12, 0);
        l.sendParticles(ArmasFuegoRegistro.CASQUILLO.get(), sale.x, sale.y, sale.z, 0, v.x, v.y, v.z, 1.0);
        l.playSound(null, boca.x, boca.y, boca.z, ModSounds.GATLING_DISPARO.get(), SoundSource.PLAYERS, 3.5F, 0.9F + random.nextFloat() * 0.2F);
        l.gameEvent(quien, GameEvent.PROJECTILE_SHOOT, boca);
        if (p instanceof ServerPlayer sp && rafaga >= 40) LogrosMiscelanea.dar(sp, "gatling_rafaga");
        if (p instanceof ServerPlayer sp && getVehicle() instanceof net.minecraft.world.entity.vehicle.AbstractMinecart) LogrosMiscelanea.dar(sp, "gatling_vagoneta");
        if (p instanceof ServerPlayer sp && getVehicle() instanceof net.minecraft.world.entity.vehicle.Boat) LogrosMiscelanea.dar(sp, "gatling_barco");
        // el cobre se oxida con el uso (una fase cada 400 balas, antes con lluvia)
        if (!arma.isEmpty()) {
            int que = com.miscelanea.armasfuego.comun.Cobre.gastar(arma, 400, isInWaterOrRain());
            entityData.set(OXIDO, com.miscelanea.armasfuego.comun.Cobre.oxido(arma));
            if (duenoP instanceof ServerPlayer sp) com.miscelanea.armasfuego.comun.Cobre.avisar(sp, arma, que);
        }
        // desgaste del arma (si se rompe, se acabó)
        if (!arma.isEmpty() && arma.isDamageableItem() && !creativo && arma.hurt(1, random, p instanceof ServerPlayer sp ? sp : null)) {
            playSound(SoundEvents.ITEM_BREAK, 1.0F, 0.6F);
            ejectPassengers();
            discard();
        }
    }

    // ------------------------------------------------------------------ montar, cargar, recoger

    @Override
    public InteractionResult interact(Player p, InteractionHand hand) {
        ItemStack h = p.getItemInHand(hand);
        if (esCinta(h) && (balas() < balasCinta() || !net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(h.getItem()).toString().equals(cinta)) && recarga() == 0) {
            if (!level().isClientSide) {
                ponerCinta(h);
                entityData.set(BALAS, 0);
                if (!p.getAbilities().instabuild) h.shrink(1);
                entityData.set(RECARGA, T_RECARGA);
                playSound(ModSounds.GATLING_CINTA.get(), 0.9F, 0.9F);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (h.is(Items.WATER_BUCKET) && calor() > 1F) {
            if (!level().isClientSide) {
                entityData.set(CALOR, 0F);
                entityData.set(ATASCO, 0);
                playSound(SoundEvents.FIRE_EXTINGUISH, 1.0F, 0.8F);
                playSound(ModSounds.GATLING_VAPOR.get(), 1.0F, 1.2F);
                Vec3 b = boca(1.0F);
                ((ServerLevel) level()).sendParticles(ParticleTypes.CLOUD, b.x, b.y, b.z, 20, 0.4, 0.3, 0.4, 0.05);
                if (!p.getAbilities().instabuild) p.setItemInHand(hand, new ItemStack(Items.BUCKET));
                if (p instanceof ServerPlayer sp) LogrosMiscelanea.dar(sp, "gatling_cubo");
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        // panal / hacha: encerar o rascar el cobre de la Gatling plantada
        if ((com.miscelanea.armasfuego.comun.Cobre.esPanal(h) || com.miscelanea.armasfuego.comun.Cobre.esHacha(h)) && !arma.isEmpty()) {
            if (!level().isClientSide) {
                Vec3 b = pivote();
                boolean ok = com.miscelanea.armasfuego.comun.Cobre.esPanal(h) ? com.miscelanea.armasfuego.comun.Cobre.encerar(p, arma, h, b)
                        : com.miscelanea.armasfuego.comun.Cobre.rascar(p, arma, h, b);
                entityData.set(OXIDO, com.miscelanea.armasfuego.comun.Cobre.oxido(arma));
                if (!ok) return InteractionResult.PASS;
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (p.isSecondaryUseActive()) {
            if (isVehicle()) return InteractionResult.PASS;
            if (!level().isClientSide) recoger(p);
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (isVehicle()) return InteractionResult.PASS;
        if (getVehicle() instanceof net.minecraft.world.entity.vehicle.Boat barco) {
            // en un barco no se monta en ella: se sube al barco y la dispara desde ahí
            if (!level().isClientSide && p.getVehicle() != barco) p.startRiding(barco);
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (!level().isClientSide) {
            p.setYRot(getYRot());
            p.setXRot(0);
            p.startRiding(this);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    private void recoger(Player p) {
        ItemStack s = comoObjeto();
        if (!p.getAbilities().instabuild || !p.getInventory().contains(s)) {
            if (!p.getInventory().add(s)) spawnAtLocation(s);
        }
        playSound(SoundEvents.ARMOR_EQUIP_IRON, 1.0F, 0.8F);
        discard();
    }

    @Override
    public boolean hurt(DamageSource src, float n) {
        if (isInvulnerableTo(src) || level().isClientSide || isRemoved()) return !level().isClientSide;
        if (src.getEntity() instanceof Player p && p.getAbilities().instabuild) {
            discard();
            return true;
        }
        golpes += n;
        playSound(SoundEvents.ANVIL_HIT, 0.4F, 1.6F);
        if (golpes >= 10F) {
            ejectPassengers();
            spawnAtLocation(comoObjeto());
            discard();
        }
        return true;
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    // ------------------------------------------------------------------ el que la usa

    @Override
    protected void positionRider(Entity e, MoveFunction f) {
        if (!hasPassenger(e)) return;
        Vec3 fr = Vec3.directionFromRotation(0, getYRot());
        f.accept(e, getX() - fr.x * 0.95, getY(), getZ() - fr.z * 0.95);
        limitar(e);
    }

    @Override
    public void onPassengerTurned(Entity e) {
        limitar(e);
    }

    /** El giro limitado del trípode. */
    private void limitar(Entity e) {
        float base = getYRot();
        e.setYBodyRot(base);
        float f = Mth.wrapDegrees(e.getYRot() - base);
        float g = Mth.clamp(f, -GIRO_LADO, GIRO_LADO);
        e.yRotO += g - f;
        e.setYRot(e.getYRot() + g - f);
        e.setYHeadRot(e.getYRot());
        float x = Mth.clamp(e.getXRot(), ARRIBA, ABAJO);
        e.xRotO += x - e.getXRot();
        e.setXRot(x);
    }

    @Override
    public boolean shouldRiderSit() {
        return false;
    }

    @Override
    protected void removePassenger(Entity e) {
        super.removePassenger(e);
        ponerGirando(false);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity e) {
        Vec3 fr = Vec3.directionFromRotation(0, getYRot());
        return position().subtract(fr.scale(1.2));
    }

    @Override
    protected boolean canAddPassenger(Entity e) {
        return getPassengers().isEmpty() && e instanceof Player;
    }

    // ------------------------------------------------------------------ guardar

    @Override
    protected void readAdditionalSaveData(CompoundTag t) {
        entityData.set(CALOR, t.getFloat("Calor"));
        entityData.set(BALAS, t.getInt("Balas"));
        if (t.contains("Arma")) arma = ItemStack.of(t.getCompound("Arma"));
        cinta = t.getString("Cinta");
        entityData.set(OXIDO, com.miscelanea.armasfuego.comun.Cobre.oxido(arma));
        if (t.hasUUID("Dueno")) dueno = t.getUUID("Dueno");
        apuntar(t.getFloat("ApuntaY"), t.getFloat("ApuntaX"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {
        t.putFloat("Calor", calor());
        t.putInt("Balas", balas());
        if (!arma.isEmpty()) t.put("Arma", arma.save(new CompoundTag()));
        t.putString("Cinta", cinta);
        if (dueno != null) t.putUUID("Dueno", dueno);
        t.putFloat("ApuntaY", apuntaY());
        t.putFloat("ApuntaX", apuntaX());
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(ModItems.GATLING.get());
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
