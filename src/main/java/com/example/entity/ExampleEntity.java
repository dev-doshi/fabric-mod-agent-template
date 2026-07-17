package com.example.entity;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Minimal custom entity extending {@link Entity} directly (not a Mob), so it needs no
 * attribute supplier and no model. Rendered with vanilla {@code NoopRenderer} on the client.
 * It just counts the ticks it has been alive — enough surface to assert in a gametest.
 *
 * <p>The four abstract methods below are exactly what {@code Entity} forces subclasses to
 * implement in MC 26.1 (verified in .agent-docs/mc-src: defineSynchedData, hurtServer,
 * readAdditionalSaveData, addAdditionalSaveData). Note ValueInput/ValueOutput live in
 * {@code net.minecraft.world.level.storage} — NOT net.minecraft.nbt.
 */
public class ExampleEntity extends Entity {
	private int ticksLived;

	public ExampleEntity(EntityType<? extends ExampleEntity> type, Level level) {
		super(type, level);
	}

	public int ticksLived() {
		return ticksLived;
	}

	@Override
	public void tick() {
		super.tick();
		ticksLived++;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		// No synched data for this minimal entity.
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false; // Invulnerable marker-style entity.
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}
}
