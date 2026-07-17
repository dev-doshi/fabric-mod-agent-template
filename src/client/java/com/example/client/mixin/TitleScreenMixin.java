package com.example.client.mixin;

import net.minecraft.client.gui.screens.TitleScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.example.ExampleMod;

/**
 * Client-side mixin example. Logs once when the title screen finishes initializing.
 * Package must match the "client" mixin config (example.client.mixins.json).
 */
@Mixin(TitleScreen.class)
public class TitleScreenMixin {
	@Inject(at = @At("TAIL"), method = "init")
	private void example$onInit(CallbackInfo ci) {
		ExampleMod.LOGGER.info("[{}] TitleScreen initialized — client mixin applied", ExampleMod.MOD_ID);
	}
}
