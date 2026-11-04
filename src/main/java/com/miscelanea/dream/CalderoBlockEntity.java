package com.miscelanea.dream;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Lo que hay dentro del caldero de mezclas: los efectos ya fusionados (sin recortar), los colores de cada poción
 * echada, cuántas pociones se han echado y cuántos frascos quedan por sacar.
 * <p>
 * Salen tantos frascos como pociones se echaron (4 pociones = 4 frascos, cada uno con todos los efectos): mezclar no
 * gasta botellas, el precio es que cada efecto dura menos ({@link Mezclas#factor}). Una vez se saca el primer frasco
 * la mezcla queda cerrada (no se pueden añadir más pociones hasta vaciarlo).
 */
public class CalderoBlockEntity extends BlockEntity {
    public final List<MobEffectInstance> efectos = new ArrayList<>();
    public final List<Integer> colores = new ArrayList<>();
    /** Pociones echadas (1-4) y frascos que quedan. */
    public int pociones, quedan;
    /** Con pólvora: los frascos salen arrojadizos. */
    public boolean arrojadizo;
    /** Se ha echado veneno de Dream (o una mezcla con él): los frascos que salgan matan al beberlos. */
    public boolean letal;

    // ---- solo cliente: para fundir el color y el nivel al cambiar
    public int colorAntes = -1, colorAhora = -1;
    public float nivelAntes, nivelAhora;
    public long cambio = -1000;

    public CalderoBlockEntity(BlockPos p, BlockState s) {
        super(DreamRegistro.CALDERO_BE.get(), p, s);
    }

    public int color() {
        return Mezclas.mediaColor(colores);
    }

    public boolean vacio() {
        return quedan <= 0;
    }

    // ------------------------------------------------------------------ uso (servidor)

    void verter(Player p, InteractionHand mano, ItemStack st) {
        List<MobEffectInstance> ef = Mezclas.efectosDe(st);
        if (ef.isEmpty()) {
            p.displayClientMessage(Component.translatable("miscelanea.caldero.sin_efecto"), true);
            return;
        }
        if (quedan > 0 && quedan < pociones) {
            p.displayClientMessage(Component.translatable("miscelanea.caldero.empezada"), true);
            return;
        }
        if (pociones >= Mezclas.MAX_POCIONES) {
            p.displayClientMessage(Component.translatable("miscelanea.caldero.lleno", Mezclas.MAX_POCIONES), true);
            return;
        }
        for (MobEffectInstance e : ef) Mezclas.fusionar(efectos, e);
        if (VenenoLetal.es(st)) letal = true;
        colores.add(Mezclas.colorDe(st));
        pociones++;
        quedan = pociones;
        if (!p.getAbilities().instabuild) p.setItemInHand(mano, ItemUtils.createFilledResult(st, p, new ItemStack(Items.GLASS_BOTTLE)));
        level.playSound(null, worldPosition, DreamRegistro.CALDERO_VERTER.get(), SoundSource.BLOCKS, 0.9F, 0.9F + level.random.nextFloat() * 0.2F);
        level.gameEvent(null, GameEvent.FLUID_PLACE, worldPosition);
        salpicar(6);
        p.displayClientMessage(Component.translatable("miscelanea.caldero.vertido", pociones, Mezclas.MAX_POCIONES, resumen()), true);
        cambio();
    }

    void llenar(Player p, InteractionHand mano, ItemStack st) {
        if (vacio()) {
            p.displayClientMessage(Component.translatable("miscelanea.caldero.vacio"), true);
            return;
        }
        ItemStack frasco = Mezclas.frasco(Mezclas.escalados(efectos), color(), arrojadizo, pociones, letal);
        p.setItemInHand(mano, ItemUtils.createFilledResult(st, p, frasco));
        level.playSound(null, worldPosition, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 0.9F);
        level.gameEvent(null, GameEvent.FLUID_PICKUP, worldPosition);
        quedan--;
        if (quedan <= 0) reiniciar();
        else p.displayClientMessage(Component.translatable("miscelanea.caldero.quedan", quedan), true);
        cambio();
    }

    void polvora(Player p, ItemStack st) {
        if (vacio()) {
            p.displayClientMessage(Component.translatable("miscelanea.caldero.vacio"), true);
            return;
        }
        if (arrojadizo) {
            p.displayClientMessage(Component.translatable("miscelanea.caldero.ya_polvora"), true);
            return;
        }
        arrojadizo = true;
        if (!p.getAbilities().instabuild) st.shrink(1);
        level.playSound(null, worldPosition, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.5F, 1.4F);
        if (level instanceof ServerLevel sl)
            sl.sendParticles(ParticleTypes.SMOKE, worldPosition.getX() + 0.5, worldPosition.getY() + 0.95, worldPosition.getZ() + 0.5, 12, 0.18, 0.05, 0.18, 0.02);
        p.displayClientMessage(Component.translatable("miscelanea.caldero.polvora"), true);
        cambio();
    }

    void vaciar(Player p) {
        if (vacio()) {
            p.displayClientMessage(Component.translatable("miscelanea.caldero.ayuda"), true);
            return;
        }
        reiniciar();
        level.playSound(null, worldPosition, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 0.8F);
        level.playSound(null, worldPosition, SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.BLOCKS, 0.4F, 1.6F);
        if (level instanceof ServerLevel sl)
            sl.sendParticles(ParticleTypes.CLOUD, worldPosition.getX() + 0.5, worldPosition.getY() + 0.9, worldPosition.getZ() + 0.5, 10, 0.2, 0.05, 0.2, 0.02);
        p.displayClientMessage(Component.translatable("miscelanea.caldero.vaciado"), true);
        cambio();
    }

    void mostrar(Player p) {
        if (vacio()) {
            p.displayClientMessage(Component.translatable("miscelanea.caldero.ayuda"), true);
            return;
        }
        p.displayClientMessage(Component.translatable(arrojadizo ? "miscelanea.caldero.contenido_arrojadizo" : "miscelanea.caldero.contenido",
                quedan, resumen()), true);
    }

    /** "Velocidad II, Fuerza, Visión nocturna" */
    private Component resumen() {
        MutableComponent c = Component.empty();
        for (int i = 0; i < efectos.size(); i++) {
            MobEffectInstance e = efectos.get(i);
            if (i > 0) c.append(", ");
            c.append(Component.translatable(e.getDescriptionId()));
            if (e.getAmplifier() > 0) c.append(" ").append(Component.translatable("potion.potency." + e.getAmplifier()));
        }
        return c;
    }

    private void reiniciar() {
        efectos.clear();
        colores.clear();
        pociones = quedan = 0;
        arrojadizo = false;
        letal = false;
    }

    private void salpicar(int n) {
        if (!(level instanceof ServerLevel sl)) return;
        int c = color();
        DustParticleOptions gota = new DustParticleOptions(new Vector3f((c >> 16 & 255) / 255F, (c >> 8 & 255) / 255F, (c & 255) / 255F), 0.8F);
        sl.sendParticles(gota, worldPosition.getX() + 0.5, worldPosition.getY() + 0.85, worldPosition.getZ() + 0.5, n, 0.15, 0.05, 0.15, 0.05);
        sl.sendParticles(ParticleTypes.SPLASH, worldPosition.getX() + 0.5, worldPosition.getY() + 0.85, worldPosition.getZ() + 0.5, n, 0.15, 0.02, 0.15, 0.1);
    }

    void soltarAlRomper() {
        // al romperlo, lo que hubiera dentro se pierde (como el agua de un caldero normal), con un buen chof
        if (!vacio() && level != null) level.playSound(null, worldPosition, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 0.8F, 1.0F);
    }

    private void cambio() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    // ------------------------------------------------------------------ cliente: burbujeo

    /** Nivel del líquido (0 = vacío, 1 = lleno con 4 frascos). */
    public float nivel() {
        return quedan / (float) Mezclas.MAX_POCIONES;
    }

    public static void tickCliente(Level l, BlockPos pos, BlockState s, CalderoBlockEntity be) {
        if (be.vacio()) return;
        RandomSource r = l.random;
        int c = be.color();
        float cr = (c >> 16 & 255) / 255F, cg = (c >> 8 & 255) / 255F, cb = (c & 255) / 255F;
        double y = pos.getY() + CalderoBlock.alturaLiquido(be.nivel()) / 16.0;
        double radio = CalderoBlock.radioLiquido(be.nivel()) / 16.0 * 0.8;
        double x = pos.getX() + 0.5 + (r.nextDouble() * 2 - 1) * radio, z = pos.getZ() + 0.5 + (r.nextDouble() * 2 - 1) * radio;
        // burbujas que revientan en la superficie
        if (r.nextInt(be.arrojadizo ? 2 : 4) == 0) l.addParticle(ParticleTypes.BUBBLE_POP, x, y + 0.02, z, 0, 0.02, 0);
        // vapor del color de la mezcla subiendo en espiral
        if (r.nextInt(5) == 0) l.addParticle(ParticleTypes.ENTITY_EFFECT, x, y + 0.05, z, cr, cg, cb);
        // vaho blanco que sale por la boca
        if (r.nextInt(14) == 0)
            l.addParticle(ParticleTypes.CLOUD, pos.getX() + 0.5 + r.nextGaussian() * 0.08, pos.getY() + 0.95, pos.getZ() + 0.5 + r.nextGaussian() * 0.08,
                    r.nextGaussian() * 0.004, 0.025 + r.nextDouble() * 0.02, r.nextGaussian() * 0.004);
        // con pólvora, alguna chispa
        if (be.arrojadizo && r.nextInt(10) == 0) l.addParticle(ParticleTypes.SMALL_FLAME, x, y + 0.03, z, 0, 0.03, 0);
        if (r.nextInt(70) == 0)
            l.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, DreamRegistro.CALDERO_BURBUJAS.get(), SoundSource.BLOCKS,
                    0.35F + r.nextFloat() * 0.2F, 0.85F + r.nextFloat() * 0.3F, false);
    }

    // ------------------------------------------------------------------ guardado y red

    @Override
    protected void saveAdditional(CompoundTag t) {
        super.saveAdditional(t);
        ListTag l = new ListTag();
        for (MobEffectInstance e : efectos) l.add(e.save(new CompoundTag()));
        t.put("efectos", l);
        t.put("colores", new IntArrayTag(colores));
        t.putInt("pociones", pociones);
        t.putInt("quedan", quedan);
        t.putBoolean("arrojadizo", arrojadizo);
        t.putBoolean("letal", letal);
    }

    @Override
    public void load(CompoundTag t) {
        super.load(t);
        efectos.clear();
        for (Tag e : t.getList("efectos", Tag.TAG_COMPOUND)) {
            MobEffectInstance m = MobEffectInstance.load((CompoundTag) e);
            if (m != null) efectos.add(m);
        }
        colores.clear();
        for (int c : t.getIntArray("colores")) colores.add(c);
        pociones = t.getInt("pociones");
        quedan = t.getInt("quedan");
        arrojadizo = t.getBoolean("arrojadizo");
        letal = t.getBoolean("letal");
        if (level != null && level.isClientSide) {
            // en el cliente: apunta el cambio para que el renderer funda color y nivel
            float n = nivel();
            // al vaciarse, el líquido baja con su color (no con el azul de "sin nada")
            int nuevo = n <= 0 && colorAhora >= 0 ? colorAhora : color();
            if (nuevo != colorAhora || n != nivelAhora) {
                colorAntes = colorAhora < 0 ? nuevo : colorAhora;
                nivelAntes = nivelAhora;
                colorAhora = nuevo;
                nivelAhora = n;
                cambio = level.getGameTime();
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
