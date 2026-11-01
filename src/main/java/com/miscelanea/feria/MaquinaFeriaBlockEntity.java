package com.miscelanea.feria;

import com.miscelanea.registry.ModItems;
import com.miscelanea.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Estado de una máquina de feria. Algodón: azúcar → 4 s girando → algodón del color elegido con un tinte.
 * Palomitas: trigo → 5 s explotando granos → cucurucho. Fuegos: clic o redstone → espectáculo (castillo de cohetes
 * de 30 s o mascletà de 20 s); agachado + clic cambia de modo.
 */
public class MaquinaFeriaBlockEntity extends BlockEntity {
    public static final int DURA_ALGODON = 80, DURA_PALOMITAS = 100, DURA_CASTILLO = 640, DURA_MASCLETA = 420;
    public static final int[] COLORES = {0xF7A8D0, 0x9CD4F7, 0xFFFFFF, 0xFFE58A, 0xB8F0A0};
    public static final String[] NOMBRES_COLOR = {"rosa", "azul", "blanco", "amarillo", "verde"};
    public boolean activo;
    public long inicio;
    public int color, modo;
    private boolean senal;
    /** Mascletà en marcha que ve este cliente (para el temblor de cámara). */
    public static BlockPos mascletaPos = BlockPos.ZERO;
    public static long mascletaIni = -100000;

    public MaquinaFeriaBlockEntity(BlockPos p, BlockState s) {
        super(com.miscelanea.registry.ModBlocks.MAQUINA_FERIA_BE.get(), p, s);
    }

    public int tipo() {
        return getBlockState().getBlock() instanceof MaquinaFeriaBlock b ? b.tipo : 0;
    }

    public Direction facing() {
        return getBlockState().getValue(MaquinaFeriaBlock.FACING);
    }

    public int dura() {
        return switch (tipo()) {
            case MaquinaFeriaBlock.ALGODON -> DURA_ALGODON;
            case MaquinaFeriaBlock.PALOMITAS -> DURA_PALOMITAS;
            default -> modo == 0 ? DURA_CASTILLO : DURA_MASCLETA;
        };
    }

    /** Progreso 0..1 de la tanda en marcha (0 si está parada). */
    public float progreso(float tiempo) {
        return activo ? Math.min(1, (tiempo - inicio) / dura()) : 0;
    }

    void usar(Player p, ItemStack st) {
        long ahora = level.getGameTime();
        switch (tipo()) {
            case MaquinaFeriaBlock.ALGODON -> {
                int c = st.is(Items.PINK_DYE) ? 0 : st.is(Items.LIGHT_BLUE_DYE) ? 1 : st.is(Items.WHITE_DYE) ? 2 : st.is(Items.YELLOW_DYE) ? 3 : st.is(Items.LIME_DYE) ? 4 : -1;
                if (c >= 0) {
                    color = c;
                    p.displayClientMessage(Component.translatable("miscelanea.algodon.color", Component.translatable("miscelanea.color." + NOMBRES_COLOR[c])), true);
                    cambio();
                    return;
                }
                if (activo) return;
                if (!st.is(Items.SUGAR)) {
                    p.displayClientMessage(Component.translatable("miscelanea.algodon.ayuda"), true);
                    return;
                }
                if (!p.getAbilities().instabuild) st.shrink(1);
                empezar(ahora);
                level.playSound(null, worldPosition, ModSounds.FE_ALGODON.get(), SoundSource.BLOCKS, 0.8F, 1F);
            }
            case MaquinaFeriaBlock.PALOMITAS -> {
                if (activo) return;
                if (!st.is(Items.WHEAT)) {
                    p.displayClientMessage(Component.translatable("miscelanea.palomitas.ayuda"), true);
                    return;
                }
                if (!p.getAbilities().instabuild) st.shrink(1);
                empezar(ahora);
            }
            default -> {
                if (p.isSecondaryUseActive()) {
                    if (activo) return;
                    modo = 1 - modo;
                    p.displayClientMessage(Component.translatable(modo == 0 ? "miscelanea.fuegos.castillo" : "miscelanea.fuegos.mascleta"), true);
                    cambio();
                    return;
                }
                if (!activo) empezar(ahora);
            }
        }
    }

