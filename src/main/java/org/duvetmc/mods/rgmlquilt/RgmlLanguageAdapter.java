package org.duvetmc.mods.rgmlquilt;

import org.duvetmc.rgml.BaseMod;
import org.duvetmc.rgml.ModLoader;
import org.quiltmc.loader.api.LanguageAdapter;
import org.quiltmc.loader.api.LanguageAdapterException;
import org.quiltmc.loader.api.ModContainer;
import org.quiltmc.loader.impl.launch.common.QuiltLauncherBase;

import java.io.IOException;

public class RgmlLanguageAdapter implements LanguageAdapter {
	@Override
	public <T> T create(ModContainer mod, String value, Class<T> type) throws LanguageAdapterException {
		Class<?> instType;
		try {
			instType = Class.forName(value, true, QuiltLauncherBase.getLauncher().getTargetClassLoader());
		} catch (ClassNotFoundException e) {
			throw new LanguageAdapterException(e);
		}

		if (!type.isAssignableFrom(instType)) {
			throw new LanguageAdapterException("Cannot convert " + instType.getName() + " to " + type.getName());
		}

		try {
			ModLoader.setupProperties((Class<? extends BaseMod>) instType);
		} catch (IllegalAccessException | IOException | NoSuchFieldException e) {
			throw new LanguageAdapterException("There was an error while loading RGML mod properties", e);
		}

		try {
			return type.cast(instType.newInstance());
		} catch (InstantiationException | IllegalAccessException e) {
			throw new LanguageAdapterException("Could not instantiate mod " + instType.getName(), e);
		} catch (Throwable t) {
			throw new LanguageAdapterException("Mod " + instType.getName() + " threw an exception during initialization", t);
		}
	}
}
