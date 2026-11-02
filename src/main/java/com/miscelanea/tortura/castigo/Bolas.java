package com.miscelanea.tortura.castigo;

import com.miscelanea.tortura.Cautivos;
import com.miscelanea.tortura.Lazo;
import com.miscelanea.tortura.TorturaRegistro;
import com.miscelanea.tortura.qte.Qte;
import com.miscelanea.tortura.qte.QteParams;
import com.miscelanea.tortura.qte.QteTipo;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Bola de preso: estado en {@link Cautivos} ("Bola" = UUID de la entidad bola, "BolaCaptor" = quien la puso). La bola
 * es una entidad ({@link BolaPresoEntity}); si no está (desconexión, cambio de dimensión, se descargó...), se vuelve a
 * crear junto al preso. Morir la quita. Mientras la lleva: muy lento, sin correr ni saltar (más lento aún en brazos).
 */
public final class Bolas {
    private Bolas() {}

    private static final UUID LENTO = UUID.fromString("7c1d2e3f-4a5b-4c6d-8e9f-0a1b2c3d4e51");
    private static final UUID MUY_LENTO = UUID.fromString("7c1d2e3f-4a5b-4c6d-8e9f-0a1b2c3d4e52");
    /** Ticks seguidos que el preso lleva sin encontrar su bola (para volver a crearla). */
    private static final Map<UUID, Integer> SIN_BOLA = new HashMap<>();

    public static boolean tiene(Entity e) {
        return Cautivos.tieneAlgo(e) && Cautivos.leer(e).hasUUID("Bola");
    }

    @Nullable
    static UUID bolaId(Entity e) {
        if (!Cautivos.tieneAlgo(e)) return null;
        CompoundTag d = Cautivos.leer(e);
        return d.hasUUID("Bola") ? d.getUUID("Bola") : null;
    }

    static boolean esSuya(LivingEntity p, BolaPresoEntity b) {
        UUID u = bolaId(p);
        return u != null && u.equals(b.getUUID());
    }

    public static boolean esCaptor(LivingEntity v, Player p) {
        CompoundTag d = Cautivos.leer(v);
        return d.hasUUID("BolaCaptor") && d.getUUID("BolaCaptor").equals(p.getUUID());
    }

    @Nullable
    public static BolaPresoEntity bola(LivingEntity v) {
        UUID u = bolaId(v);
        if (u == null || !(v.level() instanceof ServerLevel sl)) return null;
        Entity e = sl.getEntity(u);
        return e instanceof BolaPresoEntity b && !b.isRemoved() ? b : null;
    }

    /** ¿Es un cautivo? (grilletes, lazo, cepo o jaula) */
    static boolean cautivo(LivingEntity t) {
        return Cautivos.atado(t) || Lazo.estado(t) != 0 || Escarnio.enCepo(t) || t.getVehicle() instanceof JaulaEntity;
    }

