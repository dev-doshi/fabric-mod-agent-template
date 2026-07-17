package com.example.entity;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import com.example.ExampleMod;

/**
 * Entity type registration. Pattern verified against fabric-docs reference mod (ModEntityTypes).
 * The client renderer is registered separately in {@code ExampleModClient} (client source set).
 */
public final class ModEntities {
	public static final EntityType<ExampleEntity> EXAMPLE_ENTITY = register(
			"example_entity",
			EntityType.Builder.<ExampleEntity>of(ExampleEntity::new, MobCategory.MISC)
					.sized(0.5f, 0.5f)
	);

	private static <T extends net.minecraft.world.entity.Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, ExampleMod.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	public static void initialize() {
		ExampleMod.LOGGER.info("[{}] Registered entity types", ExampleMod.MOD_ID);
	}

	private ModEntities() {
	}
}
