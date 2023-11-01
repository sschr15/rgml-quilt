package org.duvetmc.mods.rgmlquilt.plugin;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.metadata.ModEnvironment;
import org.duvetmc.mods.rgmlquilt.util.*;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.Sys;
import org.quiltmc.json5.JsonWriter;
import org.quiltmc.loader.api.*;
import org.quiltmc.loader.api.gui.QuiltLoaderGui;
import org.quiltmc.loader.api.gui.QuiltLoaderIcon;
import org.quiltmc.loader.api.gui.QuiltLoaderText;
import org.quiltmc.loader.api.plugin.ModContainerExt;
import org.quiltmc.loader.api.plugin.ModMetadataExt;
import org.quiltmc.loader.api.plugin.QuiltPluginContext;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;
import org.quiltmc.loader.api.plugin.solver.QuiltFileHasher;
import org.quiltmc.loader.impl.filesystem.QuiltMemoryFileSystem;
import org.quiltmc.loader.impl.metadata.qmj.AdapterLoadableClassEntry;

import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

@SuppressWarnings("UnstableApiUsage")
public class RisugamiModLoadOption extends ModLoadOption {
	private final QuiltPluginContext context;
	private final Path path;
	private final Path root;
	private final String id;
	private final String version;

	private final Metadata metadata;

	public RisugamiModLoadOption(QuiltPluginContext context, Path path, Path root, String id, String version) {
		this.context = context;
		this.path = path;
		this.id = id;
		this.version = version;

		this.metadata = new Metadata();

		// Must remap mod to correct namespace
		Path remappedRoot;
		try {
			remappedRoot = context.manager().createMemoryFileSystem("temp" + id);

			try (FileSystem fs = context.manager().createMemoryFileSystem("temp1" + System.nanoTime()).getFileSystem()) {
				Path tempRoot = fs.getPath("/");
				Remap.remap(context, path, root, tempRoot);
				ReflectionInterceptMapper.copyWithIntercepts(tempRoot, remappedRoot);
			}

			List<String> mixins = new ArrayList<>();
			MixinMaker.modifyIfNecessary(context, remappedRoot, id, remappedRoot, mixins);

			if (!mixins.isEmpty()) {
				try (JsonWriter writer = JsonWriter.json(remappedRoot.resolve("mixins.json"))) {
					writer.beginObject();
					writer.name("required").value(true);
					writer.name("package").value(id + ".mixin");
					writer.name("compatibilityLevel").value("JAVA_8");
					writer.name("plugin").value("org.duvetmc.mods.rgmlquilt.RgmlModMixinPlugin");

					writer.name("mixins").beginArray();
					for (String mixin : mixins) {
						writer.value(mixin.replace('/', '.'));
					}
					writer.endArray();

					writer.endObject();
				}
				metadata.generatedMixinFile = "mixins.json";
			}

			Path mixinDir = remappedRoot.resolve(id).resolve("mixin");
			if (Files.exists(mixinDir)) Files.walkFileTree(mixinDir, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					// Get original class
					Path relative = mixinDir.relativize(file);
					Path target = remappedRoot.resolve(relative);

					// Delete original class (because classload shenanigans)
					Files.delete(target);

					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
					// Remove empty directories created along the way

					Path relative = mixinDir.relativize(dir);
					Path target = remappedRoot.resolve(relative);

					if (!Files.list(target).findAny().isPresent()) {
						Files.delete(target);
					}

					return FileVisitResult.CONTINUE;
				}
			});

