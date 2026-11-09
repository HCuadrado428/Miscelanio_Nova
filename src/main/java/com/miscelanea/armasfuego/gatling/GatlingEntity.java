package com.miscelanea.armasfuego.gatling;

import com.miscelanea.armasfuego.comun.ArmaFuegoItem;
import com.miscelanea.armasfuego.comun.ArmasFuegoRegistro;
import com.miscelanea.armasfuego.comun.Balistica;
import com.miscelanea.armasfuego.comun.EquilibrioArmas;
import com.miscelanea.logros.LogrosMiscelanea;
import com.miscelanea.registry.ModItems;
import com.miscelanea.registry.ModSounds;
import net.minecraft.ChatFormatting;
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
    /** La cinta que lleva puesta (normal, incendiaria o de palomitas). */
    private String cinta = "";

    public static final float GIRO_LADO = 55F, ARRIBA = -35F, ABAJO = 30F;
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
    }

    public boolean girando() { return entityData.get(GIRANDO); }
    public float calor() { return entityData.get(CALOR); }
    public int balas() { return entityData.get(BALAS); }
    public int atasco() { return entityData.get(ATASCO); }
    public int recarga() { return entityData.get(RECARGA); }
    public int oxido() { return entityData.get(OXIDO); }

    private int nivel(net.minecraftforge.registries.RegistryObject<net.minecraft.world.item.enchantment.Enchantment> e) {
        return arma.isEmpty() ? 0 : ArmaFuegoItem.nivel(e, arma);
    }

    /** Balas de una cinta (Cinta larga: la mitad más por nivel). */
    public int balasCinta() {
        return Math.round(EquilibrioArmas.gatlingCinta() * (1 + 0.5F * nivel(com.miscelanea.registry.ModEnchantments.GATLING_CINTA_LARGA)));
    }

    public static boolean esCinta(ItemStack s) {
        return s.is(ModItems.CINTA_GATLING.get()) || s.is(ModItems.CINTA_INCENDIARIA.get()) || s.is(ModItems.CINTA_PALOMITAS.get());
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

    public Player tirador() {
        return getFirstPassenger() instanceof Player p ? p : null;
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
        boolean gira = girando() && p != null && atasco() == 0 && recarga() == 0;
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

    /** Hacia dónde apunta (la vista del tirador, que ya va limitada, o al frente si no hay nadie). */
    public Vec3 mira() {
        Player p = tirador();
        return p != null ? p.getViewVector(1.0F) : Vec3.directionFromRotation(0, getYRot());
    }

    /** El eje del giro (donde se apoya en el trípode). */
    public Vec3 pivote() {
        return position().add(0, 1.05, 0);
    }

    public Vec3 boca(float adelante) {
        return pivote().add(mira().scale(adelante));
    }

    private void disparar(ServerLevel l, Player p) {
        if (balas() <= 0) {
            // se acabó la cinta: mete otra de la mochila del que la gira
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
        if (!p.getAbilities().instabuild) entityData.set(BALAS, balas() - 1);
        float c = calor();
        entityData.set(CALOR, c + EquilibrioArmas.gatlingCalor() * (1 - 0.2F * nivel(com.miscelanea.registry.ModEnchantments.GATLING_REFRIGERACION)));
        double disp = 0.025 + (c > 70 ? 0.03 : 0) + com.miscelanea.armasfuego.comun.Cobre.DISPERSION[oxido()];
        boolean fuego = cintaEs(ModItems.CINTA_INCENDIARIA), palomitas = cintaEs(ModItems.CINTA_PALOMITAS);
        Vec3 dir = Balistica.dispersar(mira(), disp, random);
        Vec3 boca = boca(1.45F);
        rafaga++;
        net.minecraft.core.particles.ParticleOptions estela = palomitas ? new net.minecraft.core.particles.ItemParticleOption(ParticleTypes.ITEM, new ItemStack(ModItems.PALOMITAS.get()))
                : fuego ? ParticleTypes.FLAME : rafaga % 4 == 0 ? ParticleTypes.CRIT : null;
        Balistica.Tiro t = Balistica.trazar(l, p, pivote(), dir, EquilibrioArmas.gatlingAlcance(), boca, palomitas || fuego || rafaga % 2 == 0 ? estela : null);
        if (t.entidad() != null) {
            LivingEntity v = t.entidad();
            if (palomitas) {
                // palomitas para el espectáculo: no hacen daño y al que le dan, le dan de comer
                if (v instanceof Player pj) pj.getFoodData().eat(1, 0.1F);
                l.sendParticles(new net.minecraft.core.particles.ItemParticleOption(ParticleTypes.ITEM, new ItemStack(ModItems.PALOMITAS.get())), t.pos().x, t.pos().y, t.pos().z, 4, 0.15, 0.15, 0.15, 0.08);
                if (p instanceof ServerPlayer sp) LogrosMiscelanea.dar(sp, "gatling_palomitas");
            } else {
                float d = EquilibrioArmas.gatlingDano() * Balistica.caida(t.distancia(), 20, EquilibrioArmas.gatlingAlcance(), 0.6F);
                Balistica.herir(v, p, d, 0.1F, DANO, true);
                if (fuego) v.setSecondsOnFire(3);
            }
        }
        Balistica.impacto(l, p, t);
        // fogonazo, casquillo (sale por abajo a la derecha) y ruido
        l.sendParticles(ArmasFuegoRegistro.FOGONAZO.get(), boca.x, boca.y, boca.z, 1, 0, 0, 0, 0);
        Vec3 m = mira();
        Vec3 der = new Vec3(-m.z, 0, m.x).normalize();
        Vec3 sale = pivote().add(der.scale(0.25)).add(0, -0.1, 0);
        Vec3 v = der.scale(0.14).add(0, 0.12, 0);
        l.sendParticles(ArmasFuegoRegistro.CASQUILLO.get(), sale.x, sale.y, sale.z, 0, v.x, v.y, v.z, 1.0);
        l.playSound(null, boca.x, boca.y, boca.z, ModSounds.GATLING_DISPARO.get(), SoundSource.PLAYERS, 3.5F, 0.9F + random.nextFloat() * 0.2F);
        l.gameEvent(p, GameEvent.PROJECTILE_SHOOT, boca);
        if (p instanceof ServerPlayer sp && rafaga >= 40) LogrosMiscelanea.dar(sp, "gatling_rafaga");
        // el cobre se oxida con el uso (una fase cada 400 balas, antes con lluvia)
        if (!arma.isEmpty() && com.miscelanea.armasfuego.comun.Cobre.desgastar(arma, 400, isInWaterOrRain())) {
            entityData.set(OXIDO, com.miscelanea.armasfuego.comun.Cobre.oxido(arma));
            if (p instanceof ServerPlayer sp) com.miscelanea.armasfuego.comun.Cobre.avisar(sp, arma);
        }
        // desgaste del arma (si se rompe, se acabó)
        if (!arma.isEmpty() && arma.isDamageableItem() && !p.getAbilities().instabuild && arma.hurt(1, random, p instanceof ServerPlayer sp ? sp : null)) {
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
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {
        t.putFloat("Calor", calor());
        t.putInt("Balas", balas());
        if (!arma.isEmpty()) t.put("Arma", arma.save(new CompoundTag()));
        t.putString("Cinta", cinta);
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