    void redstone(boolean s) {
        if (s && !senal && !activo) empezar(level.getGameTime());
        senal = s;
    }

    private void empezar(long ahora) {
        activo = true;
        inicio = ahora;
        cambio();
    }

    public static void tick(Level l, BlockPos pos, BlockState s, MaquinaFeriaBlockEntity be) {
        if (!be.activo) return;
        long t = l.getGameTime() - be.inicio;
        ServerLevel sl = (ServerLevel) l;
        RandomSource r = l.random;
        double cx = pos.getX() + 0.5, cy = pos.getY(), cz = pos.getZ() + 0.5;
        switch (be.tipo()) {
            case MaquinaFeriaBlock.ALGODON -> {
                if (t % 4 == 0) sl.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.SUGAR)), cx, cy + 0.85, cz, 3, 0.25, 0.05, 0.25, 0.08);
                if (t % 40 == 0 && t > 0) l.playSound(null, pos, ModSounds.FE_ALGODON.get(), SoundSource.BLOCKS, 0.8F, 1.05F);
                if (t >= DURA_ALGODON) {
                    ItemStack st = new ItemStack(ModItems.ALGODON.get());
                    st.getOrCreateTag().putInt("color", be.color);
                    be.soltar(st);
                    l.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.6F, 1.4F);
                }
            }
            case MaquinaFeriaBlock.PALOMITAS -> {
                if (t > 15 && t < DURA_PALOMITAS - 5 && r.nextInt(t > 40 ? 2 : 5) == 0)
                    l.playSound(null, pos, ModSounds.FE_POP.get(), SoundSource.BLOCKS, 0.5F, 0.8F + r.nextFloat() * 0.6F);
                if (t % 10 == 0) sl.sendParticles(ParticleTypes.SMOKE, cx, cy + 1.1, cz, 1, 0.1, 0.02, 0.1, 0.01);
                if (t >= DURA_PALOMITAS) {
                    be.soltar(new ItemStack(ModItems.PALOMITAS.get()));
                    l.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.6F, 1.2F);
                }
            }
            default -> {
                if (be.modo == 0) be.castillo(sl, t, r);
                else be.mascleta(sl, t, r);
                if (t >= be.dura()) {
                    be.activo = false;
                    be.cambio();
                }
            }
        }
    }

    private void soltar(ItemStack st) {
        Direction f = facing();
        ItemEntity it = new ItemEntity(level, worldPosition.getX() + 0.5 + f.getStepX() * 0.7, worldPosition.getY() + 0.9, worldPosition.getZ() + 0.5 + f.getStepZ() * 0.7, st);
        it.setDeltaMovement(f.getStepX() * 0.12, 0.2, f.getStepZ() * 0.12);
        level.addFreshEntity(it);
        activo = false;
        cambio();
    }

    // ------------------------------------------------------------------ castillo de fuegos
    static final int[][] PALETAS = {{0xFF3030, 0xFFD040}, {0x40A0FF, 0xFFFFFF}, {0x60FF60, 0xFFFF80}, {0xFF60E0, 0x8040FF}, {0xFFA020, 0xFF3030},
            {0xFFFFFF, 0xC0E0FF}, {0xFFD040, 0xFFFFFF}};

    private void castillo(ServerLevel l, long t, RandomSource r) {
        int cada, cuantos = 1;
        if (t < 200) cada = 20;
        else if (t < 400) {
            cada = 12;
            cuantos = 2;
        } else if (t < 560) {
            cada = 7;
            cuantos = 2;
        } else if (t < 620) {
            cada = 2;
            cuantos = 2;
        } else return;
        if (t % cada != 0) return;
        for (int i = 0; i < cuantos; i++) cohete(l, r, t >= 560 ? 3 : t >= 400 ? 2 + r.nextInt(2) : 1 + r.nextInt(2), t >= 560);
    }

    private void cohete(ServerLevel l, RandomSource r, int vuelo, boolean final_) {
        int[] pal = PALETAS[r.nextInt(PALETAS.length)];
        CompoundTag ex = new CompoundTag();
        int forma = final_ ? (r.nextBoolean() ? 1 : 4) : new int[]{0, 1, 2, 4, 0, 1}[r.nextInt(6)];
        ex.putByte("Type", (byte) forma);
        ex.putIntArray("Colors", new int[]{pal[0], pal[1]});
        ex.putIntArray("FadeColors", new int[]{pal[1]});
        ex.putBoolean("Trail", r.nextInt(3) == 0 || final_);
        ex.putBoolean("Flicker", r.nextInt(3) == 0);
        ListTag lista = new ListTag();
        lista.add(ex);
        CompoundTag fw = new CompoundTag();
        fw.putByte("Flight", (byte) vuelo);
        fw.put("Explosions", lista);
        ItemStack st = new ItemStack(Items.FIREWORK_ROCKET);
        st.getOrCreateTag().put("Fireworks", fw);
        double x = worldPosition.getX() + 0.5 + (r.nextDouble() - 0.5) * 1.2, z = worldPosition.getZ() + 0.5 + (r.nextDouble() - 0.5) * 1.2;
        FireworkRocketEntity c = new FireworkRocketEntity(l, x, worldPosition.getY() + 0.9, z, st);
        c.setDeltaMovement(r.triangle(0, 0.08), 0.05, r.triangle(0, 0.08));
        l.addFreshEntity(c);
    }

    // ------------------------------------------------------------------ mascletà
    private void mascleta(ServerLevel l, long t, RandomSource r) {
        int cada;
        if (t < 260) cada = Math.max(2, 16 - (int) (t / 20));
        else if (t < 380) cada = 1;
        else if (t == 385 || t == 395 || t == 405) {
            traca(l, r, true);
            return;
        } else return;
        if (t % cada != 0) return;
        int n = t >= 260 ? 1 + r.nextInt(3) : 1;
        for (int i = 0; i < n; i++) traca(l, r, false);
    }

    private void traca(ServerLevel l, RandomSource r, boolean gorda) {
        Direction f = facing(), u = f.getClockWise();
        double lado = (r.nextDouble() - 0.5) * 8;
        double x = worldPosition.getX() + 0.5 + f.getStepX() * (3 + r.nextDouble() * 2) + u.getStepX() * lado;
        double z = worldPosition.getZ() + 0.5 + f.getStepZ() * (3 + r.nextDouble() * 2) + u.getStepZ() * lado;
        double y = worldPosition.getY() + 1 + r.nextDouble() * (gorda ? 3 : 1.5);
        l.playSound(null, x, y, z, gorda ? ModSounds.FE_MASCLET_FINAL.get() : ModSounds.FE_MASCLET.get(), SoundSource.BLOCKS,
                gorda ? 6F : 3F, 0.85F + r.nextFloat() * 0.3F);
        l.sendParticles(gorda ? ParticleTypes.EXPLOSION_EMITTER : ParticleTypes.EXPLOSION, x, y, z, 1, 0, 0, 0, 0);
        l.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y, z, gorda ? 12 : 3, 0.4, 0.3, 0.4, 0.02);
        l.sendParticles(ParticleTypes.FLASH, x, y, z, 1, 0, 0, 0, 0);
    }

    void cambio() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag t) {
        super.saveAdditional(t);
        t.putBoolean("act", activo);
        t.putLong("ini", inicio);
        t.putInt("color", color);
        t.putInt("modo", modo);
    }

    @Override
    public void load(CompoundTag t) {
        super.load(t);
        activo = t.getBoolean("act");
        inicio = t.getLong("ini");
        color = t.getInt("color");
        modo = t.getInt("modo");
        if (level != null && level.isClientSide && activo && modo == 1 && tipo() == MaquinaFeriaBlock.FUEGOS) {
            mascletaPos = worldPosition;
            mascletaIni = inicio;
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
