package com.miscelanea.rave;

import com.miscelanea.muebles.Muebles;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Panel de luces (bloque) y mando de luces (objeto): una pantalla para manejar de golpe todas las luces de la rave que haya
 * a menos de {@link #RADIO} bloques (del panel, o de ti con el mando): encender, apagar, patrón, color y disparar el confeti.
 */
public final class PanelLuces {
    private PanelLuces() {}

    public static final int RADIO = 24;
    public static final int ON = 0, OFF = 1, MODO = 2, COLOR = 3, CONFETI = 4;

    private static boolean esDeLaRave(Block b) {
        return b instanceof LuzRaveBlock || b instanceof NeonBlock || b instanceof TrussBlock || b instanceof ConfetiBlock;
    }

    /** Aplica la orden a las luces de alrededor; devuelve cuántas han cambiado. */
    public static int aplicar(Level l, BlockPos centro, int accion, int valor) {
        int n = 0;
        for (BlockPos q : BlockPos.betweenClosed(centro.offset(-RADIO, -8, -RADIO), centro.offset(RADIO, 16, RADIO))) {
            BlockState s = l.getBlockState(q);
            if (!esDeLaRave(s.getBlock())) continue;
            BlockState n2 = s;
            switch (accion) {
                case ON, OFF -> {
                    if (s.hasProperty(LucesRave.ENCENDIDA)) n2 = s.setValue(LucesRave.ENCENDIDA, accion == ON);
                }
                case MODO -> {
                    if (s.hasProperty(LucesRave.MODO)) n2 = s.setValue(LucesRave.MODO, Math.max(0, Math.min(3, valor)));
                }
                case COLOR -> {
                    if (s.hasProperty(Muebles.COLOR)) n2 = s.setValue(Muebles.COLOR, DyeColor.byId(valor));
                }
                case CONFETI -> {
                    if (s.getBlock() instanceof ConfetiBlock && s.getValue(ConfetiBlock.CARGADO)) {
                        ConfetiBlock.disparar(l, q.immutable(), s);
                        n++;
                    }
                }
                default -> {
                }
            }
            if (n2 != s) {
                l.setBlock(q, n2, 3);
                n++;
            }
        }
        return n;
    }

    /** Orden que llega de la pantalla: desde un panel (hay que estar cerca) o desde el mando (hay que llevarlo en la mano). */
    static void orden(ServerPlayer p, boolean mando, BlockPos pos, int accion, int valor) {
        BlockPos centro;
        if (mando) {
            if (!p.getMainHandItem().is(RaveRegistro.MANDO_LUCES.get()) && !p.getOffhandItem().is(RaveRegistro.MANDO_LUCES.get())) return;
            centro = p.blockPosition();
        } else {
            // primero la distancia: si no, un cliente trucado podía hacer cargar chunks lejanos con posiciones inventadas
            if (p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 100 || !p.level().getBlockState(pos).is(RaveRegistro.PANEL_LUCES.get())) return;
            centro = pos;
        }
        int n = aplicar(p.level(), centro, accion, valor);
        p.displayClientMessage(Component.translatable("rave.miscelanea.dj.luces", n), true);
    }

    public static class Bloque extends Block {
        public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING = net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;
        private static final VoxelShape FORMA = Block.box(0.6, 0, 1.4, 15.4, 16, 14.6);

        public Bloque(Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH));
        }

        @Override
        protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> b) {
            b.add(FACING);
        }

        /** El tablero queda hacia quien la pone (el técnico) y la pantalla, al fondo. */
        @Override
        public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext c) {
            return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite());
        }

        @Override
        public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
            return FORMA;
        }

        @Override
        public InteractionResult use(BlockState s, Level l, BlockPos p, Player j, InteractionHand m, BlockHitResult h) {
            if (l.isClientSide) DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.rave.PanelLucesScreen.abrir(p));
            return InteractionResult.sidedSuccess(l.isClientSide);
        }
    }

    public static class Mando extends Item {
        public Mando(Properties p) {
            super(p);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level l, Player j, InteractionHand m) {
            if (l.isClientSide) DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.rave.PanelLucesScreen.abrir(null));
            return InteractionResultHolder.sidedSuccess(j.getItemInHand(m), l.isClientSide);
        }

        @Override
        public void appendHoverText(ItemStack s, @Nullable Level l, List<Component> t, TooltipFlag f) {
            t.add(Component.translatable("item.miscelanea.mando_luces.ayuda").withStyle(ChatFormatting.GRAY));
        }
    }
}
