package org.duvetmc.mods.rgmlquilt.util;

import org.quiltmc.loader.api.ModLicense;

@SuppressWarnings("NonExtendableApiUsage") // :yeef:
public class ArrLicense implements ModLicense {
	public static final ArrLicense INSTANCE = new ArrLicense();

	private ArrLicense() {
	}

	@Override
	public String name() {
		return "All Rights Reserved";
	}

	@Override
	public String id() {
		return "arr";
	}

	@Override
	public String url() {
		return "";
	}

	@Override
	public String description() {
		return "All rights are reserved to the original author of this mod.";
	}
}
