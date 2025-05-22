package org.duvetmc.rgml.mixin;

import net.minecraft.client.resource.pack.TexturePack;
import org.duvetmc.mods.rgmlquilt.util.Utils;
import org.quiltmc.loader.api.FasterFiles;
import org.quiltmc.loader.api.QuiltLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.InputStream;
import java.util.Optional;

@Mixin(TexturePack.class)
public class EmbeddedResourcesMixin {
	@Inject(method = "getResource", at = @At("HEAD"), cancellable = true)
	private void rgml$findResource(String s, CallbackInfoReturnable<InputStream> cir) {
		Optional<InputStream> stream = QuiltLoader.getAllMods().stream()
			.map(mod -> mod.getPath(s))
			.filter(FasterFiles::exists)
			.filter(FasterFiles::isRegularFile)
			.findFirst()
			.map(Utils::fileInputStream);

		stream.ifPresent(cir::setReturnValue);
	}
}