    // ------------------------------------------------------------------ poner y quitar
    public static void poner(Player captor, LivingEntity v, ItemStack st) {
        if (v == captor) return;
        if (!cautivo(v) && !captor.getAbilities().instabuild) {
            captor.displayClientMessage(Component.translatable("msg.miscelanea.bola.no_cautivo").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (captor.distanceTo(v) > 3.5) {
            captor.displayClientMessage(Component.translatable("msg.miscelanea.grilletes.lejos").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        BolaPresoEntity b = crear(v);
        CompoundTag d = Cautivos.leer(v);
        d.putUUID("Bola", b.getUUID());
        d.putUUID("BolaCaptor", captor.getUUID());
        Cautivos.escribir(v, d);
        if (!captor.getAbilities().instabuild) st.shrink(1);
        v.level().playSound(null, v.getX(), v.getY() + 0.3, v.getZ(), TorturaRegistro.BOLA_PONER.get(), SoundSource.PLAYERS, 1F, 1F);
        if (v instanceof ServerPlayer sp) sp.sendSystemMessage(Component.translatable("msg.miscelanea.bola.puesta_tu").withStyle(ChatFormatting.RED));
        captor.displayClientMessage(Component.translatable("msg.miscelanea.bola.puesta", v.getDisplayName()).withStyle(ChatFormatting.GOLD), true);
    }

    private static BolaPresoEntity crear(LivingEntity v) {
        BolaPresoEntity b = new BolaPresoEntity(v.level(), v);
        v.level().addFreshEntity(b);
        return b;
    }

    /** Quita la bola. quien: el que la quita (recupera el objeto si no está en creativo); soltar: el objeto cae al suelo. */
    public static void quitar(LivingEntity v, @Nullable Player quien, boolean soltar) {
        BolaPresoEntity b = bola(v);
        if (b != null) {
            if (soltar) b.spawnAtLocation(new ItemStack(TorturaRegistro.BOLA_PRESO.get()));
            b.discard();
        } else if (soltar) v.spawnAtLocation(new ItemStack(TorturaRegistro.BOLA_PRESO.get()));
        if (quien != null && !quien.getAbilities().instabuild) ItemHandlerHelper.giveItemToPlayer(quien, new ItemStack(TorturaRegistro.BOLA_PRESO.get()));
        limpiar(v);
        v.level().playSound(null, v.getX(), v.getY() + 0.3, v.getZ(), TorturaRegistro.GRILLETES_QUITAR.get(), SoundSource.PLAYERS, 1F, 0.8F);
        if (v instanceof ServerPlayer sp) sp.displayClientMessage(Component.translatable("msg.miscelanea.bola.quitada").withStyle(ChatFormatting.GREEN), true);
    }

    /** Borra el estado sin efectos (muerte...). */
    public static void limpiar(LivingEntity v) {
        CompoundTag d = Cautivos.leer(v);
        d.remove("Bola");
        d.remove("BolaCaptor");
        Cautivos.escribir(v, d);
        SIN_BOLA.remove(v.getUUID());
        quitarLento(v);
    }

    /** Al desconectarse o cambiar de dimensión: la bola desaparece (se vuelve a crear junto a él), el estado se queda. */
    public static void guardarBola(LivingEntity v) {
        BolaPresoEntity b = bola(v);
        if (b != null) b.discard();
    }

    // ------------------------------------------------------------------ cada tick del preso (servidor)
    public static void tick(LivingEntity v) {
        if (!(v.level() instanceof ServerLevel)) return;
        BolaPresoEntity b = bola(v);
        if (b == null) {
            int n = SIN_BOLA.merge(v.getUUID(), 1, Integer::sum);
            if (n > 20) {
                SIN_BOLA.remove(v.getUUID());
                BolaPresoEntity nb = crear(v);
                CompoundTag d = Cautivos.leer(v);
                d.putUUID("Bola", nb.getUUID());
                Cautivos.escribir(v, d);
            }
            return;
        }
        SIN_BOLA.remove(v.getUUID());
        boolean brazos = b.estado() == BolaPresoEntity.EN_BRAZOS;
        lento(v, brazos);
        if (v.isSprinting()) v.setSprinting(false);
    }

    private static void lento(LivingEntity v, boolean brazos) {
        AttributeInstance a = v.getAttribute(Attributes.MOVEMENT_SPEED);
        if (a == null) return;
        if (a.getModifier(LENTO) == null)
            a.addTransientModifier(new AttributeModifier(LENTO, "miscelanea_bola", -0.55, AttributeModifier.Operation.MULTIPLY_TOTAL));
        if (brazos && a.getModifier(MUY_LENTO) == null)
            a.addTransientModifier(new AttributeModifier(MUY_LENTO, "miscelanea_bola_brazos", -0.6, AttributeModifier.Operation.MULTIPLY_TOTAL));
        if (!brazos && a.getModifier(MUY_LENTO) != null) a.removeModifier(MUY_LENTO);
    }

    private static void quitarLento(LivingEntity v) {
        AttributeInstance a = v.getAttribute(Attributes.MOVEMENT_SPEED);
        if (a == null) return;
        if (a.getModifier(LENTO) != null) a.removeModifier(LENTO);
        if (a.getModifier(MUY_LENTO) != null) a.removeModifier(MUY_LENTO);
    }

    // ------------------------------------------------------------------ lanzar
    public static void lanzar(ServerPlayer p) {
        BolaPresoEntity b = bola(p);
        if (b == null || b.estado() != BolaPresoEntity.EN_BRAZOS || Escarnio.enCepo(p) || p.getVehicle() instanceof JaulaEntity) return;
        b.lanzar(p);
    }

    // ------------------------------------------------------------------ serrar la cadena
    /** 'quien' sierra la cadena de 'objetivo' (puede ser él mismo) con la sierra que lleva en la mano. */
    public static void serrar(ServerPlayer quien, LivingEntity objetivo, InteractionHand mano) {
        if (!tiene(objetivo)) {
            quien.displayClientMessage(Component.translatable("msg.miscelanea.sierra.nada").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (Qte.activo(quien)) return;
        if (objetivo != quien && quien.distanceTo(objetivo) > 3.5) {
            quien.displayClientMessage(Component.translatable("msg.miscelanea.grilletes.lejos").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        quien.level().playSound(null, objetivo.getX(), objetivo.getY() + 0.3, objetivo.getZ(), TorturaRegistro.SIERRA_SERRAR.get(), SoundSource.PLAYERS, 0.7F, 1F);
        Qte.iniciar(quien, QteTipo.SERRAR, QteParams.de(0.5F).titulo(Component.translatable("qte.miscelanea.titulo.serrar"))
                .ancla(objetivo.position(), 4.5), (j, r) -> {
            if (j == null) return;
            ItemStack s = j.getItemInHand(mano);
            boolean sierra = s.getItem() instanceof SierraItem;
            if (r.exito() && objetivo.isAlive() && tiene(objetivo)) {
                quitar(objetivo, null, true);
                j.level().playSound(null, objetivo.getX(), objetivo.getY() + 0.3, objetivo.getZ(), TorturaRegistro.CADENA_ROTA.get(), SoundSource.PLAYERS, 1F, 1.1F);
                j.displayClientMessage(Component.translatable("msg.miscelanea.sierra.cortada").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), true);
                if (sierra && !j.getAbilities().instabuild) s.hurtAndBreak(2, j, x -> x.broadcastBreakEvent(mano));
            } else if (r == com.miscelanea.tortura.qte.QteResultado.FALLO) {
                j.displayClientMessage(Component.translatable("msg.miscelanea.sierra.mellada").withStyle(ChatFormatting.YELLOW), true);
                if (sierra && !j.getAbilities().instabuild) s.hurtAndBreak(1, j, x -> x.broadcastBreakEvent(mano));
            }
        });
    }

    // ------------------------------------------------------------------ clic derecho a un preso (servidor)
    /** Devuelve true si la bola se encarga del clic. */
    public static boolean interactuar(Player p, LivingEntity t, ItemStack st, InteractionHand mano) {
        if (mano != InteractionHand.MAIN_HAND) return false;
        boolean la = tiene(t);
        if (st.getItem() instanceof BolaPresoItem) {
            if (!la) poner(p, t, st);
            else if (esCaptor(t, p) || p.getAbilities().instabuild) quitar(t, p, false);
            else p.displayClientMessage(Component.translatable("msg.miscelanea.bola.ya").withStyle(ChatFormatting.GRAY), true);
            return true;
        }
        if (!la) return false;
        if (st.getItem() instanceof LlaveCarceleroItem) {
            quitar(t, p, false);
            return true;
        }
        if (st.getItem() instanceof SierraItem) {
            if (p instanceof ServerPlayer sp) serrar(sp, t, mano);
            return true;
        }
        if (st.isEmpty() && p.isShiftKeyDown() && (esCaptor(t, p) || p.getAbilities().instabuild)) {
            quitar(t, p, false);
            return true;
        }
        return false;
    }

    /** Lo mismo en el cliente (sin estado: solo por el objeto en la mano), para cancelar igual el clic. */
    public static boolean interactuaria(ItemStack st) {
        return st.getItem() instanceof BolaPresoItem || st.getItem() instanceof SierraItem;
    }

    public static void limpiarMemoria() {
        SIN_BOLA.clear();
    }
}
