package org.duvetmc.mods.rgmlquilt.util;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.QuiltLoader;
import org.quiltmc.loader.api.gui.QuiltLoaderGui;
import org.quiltmc.loader.api.gui.QuiltLoaderIcon;
import org.quiltmc.loader.api.gui.QuiltLoaderText;
import org.quiltmc.loader.api.plugin.ModContainerExt;
import org.quiltmc.loader.api.plugin.ModMetadataExt;
import org.quiltmc.loader.api.plugin.QuiltPluginContext;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;
import org.quiltmc.loader.api.plugin.solver.QuiltFileHasher;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.spi.FileSystemProvider;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class AllOpenModLoadOption extends ModLoadOption implements ModContainerExt {
	private final QuiltPluginContext context;
	private final Path path;
	private final Path root;
	private final AllOpenMetadata metadata = new AllOpenMetadata();

	public AllOpenModLoadOption(QuiltPluginContext context) throws IOException {
		this.context = context;
		this.path = Files.createTempFile("rgml-allopen", ".jar");
		Files.deleteIfExists(this.path);

		Map<String, String> env = Collections.singletonMap("create", "true");

		FileSystemProvider zipfs = null;
		for (FileSystemProvider provider : FileSystemProvider.installedProviders()) {
			if (provider.getScheme().equals("jar")) {
				zipfs = provider;
				break;
			}
		}

		if (zipfs == null) throw new RuntimeException("No zipfs provider found!");

		try (FileSystem fs = zipfs.newFileSystem(this.path, env)) {
			Path tempRoot = fs.getPath("/");
			MinecraftEntryViewer.generateAccessWidener(context, tempRoot, QuiltLoader.getMappingResolver().getCurrentRuntimeNamespace());
			this.root = context.manager().copyToReadOnlyFileSystem("rgml-allopen", tempRoot);
		}
	}

	@Override
	public QuiltPluginContext loader() {
		return context;
	}

	@Override
	public ModMetadataExt metadata() {
		return metadata;
	}

	@Override
	public Path rootPath() {
		return root;
	}

	@Override
	public List<List<Path>> getSourcePaths() {
		return Collections.emptyList();
	}

	@Override
	public BasicSourceType getSourceType() {
		return BasicSourceType.OTHER;
	}

	@Override
	public String pluginId() {
		return context.pluginId();
	}

	@Override
	public String modType() {
		return "RGML-Quilt pseudo-mod";
	}

	@Override
	public boolean shouldAddToQuiltClasspath() {
		return false;
	}

	@Override
	public Path from() {
		return path;
	}

	@Override
	public Path resourceRoot() {
		return root;
	}

	@Override
	public boolean isMandatory() {
		return true;
	}

	@Override
	public @Nullable String namespaceMappingFrom() {
		return null;
	}

	@Override
	public boolean needsTransforming() {
		return true;
	}

	@Override
	public byte[] computeOriginHash(QuiltFileHasher hasher) {
		return new byte[hasher.getHashLength()];
	}

	@Override
	public QuiltLoaderIcon modFileIcon() {
		return null;
	}

	@Override
	public QuiltLoaderIcon modTypeIcon() {
		return QuiltLoaderGui.iconTextFile();
	}

	@Override
	public ModContainerExt convertToMod(Path transformedResourceRoot) {
		return this;
	}

	@Override
	public String shortString() {
		return "AllOpen";
	}

	@Override
	public String getSpecificInfo() {
		return "";
	}

	@Override
	public QuiltLoaderText describe() {
		return QuiltLoaderText.of("RGML AllOpen");
	}
}
