package org.duvetmc.rgml.mixin;

import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkSource;
import net.minecraft.world.chunk.ServerChunkCache;
import org.duvetmc.rgml.ModLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerChunkCache.class)
public class ServerChunkCacheMixin {
	@Shadow
	private ChunkSource generator;

	@Shadow
	private World world;

	@Inject(method = "populateChunk", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/chunk/WorldChunk;markDirty()V"))
	private void rgml$onChunkPopulate(ChunkSource src, int x, int z, CallbackInfo ci) {
		ModLoader.PopulateChunk(generator, x, z, world);
	}
}
