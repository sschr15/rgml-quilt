package org.duvetmc.mods.rgmlquilt;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.duvetmc.rgml.BaseMod;
import org.quiltmc.loader.api.QuiltLoader;
import org.quiltmc.loader.api.entrypoint.EntrypointContainer;

import java.util.List;

public class RgmlLoadSystem {
	private static final Logger LOGGER = LogManager.getLogger(RgmlLoadSystem.class);

	public static void locateMods(List<? super BaseMod> result) {
		for (EntrypointContainer<BaseMod> entrypoint : QuiltLoader.getEntrypointContainers("rgml", BaseMod.class)) {
			result.add(entrypoint.getEntrypoint());
			LOGGER.trace("Loaded mod {} with RGML-Quilt", entrypoint.getProvider().metadata().id());
		}
	}
}
