package org.duvetmc.rgml.mixin;

import net.minecraft.client.crash.CrashPanel;
import org.duvetmc.mods.rgmlquilt.util.Constants;
import org.duvetmc.rgml.BaseMod;
import org.duvetmc.rgml.ModLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import java.util.ArrayList;
import java.util.List;

@Mixin(CrashPanel.class)
public class CrashScreenMixin {
	@ModifyConstant(method = "<init>", constant = @Constant(stringValue = "OS: "))
	private String rgml$injectRgmlMods(String os) {
		List<String> lines = new ArrayList<>();
		lines.add("ModLoader Beta 1.8.1");
		lines.add("RGML-Quilt " + Constants.VERSION);
		lines.add("Mods loaded: " + ModLoader.getLoadedMods().size());

		for (BaseMod mod : ModLoader.getLoadedMods()) {
			lines.add("   " + mod.getClass().getName() + " " + mod.Version());
		}

		lines.add(os);

		return String.join("\n", lines);
	}
}
