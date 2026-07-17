package com.example.mixin;

import net.minecraft.server.MinecraftServer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.example.ExampleMod;

/**
 * Server-side mixin example. Injects at the head of {@code MinecraftServer.loadLevel()}.
 *
 * <p>To VERIFY a mixin actually applied, run with {@code -Dmixin.debug.export=true} and check
 * {@code run/.mixin.out/} for the transformed class (see CLAUDE.md). Prefer MixinExtras
 * annotations ({@code @WrapOperation}, {@code @ModifyExpressionValue}) over {@code @Redirect};
 * MixinExtras 0.5.4 is bundled in the loader.
 */
@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {
	@Inject(at = @At("HEAD"), method = "loadLevel")
	private void example$onLoadLevel(CallbackInfo ci) {
		ExampleMod.LOGGER.info("[{}] MinecraftServer.loadLevel() reached — mixin applied", ExampleMod.MOD_ID);
	}
}
