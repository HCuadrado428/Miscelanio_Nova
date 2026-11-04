package com.miscelanea.tortura.muertes;

import com.miscelanea.ConfigServidor;
import com.miscelanea.Miscelanea;
import com.miscelanea.tortura.TrampaOso;
import com.miscelanea.tortura.aparatos.AparatoBlockEntity;
import com.miscelanea.tortura.aparatos.Aparatos;
import com.miscelanea.tortura.aparatos.AparatosRed;
import com.miscelanea.tortura.aparatos.AparatosRegistro;
import com.miscelanea.tortura.aparatos.DoncellaBlockEntity;
import com.miscelanea.tortura.aparatos.GanchoBlockEntity;
import com.miscelanea.tortura.aparatos.InmersionBlockEntity;
import com.miscelanea.tortura.aparatos.PenduloBlockEntity;
import com.miscelanea.tortura.aparatos.PotroBlockEntity;
import com.miscelanea.tortura.aparatos.SillaElectricaBlockEntity;
import com.miscelanea.tortura.aparatos.SillaInterrogatorioBlockEntity;
import com.miscelanea.tortura.efectos.Collares;
import com.miscelanea.tortura.efectos.EfectosRegistro;
import com.miscelanea.tortura.saw.CajaAgua;
import com.miscelanea.tortura.saw.SawRegistro;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.function.IntPredicate;

