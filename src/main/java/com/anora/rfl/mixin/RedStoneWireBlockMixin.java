package com.anora.rfl.mixin;

import com.anora.rfl.core.block.common.LogicGateBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RedstoneSide;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fixes cosmetic "fake" redstone wire connections to RFL logic gates.
 *
 * In vanilla 1.21.x, wire connection visuals are derived from the wire state
 * returned by RedStoneWireBlock#getConnectionState(...).
 *
 * We post-process that returned state: if a neighboring block is a LogicGateBlock
 * and that side is NOT a real port (canConnectRedstone == false), we force the wire
 * connection on that side to RedstoneSide.NONE.
 *
 * This affects visuals only; your gate logic stays unchanged.
 */
@Mixin(RedStoneWireBlock.class)
public abstract class RedStoneWireBlockMixin {

    @Inject(
            method = "getConnectionState",
            at = @At("RETURN"),
            cancellable = true
    )
    private void rfl$trimFakeGateConnections(BlockGetter level, BlockState state, BlockPos pos,
                                             CallbackInfoReturnable<BlockState> cir) {

        BlockState out = cir.getReturnValue();

        out = trimSide(level, pos, out, Direction.NORTH);
        out = trimSide(level, pos, out, Direction.EAST);
        out = trimSide(level, pos, out, Direction.SOUTH);
        out = trimSide(level, pos, out, Direction.WEST);

        cir.setReturnValue(out);
    }

    private static BlockState trimSide(BlockGetter level, BlockPos wirePos, BlockState wireState, Direction dirToNeighbor) {
        BlockPos neighborPos = wirePos.relative(dirToNeighbor);
        BlockState neighborState = level.getBlockState(neighborPos);

        if (!(neighborState.getBlock() instanceof LogicGateBlock gate)) {
            return wireState;
        }

        // From the neighbor's POV, the side facing the wire is dirToNeighbor.getOpposite()
        Direction sideOnNeighbor = dirToNeighbor.getOpposite();
        boolean allowed = gate.canConnectRedstone(neighborState, level, neighborPos, sideOnNeighbor);

        if (allowed) return wireState;

        // Force the wire to NOT visually connect on this side
        return wireState.setValue(RedStoneWireBlock.PROPERTY_BY_DIRECTION.get(dirToNeighbor), RedstoneSide.NONE);
    }
}
