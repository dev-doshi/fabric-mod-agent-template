package com.example.sentinel.core;

import net.minecraft.world.phys.Vec3;

/**
 * Immutable snapshot of a single movement packet, resolved against the player's prior position.
 * Built by {@link CheckManager} and passed to every movement check.
 */
public final class MoveContext {
	public final Vec3 from;
	public final Vec3 to;
	public final double dx;
	public final double dy;
	public final double dz;
	/** Horizontal distance travelled this packet (blocks). */
	public final double horizontal;
	/** onGround flag the CLIENT claimed in this packet (untrusted — checks validate it). */
	public final boolean claimedOnGround;
	/** Measured RTT in ms at packet time (for lag-compensated envelopes). */
	public final int pingMs;

	public MoveContext(Vec3 from, Vec3 to, boolean claimedOnGround, int pingMs) {
		this.from = from;
		this.to = to;
		this.dx = to.x - from.x;
		this.dy = to.y - from.y;
		this.dz = to.z - from.z;
		this.horizontal = Math.sqrt(dx * dx + dz * dz);
		this.claimedOnGround = claimedOnGround;
		this.pingMs = pingMs;
	}

	/** Ping expressed in server ticks (50ms each), for widening envelopes under lag. */
	public double pingTicks() {
		return pingMs / 50.0;
	}
}