/**
 * Cinemáticas de muerte de los aparatos (la "ejecución" de la trampa de oso, generalizada). Cuando un aparato va a
 * matar ({@link Aparatos#danar} con daño mortal, {@link Aparatos#matar}, el collar al detonar), en vez de morir en el
 * acto:
 * <ul>
 *   <li>se guarda {@code miscelanea_muerte: {Tipo, Inicio, Dano, Captor, foco, frente, aux, X, Z}} en los datos
 *       persistentes de la víctima (así sobrevive a un reinicio: si al cargar ya ha pasado el tiempo, muere en el acto);</li>
 *   <li>se avisa a la víctima y a quien la ve ({@link MuertePacket}): el cliente pone la cámara de cine, la animación del
 *       cuerpo, los sonidos, las partículas y los efectos de pantalla (todo en el cliente, por datos: MuertesCliente);</li>
 *   <li>mientras dura no le afecta otro daño (salvo /kill y el vacío), no se mueve (si no va montada en el aparato), no
 *       usa nada; desconectarse o cambiar de dimensión la mata ya;</li>
 *   <li>al llegar a {@link TipoMuerte#muerte} muere con el daño del aparato (su mensaje, el captor como asesino, y la
 *       compatibilidad con Incapacitated de {@link Aparatos#matarYa}); el collar, con su explosión.</li>
 * </ul>
 * Con {@code [tortura] muertes_animadas = false} en la config del servidor todo vuelve a ser instantáneo.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class Muertes {
    private Muertes() {}

    public static final String CLAVE = "miscelanea_muerte";
    /** En el cliente: ¿esa entidad (id) está en plena cinemática? (lo pone MuertesCliente). */
    public static IntPredicate cliente = id -> false;

    // ------------------------------------------------------------------ consultas
    /** ¿Está en plena cinemática de muerte? (servidor: sus datos; cliente: lo recibido). */
    public static boolean en(@Nullable Entity e) {
        if (e == null) return false;
        if (e.level().isClientSide) return cliente.test(e.getId());
        return e.getPersistentData().contains(CLAVE);
    }

    /** Lo mismo por id de entidad (para el cliente). */
    public static boolean enId(int id) {
        return cliente.test(id);
    }

    // ------------------------------------------------------------------ empezar
    /**
     * Un aparato va a matar a 'v' con el daño 'k': si hay cinemática para ello, empieza y devuelve true (el aparato no
     * debe hacer el daño). Si ya estaba en una, también true (no hay más daño).
     */
    public static boolean intentar(LivingEntity v, ResourceKey<DamageType> k, @Nullable UUID captor) {
        if (!(v.level() instanceof ServerLevel sl)) return false;
        if (en(v)) return true;
        if (!ConfigServidor.muertesAnimadas() || !v.isAlive() || TrampaOso.ejecutando(v)) return false;
        if (v instanceof Player p && (p.isSpectator() || p.getAbilities().invulnerable)) return false;
        // con un tótem en la mano se salva como siempre (la cinemática es de una muerte segura)
        if (v.getItemInHand(InteractionHand.MAIN_HAND).is(Items.TOTEM_OF_UNDYING) || v.getItemInHand(InteractionHand.OFF_HAND).is(Items.TOTEM_OF_UNDYING))
            return false;
        AparatoBlockEntity be = Aparatos.de(v);
        TipoMuerte tipo = null;
        Vec3 foco = null, aux = null;
        float yaw = v.yBodyRot;
        if (k == AparatosRegistro.DANO_DONCELLA && be instanceof DoncellaBlockEntity d) {
            tipo = TipoMuerte.DONCELLA;
            foco = d.sitioVictima(v);
            yaw = d.yawVictima();
        } else if (k == AparatosRegistro.DANO_POTRO && be instanceof PotroBlockEntity p) {
            tipo = TipoMuerte.POTRO;
            Vec3 c = p.centroMesa();
            foco = new Vec3(c.x, p.getBlockPos().getY() + PotroBlockEntity.SUPERFICIE, c.z);
            yaw = p.yawVictima();
        } else if (k == AparatosRegistro.DANO_SILLA && be instanceof SillaElectricaBlockEntity s) {
            tipo = TipoMuerte.SILLA;
            foco = s.centro();
            yaw = s.yawVictima();
        } else if (k == AparatosRegistro.DANO_PENDULO && be instanceof PenduloBlockEntity p) {
            tipo = TipoMuerte.PENDULO;
            Vec3 c = p.centroMesa();
            foco = new Vec3(c.x, p.getBlockPos().getY() + PenduloBlockEntity.SUPERFICIE, c.z);
            yaw = p.yawVictima();
        } else if (k == AparatosRegistro.DANO_INMERSION && be instanceof InmersionBlockEntity i) {
            tipo = TipoMuerte.INMERSION;
            foco = v.getEyePosition();
            yaw = i.yawVictima();
        } else if ((k == AparatosRegistro.DANO_PARO || k == AparatosRegistro.DANO_INTERROGATORIO) && be instanceof SillaInterrogatorioBlockEntity s) {
            tipo = TipoMuerte.PARO;
            foco = s.centro();
            aux = s.monitor();
            yaw = s.yawVictima();
        } else if (k == AparatosRegistro.DANO_COLGADO && be instanceof GanchoBlockEntity) {
            tipo = TipoMuerte.COLGADO;
            foco = v.getBoundingBox().getCenter();
        } else if ((k == SawRegistro.DANO_CAJA || k == SawRegistro.DANO_CUCHILLAS) && CajaAgua.tiene(v)) {
            tipo = TipoMuerte.CAJA;
            foco = v.position();
        } else if (k == EfectosRegistro.DANO_COLLAR) {
            tipo = TipoMuerte.COLLAR;
            foco = v.position();
        } else if (k == com.miscelanea.dream.VenenoLetal.DANO) {
            // el veneno de Dream: en cualquier sitio, desde los pies y hacia donde mira (el cliente le fija el giro)
            if (v.isPassenger()) v.stopRiding();
            tipo = TipoMuerte.VENENO;
            foco = v.position();
            yaw = v.getYRot();
        }
        if (tipo == null) return false;
        empezar(v, sl, tipo, k, captor, foco, yaw, aux == null ? foco : aux);
        return true;
    }

    private static void empezar(LivingEntity v, ServerLevel sl, TipoMuerte tipo, ResourceKey<DamageType> k, @Nullable UUID captor,
                                Vec3 foco, float yaw, Vec3 aux) {
        CompoundTag d = new CompoundTag();
        d.putInt("Tipo", tipo.id);
        d.putLong("Inicio", sl.getGameTime());
        d.putString("Dano", k.location().toString());
        if (captor != null) d.putUUID("Captor", captor);
        d.putDouble("FX", foco.x);
        d.putDouble("FY", foco.y);
        d.putDouble("FZ", foco.z);
        d.putFloat("Yaw", yaw);
        d.putDouble("AX", aux.x);
        d.putDouble("AY", aux.y);
        d.putDouble("AZ", aux.z);
        d.putDouble("X", v.getX());
        d.putDouble("Z", v.getZ());
        v.getPersistentData().put(CLAVE, d);
        if (v.isUsingItem()) v.stopUsingItem();
        if (v instanceof ServerPlayer sp) {
            com.miscelanea.tortura.qte.Qte.cancelar(sp);
            if (sp.containerMenu != sp.inventoryMenu) sp.closeContainer();
            // el paro deja ciego: que vea su propia muerte
            sp.removeEffect(MobEffects.BLINDNESS);
            sp.removeEffect(MobEffects.DARKNESS);
        }
        if (v instanceof Mob m) {
            m.getNavigation().stop();
            m.setTarget(null);
        }
        alAparato(v, tipo, 0);
        AparatosRed.alrededor(v, paquete(v, d, 0));
    }

    private static MuertePacket paquete(LivingEntity v, CompoundTag d, int transcurrido) {
        return new MuertePacket(v.getId(), d.getInt("Tipo"), transcurrido, new Vec3(d.getDouble("FX"), d.getDouble("FY"), d.getDouble("FZ")),
                d.getFloat("Yaw"), new Vec3(d.getDouble("AX"), d.getDouble("AY"), d.getDouble("AZ")));
    }

    /** Lo que el propio aparato hace durante la cinemática (pararse, quedarse abajo, mantener el paro...). */
    private static void alAparato(LivingEntity v, TipoMuerte tipo, long t) {
        AparatoBlockEntity be = Aparatos.de(v);
        if (be == null) return;
        switch (tipo) {
            case PENDULO -> { if (t == 0 && be instanceof PenduloBlockEntity p) p.pararPorMuerte(); }
            case INMERSION -> { if (be instanceof InmersionBlockEntity i) i.hundirPorMuerte(); }
            case PARO -> { if (be instanceof SillaInterrogatorioBlockEntity s) s.paroPorMuerte(t == 0); }
            case COLGADO -> { if (be instanceof GanchoBlockEntity g) g.frenarPorMuerte(t); }
            default -> {}
        }
    }

    // ------------------------------------------------------------------ cada tick
    @SubscribeEvent
    public static void tickVivo(LivingEvent.LivingTickEvent e) {
        LivingEntity v = e.getEntity();
        if (v.level().isClientSide || !v.getPersistentData().contains(CLAVE)) return;
        CompoundTag d = v.getPersistentData().getCompound(CLAVE);
        TipoMuerte tipo = TipoMuerte.de(d.getInt("Tipo"));
        long t = v.level().getGameTime() - d.getLong("Inicio");
        if (tipo == null || t < 0 || t >= tipo.muerte || t > 400) {
            // (también: estado viejo tras un reinicio o un cambio de hora)
            terminar(v);
            return;
        }
        if (t > 0) alAparato(v, tipo, t);
        congelar(v, d);
    }

    /** Clavada en el sitio (si no va montada en el aparato, que ya la sujeta): sin moverse ni usar nada. */
    private static void congelar(LivingEntity v, CompoundTag d) {
        v.setSprinting(false);
        v.setJumping(false);
        if (v.isUsingItem()) v.stopUsingItem();
        if (v instanceof Mob m) {
            m.getNavigation().stop();
            m.setTarget(null);
            m.setXxa(0);
            m.setYya(0);
            m.setZza(0);
        }
        if (v.isPassenger()) return;
        Vec3 dm = v.getDeltaMovement();
        v.setDeltaMovement(0, Math.min(0, dm.y), 0);
        double ax = d.getDouble("X"), az = d.getDouble("Z");
        double dx = v.getX() - ax, dz = v.getZ() - az;
        if (dx * dx + dz * dz > 0.09) {
            if (v instanceof ServerPlayer sp) sp.connection.teleport(ax, sp.getY(), az, sp.getYRot(), sp.getXRot());
            else v.teleportTo(ax, v.getY(), az);
        }
    }

    /** Fin de la cinemática: muere con el daño del aparato (el collar, reventando). */
    public static void terminar(LivingEntity v) {
        if (!v.getPersistentData().contains(CLAVE)) return;
        CompoundTag d = v.getPersistentData().getCompound(CLAVE);
        v.getPersistentData().remove(CLAVE);
        if (!v.isAlive()) return;
        TipoMuerte tipo = TipoMuerte.de(d.getInt("Tipo"));
        if (tipo == TipoMuerte.COLLAR && Collares.tiene(v)) {
            Collares.explotarFinal(v);
            if (!v.isAlive()) return;
        }
        ResourceKey<DamageType> k;
        try {
            k = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(d.getString("Dano")));
        } catch (Exception ex) {
            k = AparatosRegistro.DANO_DONCELLA;
        }
        Aparatos.matarYa(v, k, d.hasUUID("Captor") ? d.getUUID("Captor") : null);
    }

    // ------------------------------------------------------------------ eventos
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void ataque(LivingAttackEvent e) {
        // en plena cinemática solo el final (o /kill, el vacío...) la mata
        if (!e.getEntity().level().isClientSide && en(e.getEntity()) && !e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY))
            e.setCanceled(true);
    }

    @SubscribeEvent
    public static void morir(LivingDeathEvent e) {
        LivingEntity v = e.getEntity();
        if (v.level().isClientSide || !en(v)) return;
        // muerta de otra forma (/kill...): se anula lo que quedaba
        v.getPersistentData().remove(CLAVE);
        AparatosRed.alrededor(v, MuertePacket.anular(v.getId()));
    }

    @SubscribeEvent
    public static void salir(PlayerEvent.PlayerLoggedOutEvent e) {
        // no se escapa nadie desconectándose: muere ya (antes de que se guarde)
        if (e.getEntity() instanceof ServerPlayer sp && en(sp)) terminar(sp);
    }

    @SubscribeEvent
    public static void cambiarDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (en(e.getEntity())) terminar(e.getEntity());
    }

    @SubscribeEvent
    public static void reaparecer(PlayerEvent.PlayerRespawnEvent e) {
        // el cuerpo nuevo tiene el mismo id: que todos vuelvan a dibujarlo normal
        e.getEntity().getPersistentData().remove(CLAVE);
        AparatosRed.alrededor(e.getEntity(), MuertePacket.anular(e.getEntity().getId()));
    }

    @SubscribeEvent
    public static void verEntidad(PlayerEvent.StartTracking e) {
        if (e.getTarget() instanceof LivingEntity le && en(le) && e.getEntity() instanceof ServerPlayer sp) {
            CompoundTag d = le.getPersistentData().getCompound(CLAVE);
            int t = (int) Math.max(0L, Math.min(10000L, le.level().getGameTime() - d.getLong("Inicio")));
            AparatosRed.a(sp, paquete(le, d, t));
        }
    }

    @SubscribeEvent
    public static void entrar(PlayerEvent.PlayerLoggedInEvent e) {
        // reinicio a mitad de cinemática: ya no se ve, muere en su primer tick
        if (en(e.getEntity()) && e.getEntity() instanceof ServerPlayer sp) {
            CompoundTag d = sp.getPersistentData().getCompound(CLAVE);
            d.putLong("Inicio", sp.level().getGameTime() - 1000);
        }
    }

    // ------------------------------------------------------------------ sin usar nada
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void clicEntidad(PlayerInteractEvent.EntityInteract e) {
        if (en(e.getEntity())) e.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void clicBloque(PlayerInteractEvent.RightClickBlock e) {
        if (en(e.getEntity())) e.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void clicObjeto(PlayerInteractEvent.RightClickItem e) {
        if (en(e.getEntity())) e.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void golpearBloque(PlayerInteractEvent.LeftClickBlock e) {
        if (en(e.getEntity())) e.setCanceled(true);
    }
}
