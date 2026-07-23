package com.example.sentinel.core;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Immutable snapshot of one world interaction (block break start/finish, or a placement attempt),
 * passed to every {@link WorldCheck}.
 */
public final class BlockContext {
	public enum Kind {
		/** Client began destroying a block. */
		BREAK_START,
		/** Client claims the block is destroyed. */
		BREAK_STOP,
		/** Client used an item against a block face (placement attempt). */
		PLACE
	}

	public final Kind kind;
	public final BlockPos pos;
	/** Block state at {@code pos} at the time of the action (for break: what is being mined). */
	public final BlockState state;
	public final long tick;

	public BlockContext(Kind kind, BlockPos pos, BlockState state, long tick) {
		this.kind = kind;
		this.pos = pos;
		this.state = state;
		this.tick = tick;
	}
}
