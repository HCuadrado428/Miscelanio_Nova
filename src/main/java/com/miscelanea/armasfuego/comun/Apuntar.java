package com.miscelanea.armasfuego.comun;

import com.miscelanea.armasfuego.gatling.GatlingEntity;
import com.miscelanea.armasfuego.pistola.RevolverItem;
import com.miscelanea.armasfuego.rifle.RifleItem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Apuntar con el clic derecho mantenido (todas las armas menos la escopeta de presión, que dispara solo desde la
 * cadera). El que apunta anda más lento, dispersa menos y, en el cliente, se le cierra el campo de visión y el arma se
 * alinea con el centro de la pantalla.
 * <p>
 * El estado es un modificador de la velocidad de movimiento con un id fijo: el servidor lo pone y lo quita (lo pide el
 * cliente con {@link ApuntarPacket}), y como los atributos se mandan a todos los que ven al jugador, los demás clientes
 * saben que apunta (para la pose de los brazos) sin más paquetes. El rifle apunta con su catalejo (usar el objeto) y la
 * Gatling plantada con sus mandos: {@link #apuntando} lo junta todo.
 */
public final class Apuntar {
    private Apuntar() {}

    public static final UUID ID = UUID.fromString("5b1f0c0e-3a57-4a3c-9d53-c0bb3e51a001");

    /** Los que apuntan (servidor): se repasan cada tick por si han cambiado de objeto. */
    private static final Set<UUID> APUNTANDO = new HashSet<>();
    /** Cuánto llevan apuntando sin moverse ni mover la mira (servidor): {giro, cabeceo, x, z, ticks}. */
    private static final java.util.Map<UUID, float[]> QUIETO = new java.util.HashMap<>();
    /** Ticks quieto apuntando para que cuente como «quieto» (1,5 s): Dead Eye de la Winchester. */
    public static final int TICKS_QUIETO = 30;

    /** ¿Se apunta con este objeto con el modificador? (el rifle va por su catalejo; la escopeta no apunta) */
    public static boolean apuntable(ItemStack s) {
        return s.getItem() instanceof ArmaFuegoItem || s.getItem() instanceof RevolverItem;
    }

    /**
     * ¿Apunta por la mira con el clic derecho? Todas las apuntables menos las que van siempre a la cadera: el lanzallamas
     * (chorro en cono), el cañón de mano (tiro en parábola, con su curva en la mira) y la Derringer (a quemarropa).
     */
    public static boolean conMira(ItemStack s) {
        var i = s.getItem();
        return apuntable(s) && !(i instanceof com.miscelanea.armasfuego.nether.LanzallamasItem)
                && !(i instanceof com.miscelanea.armasfuego.oeste.CanonManoItem) && !(i instanceof com.miscelanea.armasfuego.oeste.DerringerItem);
    }

    public static boolean apuntando(Player p) {
        GatlingEntity g = GatlingEntity.deJugador(p);
        if (g != null) return g.apuntando();
        ItemStack s = p.getMainHandItem();
        if (s.getItem() instanceof RifleItem) return p.isUsingItem() && p.getUsedItemHand() == InteractionHand.MAIN_HAND;
        if (!conMira(s)) return false;
        AttributeInstance a = p.getAttribute(Attributes.MOVEMENT_SPEED);
        return a != null && a.getModifier(ID) != null;
    }

    /** Pone o quita el apuntado (en el servidor, y en el cliente para no esperar a que llegue el atributo). */
    public static void poner(Player p, boolean si) {
        AttributeInstance a = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (a == null) return;
        if (a.getModifier(ID) != null) a.removeModifier(ID);
        boolean vale = si && conMira(p.getMainHandItem()) && !p.isUsingItem() && !p.isSpectator();
        if (vale) a.addTransientModifier(new AttributeModifier(ID, "miscelanea.apuntar", -lentitud(p), AttributeModifier.Operation.MULTIPLY_TOTAL));
        if (!p.level().isClientSide) {
            if (vale) APUNTANDO.add(p.getUUID());
            else APUNTANDO.remove(p.getUUID());
        }
    }

    /** El nivel de Pulso firme del arma de la mano (0 si no es de las que apuntan con el modificador). */
    public static int pulso(Player p) {
        ItemStack s = p.getMainHandItem();
        return apuntable(s) ? net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(com.miscelanea.registry.ModEnchantments.PULSO_FIRME.get(), s) : 0;
    }

    /** Cuánto frena apuntar (la config; Pulso firme lo reduce un 25 % por nivel). */
    public static double lentitud(Player p) {
        return EquilibrioArmas.apuntarLentitud() * (1 - 0.25 * pulso(p));
    }

    /** Por cuánto se multiplica la dispersión del que dispara (1 desde la cadera; Pulso firme la baja un 20 % por nivel). */
    public static double dispersion(Player p) {
        return (apuntando(p) ? EquilibrioArmas.apuntarDispersion() * (1 - 0.2 * pulso(p)) : 1.0) * com.miscelanea.ropa.Ropa.dispersion(p);
    }

    /** ¿Lleva apuntando quieto 1,5 s (sin andar y sin mover la mira)? (servidor) */
    public static boolean quieto(Player p) {
        float[] q = QUIETO.get(p.getUUID());
        return q != null && q[4] >= TICKS_QUIETO;
    }

    /** Cada tick del servidor: el que ya no lleva el arma en la mano (o se ha puesto a recargar) deja de apuntar. */
    public static void tick(MinecraftServer srv) {
        if (APUNTANDO.isEmpty()) {
            QUIETO.clear();
            return;
        }
        QUIETO.keySet().retainAll(APUNTANDO);
        for (UUID id : APUNTANDO.toArray(new UUID[0])) {
            ServerPlayer p = srv.getPlayerList().getPlayer(id);
            if (p == null) APUNTANDO.remove(id);
            else if (!conMira(p.getMainHandItem()) || p.isUsingItem() || !p.isAlive()) poner(p, false);
            else {
                // quieto: ni andar (cambio de posición) ni mover la mira de un tick al siguiente
                float[] q = QUIETO.computeIfAbsent(id, k -> new float[]{p.getYRot(), p.getXRot(), (float) p.getX(), (float) p.getZ(), 0});
                boolean firme = Math.abs(net.minecraft.util.Mth.wrapDegrees(p.getYRot() - q[0])) < 0.6F && Math.abs(p.getXRot() - q[1]) < 0.6F
                        && (p.getX() - q[2]) * (p.getX() - q[2]) + (p.getZ() - q[3]) * (p.getZ() - q[3]) < 0.0025;
                q[0] = p.getYRot();
                q[1] = p.getXRot();
                q[2] = (float) p.getX();
                q[3] = (float) p.getZ();
                q[4] = firme ? q[4] + 1 : 0;
            }
        }
    }

    public static void limpiar() {
        APUNTANDO.clear();
        QUIETO.clear();
    }
}
