package org.duvetmc.mods.rgmlquilt.util;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.metadata.ModEnvironment;
import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.*;
import org.quiltmc.loader.api.plugin.ModMetadataExt;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

@SuppressWarnings("UnstableApiUsage")
public class AllOpenMetadata implements ModMetadataExt {
	@Override
	public String id() {
		return "rgml-quilt-all-open";
	}

	@Override
	public String group() {
		return "org.duvetmc.mods.rgmlquilt";
	}

	@Override
	public Version version() {
		return Version.of("1.0.0");
	}

	@Override
	public String name() {
		return "RGML-Quilt All-Open AW Generator";
	}

	@Override
	public String description() {
		return "A wrapper around an AccessWidener that makes public everything in the Minecraft client.";
	}

	@Override
	public Collection<ModLicense> licenses() {
		return Collections.emptyList();
	}

	@Override
	public Collection<ModContributor> contributors() {
		return Collections.emptyList();
	}

	@Override
	public @Nullable String getContactInfo(String key) {
		return null;
	}

	@Override
	public Map<String, String> contactInfo() {
		return Collections.emptyMap();
	}

	@Override
	public Collection<ModDependency> depends() {
		return Collections.emptyList();
	}

	@Override
	public Collection<ModDependency> breaks() {
		return Collections.emptyList();
	}

	@Override
	public @Nullable String icon(int size) {
		return null;
	}

	@Override
	public boolean containsValue(String key) {
		return false;
	}

	@Override
	public @Nullable LoaderValue value(String key) {
		return null;
	}

	@Override
	public Map<String, LoaderValue> values() {
		return Collections.emptyMap();
	}

	@Nullable
	@Override
	public ModPlugin plugin() {
		return null;
	}

	@Override
	public Map<String, Collection<ModEntrypoint>> getEntrypoints() {
		return Collections.emptyMap();
	}

	@Override
	public Map<String, String> languageAdapters() {
		return Collections.emptyMap();
	}

	@Override
	public Collection<String> mixins(EnvType env) {
		return Collections.emptyList();
	}

	@Override
	public Collection<String> accessWideners() {
		return Collections.singleton("allopen.accesswidener");
	}

	@Override
	public ModEnvironment environment() {
		return ModEnvironment.UNIVERSAL;
	}
}
