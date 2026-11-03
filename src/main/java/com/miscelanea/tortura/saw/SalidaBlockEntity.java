package com.miscelanea.tortura.saw;

import com.miscelanea.tortura.LlaveTrampaItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * El reloj de la puerta de salida. Los "de dentro" son los jugadores (no espectadores) a {@link #RADIO} bloques en
 * horizontal y {@link #ALTO} en vertical de la puerta que aún no han salido.
 * <ul>
 *   <li>Al arrancar: saludo de Jigsaw a los de dentro. Cada segundo, la cuenta en la barra de acción (y en grande a los 60,
 *       30 y 10..1 s) y el tictac en la puerta.</li>
 *   <li>Salir: abrirla con clic derecho mientras cuenta (se abre {@link #ABIERTA_TICKS} ticks); quien pasa por el hueco
 *       queda salvado.</li>
 *   <li>A cero: se cierra con llave, se apaga la bombilla y a los de dentro se les apagan las luces (oscuridad y un momento
 *       de ceguera).</li>
 * </ul>
 */
public class SalidaBlockEntity extends BlockEntity {
    public static final int RADIO = 32, ALTO = 8, MIN_S = 30, MAX_S = 900, PASO_S = 30, ABIERTA_TICKS = 80, OSCURIDAD = 1200;

    private FaseSalida fase = FaseSalida.ESPERA;
    private int segundos = 120;
    private int restante;
    private int abierta;
    private final Set<UUID> salvados = new HashSet<>();

    public SalidaBlockEntity(BlockPos p, BlockState s) {
        super(SawRegistro.SALIDA_BE.get(), p, s);
    }

    public FaseSalida fase() {
        return fase;
    }

    public int segundos() {
        return segundos;
    }

    /** Ticks que quedan de la cuenta atrás. */
    public int restante() {
        return restante;
    }

    public boolean abierta() {
        return abierta > 0;
    }

    // ------------------------------------------------------------------ uso
    void usar(Player p, boolean arriba, ItemStack st) {
        if (!(level instanceof ServerLevel sl)) return;
        switch (fase) {
            case ESPERA -> {
                if (arriba) {
                    segundos = Math.max(MIN_S, Math.min(MAX_S, segundos + (p.isShiftKeyDown() ? -PASO_S : PASO_S)));
                    sonido(SawRegistro.SALIDA_TIC.get(), 0.8F, 1.3F);
                    p.displayClientMessage(Component.translatable("msg.miscelanea.saw.salida.tiempo", reloj(segundos * 20)).withStyle(ChatFormatting.GOLD), true);
                    sincronizar();
                } else arrancar(sl);
            }
            case CUENTA, ABIERTA -> {
                if (p.isCreative() && p.isShiftKeyDown()) {
                    reiniciar();
                    p.displayClientMessage(Component.translatable("msg.miscelanea.saw.salida.reiniciada").withStyle(ChatFormatting.GRAY), true);
                    return;
                }
                abrir();
                salvar(p);
            }
            case CERRADA -> {
                if (st.getItem() instanceof LlaveTrampaItem) {
                    int id = st.getTag() == null ? 0 : st.getTag().getInt("Trampa");
                    if (id == 0 || id == Pozo.LLAVE_OXIDADA) {
                        if (!p.getAbilities().instabuild) st.shrink(1);
                        abrir();
                        salvar(p);
                        return;
                    }
                }
                if (p.isCreative() && p.isShiftKeyDown()) {
                    reiniciar();
                    p.displayClientMessage(Component.translatable("msg.miscelanea.saw.salida.reiniciada").withStyle(ChatFormatting.GRAY), true);
                    return;
                }
                sonido(SawRegistro.SALIDA_CERRAR.get(), 0.5F, 1.6F);
                p.displayClientMessage(Component.translatable("msg.miscelanea.saw.salida.cerrada").withStyle(ChatFormatting.DARK_RED), true);
            }
        }
    }

    private void arrancar(ServerLevel sl) {
        fase = FaseSalida.CUENTA;
        restante = segundos * 20;
        salvados.clear();
        for (ServerPlayer j : dentro(sl)) {
            Saw.saludo(j, "salida");
            j.sendSystemMessage(Component.translatable("msg.miscelanea.saw.salida.plazo", reloj(restante)).withStyle(ChatFormatting.RED));
        }
        sonido(SawRegistro.SALIDA_TIC.get(), 1.2F, 0.8F);
        estado();
    }

    private void reiniciar() {
        fase = FaseSalida.ESPERA;
        abierta = 0;
        restante = 0;
        salvados.clear();
        estado();
    }

    private void abrir() {
        if (abierta <= 0) sonido(SawRegistro.SALIDA_ABRIR.get(), 1.2F, 1F);
        abierta = ABIERTA_TICKS;
        estado();
    }

    private void salvar(Player p) {
        if (!(p instanceof ServerPlayer sp) || !salvados.add(p.getUUID())) return;
        Saw.titulo(sp, Component.translatable("msg.miscelanea.saw.salida.fuera").withStyle(ChatFormatting.GREEN),
                Component.translatable("msg.miscelanea.saw.salida.fuera_sub").withStyle(ChatFormatting.GRAY), 40);
        sp.removeEffect(MobEffects.DARKNESS);
        sp.removeEffect(MobEffects.BLINDNESS);
        setChanged();
    }

    // ------------------------------------------------------------------ tick
    public static void tickServidor(Level l, BlockPos p, BlockState s, SalidaBlockEntity be) {
        if (l instanceof ServerLevel sl) be.tick(sl);
    }

    private void tick(ServerLevel sl) {
        if (abierta > 0) {
            // quien cruza el hueco de la puerta, sale
            AABB hueco = new AABB(worldPosition).expandTowards(0, 1, 0).inflate(0.35);
            for (ServerPlayer j : sl.getEntitiesOfClass(ServerPlayer.class, hueco, x -> !x.isSpectator())) salvar(j);
            if (--abierta <= 0) {
                sonido(SawRegistro.SALIDA_CERRAR.get(), 1F, 1F);
                estado();
            }
        }
        if (fase != FaseSalida.CUENTA && fase != FaseSalida.ABIERTA) return;
        restante--;
        if (restante % 20 == 0) {
            int seg = restante / 20;
            boolean prisa = seg <= 10;
            sonido(SawRegistro.SALIDA_TIC.get(), prisa ? 1.3F : 0.9F, prisa ? 1.25F : 1F);
            for (ServerPlayer j : dentro(sl)) {
                j.displayClientMessage(Component.translatable("msg.miscelanea.saw.salida.cuenta", reloj(restante))
                        .withStyle(prisa ? ChatFormatting.RED : ChatFormatting.GOLD), true);
                if (seg == 60 || seg == 30 || (seg <= 10 && seg > 0))
                    Saw.titulo(j, Component.literal(reloj(restante)).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
                            Component.translatable("msg.miscelanea.saw.salida.date_prisa").withStyle(ChatFormatting.GRAY), 14);
            }
            sincronizar();
        }
        if (restante <= 0) apagon(sl);
    }

    private void apagon(ServerLevel sl) {
        fase = FaseSalida.CERRADA;
        restante = 0;
        abierta = 0;
        sonido(SawRegistro.SALIDA_CERRAR.get(), 1.4F, 0.9F);
        sonido(SawRegistro.SALIDA_APAGON.get(), 2.0F, 1F);
        for (ServerPlayer j : dentro(sl)) {
            j.addEffect(new MobEffectInstance(MobEffects.DARKNESS, OSCURIDAD, 0, false, false, true));
            j.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0, false, false, false));
            Saw.titulo(j, Component.translatable("msg.miscelanea.saw.salida.fin").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
                    Component.translatable("msg.miscelanea.saw.salida.fin_sub").withStyle(ChatFormatting.GRAY), 50);
        }
        estado();
    }

    /** Los de dentro: jugadores cerca que no son espectadores ni han salido. */
    private List<ServerPlayer> dentro(ServerLevel sl) {
        AABB zona = new AABB(worldPosition).inflate(RADIO, ALTO, RADIO);
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer j : sl.getEntitiesOfClass(ServerPlayer.class, zona, x -> !x.isSpectator() && x.isAlive()))
            if (!salvados.contains(j.getUUID())) out.add(j);
        return out;
    }

    public static String reloj(int ticks) {
        int s = Math.max(0, (ticks + 19) / 20);
        return String.format("%d:%02d", s / 60, s % 60);
    }

    private void sonido(SoundEvent s, float vol, float tono) {
        if (level != null)
            level.playSound(null, worldPosition.getX() + 0.5, worldPosition.getY() + 1.6, worldPosition.getZ() + 0.5, s, SoundSource.BLOCKS, vol, tono);
    }

    /** Pone en el bloque el estado que toca (modelo y colisión) y sincroniza. */
    private void estado() {
        if (level != null && !level.isClientSide) {
            FaseSalida visible = abierta > 0 ? FaseSalida.ABIERTA : fase == FaseSalida.ABIERTA ? FaseSalida.CUENTA : fase;
            BlockState s = getBlockState();
            if (s.hasProperty(SalidaBlock.ESTADO) && s.getValue(SalidaBlock.ESTADO) != visible)
                level.setBlock(worldPosition, s.setValue(SalidaBlock.ESTADO, visible), 3);
        }
        sincronizar();
    }

    private void sincronizar() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    // ------------------------------------------------------------------ guardar
    @Override
    protected void saveAdditional(CompoundTag t) {
        super.saveAdditional(t);
        t.putInt("Fase", fase.ordinal());
        t.putInt("Segundos", segundos);
        t.putInt("Restante", restante);
        t.putInt("Abierta", abierta);
        ListTag l = new ListTag();
        for (UUID u : salvados) l.add(NbtUtils.createUUID(u));
        t.put("Salvados", l);
    }

    @Override
    public void load(CompoundTag t) {
        super.load(t);
        fase = FaseSalida.de(t.getInt("Fase"));
        segundos = t.contains("Segundos") ? t.getInt("Segundos") : 120;
        restante = t.getInt("Restante");
        abierta = t.getInt("Abierta");
        salvados.clear();
        ListTag l = t.getList("Salvados", Tag.TAG_INT_ARRAY);
        for (Tag x : l) salvados.add(NbtUtils.loadUUID(x));
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag t = new CompoundTag();
        t.putInt("Fase", fase.ordinal());
        t.putInt("Segundos", segundos);
        t.putInt("Restante", restante);
        t.putInt("Abierta", abierta);
        return t;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).expandTowards(0, 2, 0).inflate(0.5);
    }
}