			if (Boolean.getBoolean("rgml.debug.save_remapped_to_disk")) {
				Path saveTo = context.manager().getGameDirectory().resolve(".cache").resolve("rgml").resolve(id + ".zip");
				Utils.deleteRecursively(saveTo);
				Files.createDirectories(saveTo.getParent());

				URI uri = URI.create("jar:" + saveTo.toUri());

				Map<String, Object> env = new HashMap<>();
				env.put("create", "true");
				try (FileSystem fs = FileSystems.newFileSystem(uri, env)) {
					Files.walkFileTree(remappedRoot, new SimpleFileVisitor<Path>() {
						@Override
						public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
							Files.copy(file, fs.getPath("/" + remappedRoot.relativize(file).toString()), StandardCopyOption.REPLACE_EXISTING);
							return FileVisitResult.CONTINUE;
						}

						@Override
						public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
							Files.createDirectories(fs.getPath("/" + remappedRoot.relativize(dir).toString()));
							return FileVisitResult.CONTINUE;
						}
					});
				}
			}

			// final step is turning it read-only (plus the gc can collect the r/w copy after this)
			this.root = context.manager().copyToReadOnlyFileSystem("rgml-mod-" + id, remappedRoot);
		} catch (IOException e) {
			throw new RuntimeException(e);
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
	public Path from() {
		return path;
	}

	@Override
	public Path resourceRoot() {
		return root;
	}

	@Override
	public boolean isMandatory() {
		return false;
	}

	@Override
	public @Nullable String namespaceMappingFrom() {
		return "official";
	}

	@Override
	public boolean needsTransforming() {
		return false;
	}

	@Override
	public byte[] computeOriginHash(QuiltFileHasher hasher) throws IOException {
		return hasher.computeRecursiveHash(path);
	}

	@Override
	public QuiltLoaderIcon modFileIcon() {
		return null;
	}

	@Override
	public QuiltLoaderIcon modTypeIcon() {
		return QuiltLoaderGui.iconZipFile();
	}

	@Override
	public ModContainerExt convertToMod(Path transformedResourceRoot) {
		return new Container(transformedResourceRoot);
	}

	@Override
	public String shortString() {
		return id;
	}

	@Override
	public String getSpecificInfo() {
		return version + " (RGML)";
	}

	@Override
	public QuiltLoaderText describe() {
		return QuiltLoaderText.of("RGML mod: " + id);
	}

	private class Metadata implements ModMetadataExt {
		String generatedMixinFile = null;

		@Override
		public @Nullable ModPlugin plugin() {
			return null;
		}

		@Override
		public Map<String, Collection<AdapterLoadableClassEntry>> getEntrypoints() {
			return Collections.emptyMap();
		}

		@Override
		public Map<String, String> languageAdapters() {
			return Collections.emptyMap();
		}

		@Override
		public Collection<String> mixins(EnvType env) {
			return generatedMixinFile == null ? Collections.emptyList() : Collections.singletonList(generatedMixinFile);
		}

		@Override
		public Collection<String> accessWideners() {
			return Collections.emptyList();
		}

		@Override
		public ModEnvironment environment() {
			return ModEnvironment.CLIENT; // No server support for RGML
		}

		@Override
		public String id() {
			return id;
		}

		@Override
		public String group() {
			return "generated.rgmlquilt";
		}

		@Override
		public Version version() {
			return Version.of(version);
		}

		@Override
		public String name() {
			return id;
		}

		@Override
		public String description() {
			return "A Risugami's Mod Loader mod loaded by RGMLQuilt";
		}

		@Override
		public Collection<ModLicense> licenses() {
			return Collections.singletonList(ArrLicense.INSTANCE);
		}

		@Override
		public Collection<ModContributor> contributors() {
			return Collections.emptyList(); // Unknown contributors
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
	}

	private class Container implements ModContainerExt {
		private final Path path;

		private Container(Path path) {
			this.path = path;
		}

		@Override
		public ModMetadataExt metadata() {
			return metadata;
		}

		@Override
		public Path rootPath() {
			return path;
		}

		@Override
		public List<List<Path>> getSourcePaths() {
			return Collections.singletonList(Collections.singletonList(RisugamiModLoadOption.this.path));
		}

		@Override
		public BasicSourceType getSourceType() {
			return BasicSourceType.OTHER;
		}

		@Override
		public String pluginId() {
			return "rgml-quilt";
		}

		@Override
		public String modType() {
			return "Risugami's Mod Loader";
		}

		@Override
		public boolean shouldAddToQuiltClasspath() {
			return true;
		}
	}
}
