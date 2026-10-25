package com.miscelanea.tinta;

import com.miscelanea.Miscelanea;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Forma de calamar (como en Splatoon): agachado con un arma de tinta en la mano.
 * - En tu tinta: nadas muy rápido, eres invisible, recargas el depósito a toda velocidad y subes por paredes pintadas.
 * - Fuera de tu tinta vas lento. La tinta de otro color te frena y te hace daño cuando vas de pie.
 * Corre en cliente y servidor (el movimiento del jugador lo calcula su cliente).
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class Calamar {
    private static final UUID NADO = UUID.fromString("2f7c6a10-5b3e-4c1d-9a8e-7b6c5d4e3f21");
    private static final UUID FRENO = UUID.fromString("2f7c6a10-5b3e-4c1d-9a8e-7b6c5d4e3f22");
    private static final Set<UUID> INVISIBLES = new HashSet<>();
    private static final Set<Integer> EN_TINTA_CLIENTE = new HashSet<>();

    private Calamar() {}

    public static boolean es(Player p) {
        return p.isCrouching() && !p.isUsingItem() && !p.isPassenger() && !p.getAbilities().flying && !p.isSpectator()
                && !ArmaTinta.enMano(p).isEmpty();
    }

    /** ¿Está el jugador en su tinta (suelo o pared que toca)? */
    public static boolean enSuTinta(Player p) {
        int c = ArmaTinta.colorJugador(p);
        if (c == 0) return false;
        if (Tinta.suelo(p.level(), p.position()) == c) return true;
        return paredPintada(p, c) != null;
    }

    /** Dirección de una pared pintada de 'c' que el jugador está tocando, o null. */
    public static Direction paredPintada(Player p, int c) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            double dx = d.getStepX() * (p.getBbWidth() / 2 + 0.05), dz = d.getStepZ() * (p.getBbWidth() / 2 + 0.05);
            for (double dy : new double[]{0.1, 0.8}) {
                BlockPos b = BlockPos.containing(p.getX() + dx, p.getY() + dy, p.getZ() + dz);
                if (Tinta.color(p.level(), b, d.getOpposite()) == c) return d;
            }
        }
        return null;
    }

    private static void modificador(Player p, UUID id, double valor, boolean activo) {
        AttributeInstance a = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (a == null) return;
        boolean tiene = a.getModifier(id) != null;
        if (activo && !tiene) a.addTransientModifier(new AttributeModifier(id, "tinta", valor, AttributeModifier.Operation.MULTIPLY_TOTAL));
        else if (!activo && tiene) a.removeModifier(id);
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Player p = e.player;
        int color = ArmaTinta.colorJugador(p);
        boolean calamar = es(p);
        boolean enTinta = calamar && enSuTinta(p);
        Direction pared = calamar ? paredPintada(p, color) : null;
        int suelo = Tinta.suelo(p.level(), p.position());
        boolean enemiga = color != 0 && suelo != 0 && suelo != color && !enTinta;

        modificador(p, NADO, 2.8, enTinta);
        modificador(p, FRENO, -0.55, enemiga);

        if (enTinta) {
            p.fallDistance = 0;
            // Trepar: empujando contra una pared pintada de tu color, subes
            if (pared != null && p.horizontalCollision) {
                Vec3 v = p.getDeltaMovement();
                p.setDeltaMovement(v.x, Math.max(v.y, 0.3), v.z);
            }
        }

        if (p.level().isClientSide) {
            boolean antes = EN_TINTA_CLIENTE.contains(p.getId());
            if (enTinta != antes) {
                if (enTinta) {
                    EN_TINTA_CLIENTE.add(p.getId());
                    p.level().playLocalSound(p.getX(), p.getY(), p.getZ(), SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.4F, 1.6F, false);
                } else EN_TINTA_CLIENTE.remove(p.getId());
            }
            if (enTinta && p.getDeltaMovement().horizontalDistanceSqr() > 0.003 && p.tickCount % 2 == 0) {
                Vector3f c = new Vector3f(((color >> 16) & 255) / 255F, ((color >> 8) & 255) / 255F, (color & 255) / 255F);
                p.level().addParticle(new DustParticleOptions(c, 1.2F), p.getX() + (p.getRandom().nextDouble() - 0.5) * 0.6,
                        p.getY() + 0.1, p.getZ() + (p.getRandom().nextDouble() - 0.5) * 0.6, 0, 0.05, 0);
                if (p.tickCount % 8 == 0)
                    p.level().playLocalSound(p.getX(), p.getY(), p.getZ(), SoundEvents.BUCKET_EMPTY, SoundSource.PLAYERS, 0.15F, 1.8F, false);
            }
            return;
        }
        ServerPlayer sp = (ServerPlayer) p;
        // Invisible mientras nadas en tu tinta
        if (enTinta && !p.isInvisible()) {
            p.setInvisible(true);
            INVISIBLES.add(p.getUUID());
        } else if (!enTinta && INVISIBLES.remove(p.getUUID())) {
            p.setInvisible(false);
        }
        // Recarga: muy rápida en tu tinta como calamar, muy lenta de pie
        if (color != 0 && !p.isUsingItem()) Deposito.recargar(sp, enTinta ? 1.7F : 0.12F);
        // Tinta enemiga: hace daño poco a poco (hasta dejarte con un poco de vida)
        if (enemiga && p.tickCount % 20 == 0 && p.getHealth() > 6.0F) {
            p.hurt(p.damageSources().magic(), 1.5F);
        }
    }
}
