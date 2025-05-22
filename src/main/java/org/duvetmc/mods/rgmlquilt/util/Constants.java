package org.duvetmc.mods.rgmlquilt.util;

import org.quiltmc.loader.api.QuiltLoader;

public class Constants {
	public static final String MOD_ID = "rgml-quilt";
	public static final String VERSION = QuiltLoader.getModContainer(MOD_ID).get().metadata().version().toString();
}
