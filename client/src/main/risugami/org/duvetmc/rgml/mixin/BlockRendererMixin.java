package org.duvetmc.rgml.mixin;

import net.minecraft.block.Block;
import net.minecraft.client.render.BlockRenderer;
import net.minecraft.world.WorldView;
import org.duvetmc.rgml.ModLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockRenderer.class)
public class BlockRendererMixin {
	@Unique // forge likes doing funky things, hide away our copy
	private static boolean cfgGrassFix;

	@Shadow
	private WorldView world;

	@Unique
	private static final int[] rgml$blacklistedRenderTypes = {
		0, 16,
		1, 19, 13, 22, 6, 2, 10, 11, 21,
	};

	@Inject(method = "renderBlock(Lnet/minecraft/block/Block;III)Z", at = @At("HEAD"), cancellable = true)
	private void rgml$renderBlock(Block block, int x, int y, int z, CallbackInfoReturnable<Boolean> cir) {
		int renderType = block.getRenderType();
		if (renderType <= 21) return; // traditional render types
		// remaining render types are handled by ModLoader
		block.updateShape(world, x, y, z);
		boolean bl = ModLoader.RenderWorldBlock((BlockRenderer) (Object) this, world, x, y, z, block, renderType);
		cir.setReturnValue(bl);
	}

	@Redirect(method = {"renderWithMaxAmbientOcclusion", "renderWithoutAmbientOcclusion"}, at = @At(value = "FIELD", target = "Lnet/minecraft/client/render/BlockRenderer;fancyGraphics:Z"))
	private boolean rgml$swapToGrassFix() {
		return cfgGrassFix;
	}

	@Inject(method = "renderBlockItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/Block;getRenderType()I"), cancellable = true)
	private void rgml$renderBlockItem(Block block, int i, float tickDelta, CallbackInfo ci) {
		// this has to be a weird inject because otherwise there's no good injection point
		// meaning we must blacklist some render types
		int renderType = block.getRenderType();
		for (int blacklistedRenderType : rgml$blacklistedRenderTypes) {
			if (renderType == blacklistedRenderType) {
				return;
			}
		}

		ModLoader.RenderInvBlock((BlockRenderer) (Object) this, block, i, renderType);
		ci.cancel();
	}
}
