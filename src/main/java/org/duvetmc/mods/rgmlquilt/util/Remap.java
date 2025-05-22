package org.duvetmc.mods.rgmlquilt.util;

import net.fabricmc.mappingio.format.tiny.Tiny1FileReader;
import net.fabricmc.mappingio.format.tiny.Tiny2FileReader;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MappingTreeView;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.fabricmc.mappingio.tree.VisitableMappingTree;
import net.fabricmc.tinyremapper.IMappingProvider;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.TinyUtils;
import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.FasterFiles;
import org.quiltmc.loader.api.QuiltLoader;
import org.quiltmc.loader.api.plugin.QuiltPluginContext;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Stream;

public class Remap {
	private static Path unmappedTemp = null;
	private static final List<Path> classpath = new ArrayList<>();
	private static final Set<Path> mods = new HashSet<>();
	private static MappingTree mappings = null;
	private static String runtimeNamespace = "intermediary"; // "named" or "intermediary"
	private static int official = -1;
	private static int runtime = -1;

	private static final String[] RGML_CLASSES = {
		"BaseMod",
		"EntityRendererProxy",
		"MLProp",
		"ModLoader",
		"ModTextureAnimation",
		"ModTextureStatic",
	};

	private static void initMappings() {
		if (mappings != null) return;

		runtimeNamespace = QuiltLoader.isDevelopmentEnvironment() ? "named" : "intermediary";

		try {
			VisitableMappingTree tree = new MemoryMappingTree(true);
			URL mappingsUrl = QuiltLoader.class.getClassLoader().getResource("mappings/mappings.tiny");
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(mappingsUrl.openStream()))) {
				reader.mark(256);
				String header = reader.readLine();
				reader.reset();

				if (header.startsWith("v1\t")) {
					Tiny1FileReader.read(reader, tree);
				} else if (header.startsWith("tiny\t2\t0")) {
					Tiny2FileReader.read(reader, tree);
				} else {
					throw new IOException("Unsupported mappings format: " + header);
				}
			}
			mappings = tree;
			official = mappings.getNamespaceId("clientOfficial");
			if (official == MappingTreeView.NULL_NAMESPACE_ID) {
				official = mappings.getNamespaceId("official");
			}

			runtime = mappings.getNamespaceId(runtimeNamespace);
		} catch (Throwable t) {
			Utils.unsafe().throwException(t);
			throw new RuntimeException(t);
		}
	}

	public static void remap(QuiltPluginContext ctx, Path modPath, Path from, Path to) throws IOException {
		Files.walkFileTree(from, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				if (file.toString().endsWith("class")) return FileVisitResult.CONTINUE;

				Path relative = from.relativize(file);
				Path target = to.resolve(relative.toString());

				if (FasterFiles.notExists(target.getParent())) {
					FasterFiles.createDirectories(target.getParent());
				}

				Files.copy(file, target);
				return FileVisitResult.CONTINUE;
			}
		});

		initMappings();

		IMappingProvider officialToRuntime = TinyUtils.createMappingProvider(mappings, "official", runtimeNamespace);
		IMappingProvider runtimeToOfficial = TinyUtils.createMappingProvider(mappings, runtimeNamespace, "official");

		if (unmappedTemp == null) {
			// whelp this is a hack
			TinyRemapper backwardsRemapper = TinyRemapper.newRemapper()
				.withMappings(runtimeToOfficial)
				.ignoreConflicts(true)
				.build();

			backwardsRemapper.readInputs(ctx.manager().getAllMods("minecraft").stream()
				.map(ModLoadOption::from)
				.distinct()
				.filter(Files::exists)
				.toArray(Path[]::new)
			);

			if (Boolean.getBoolean("rgml.debug.export")) {
				Path outputFile = ctx.manager().getGameDirectory().resolve(".rgml").resolve("unmapped-loaded.jar");
				Files.createDirectories(outputFile.getParent());
				FileSystem fs = FileSystems.newFileSystem(URI.create("jar:" + outputFile.toUri()), Collections.singletonMap("create", "true"));
				unmappedTemp = fs.getPath("/");
			} else {
				unmappedTemp = ctx.manager().createMemoryFileSystem("unmapped");
			}

			backwardsRemapper.apply((name, bytes) -> {
				if (name.matches("^(?:javax?/|sun/|org/|com/(?!mojang|jcraft)|jdk/).+$")) return; // don't copy java classes that's too much
				try {
					Path path = unmappedTemp.resolve(name + ".class");
					if (name.startsWith("net/minecraft/unmapped")) throw new IllegalStateException("Not allowed: " + name);
					if (!FasterFiles.isDirectory(path.getParent())) {
						FasterFiles.createDirectories(path.getParent());
					}
					Files.write(path, bytes);
					synchronized (classpath) {
						classpath.add(path);
					}
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			});

			ctx.manager().getModPaths().stream()
				.filter(path -> notMinecraftOrJava(ctx.manager().getModLoadOptions(path)))
				.forEach(mods::add);
		}

		TinyRemapper remapper = TinyRemapper.newRemapper()
			.withMappings(officialToRuntime)
			.withMappings(out -> {
				for (String cls : RGML_CLASSES) {
					out.acceptClass(cls, "org/duvetmc/rgml/" + cls);
				}
			})
			.threads(1) // man i hate this
			.build();

		Path[] paths = Stream.concat(
			classpath.stream().filter(Objects::nonNull),
			mods.stream().filter(p -> !p.equals(modPath))
				.map(p -> Files.isDirectory(p) ? p : Utils.loadZip(ctx, p))
				.flatMap(Utils::walk)
				.filter(p -> p.toString().endsWith(".class"))
		).toArray(Path[]::new);

		remapper.readClassPath(paths);
		remapper.readInputs(from);
		remapper.apply((name, bytes) -> {
			try {
				Path result = to.resolve(name + ".class");
				if (!Files.exists(result.getParent())) {
					try {
						Files.createDirectories(result.getParent());
					} catch (IOException e) {
						// Quilt sometimes has a bit of a breakage where it creates the folder but then
						// believes it already exists and throws an exception
						if (!Files.exists(result.getParent()))
							Files.createDirectories(result.getParent());
//							throw e;
					}
				}
				Files.write(result, bytes);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		});
	}

	private static boolean notMinecraftOrJava(@Nullable Map<String, List<ModLoadOption>> modLoadOptions) {
		return modLoadOptions.values().stream()
			.flatMap(Collection::stream)
			.map(ModLoadOption::id)
			.noneMatch(id -> "minecraft".equals(id) || "java".equals(id));
	}

	public static String remapClassName(String className) {
		initMappings();

		MappingTreeView.ClassMappingView classDef = mappings.getClass(className, official);
		if (classDef == null) return null;

		return classDef.getName(runtimeNamespace);
	}

	public static String unmapClassName(String className) {
		initMappings();
		className = className.replace('.', '/');
		MappingTree.ClassMapping mapping = mappings.getClass(className, runtime);
		if (mapping == null) return null;
		return mapping.getName(official);
	}

	public static String remapFieldName(String className, String fieldName, String fieldDesc) {
		initMappings();

		MappingTreeView.ClassMappingView classDef = mappings.getClass(className, official);
		if (classDef == null) return null;

		MappingTreeView.FieldMappingView fieldDef = classDef.getField(fieldName, fieldDesc, official);
		return fieldDef == null ? null : fieldDef.getName(runtime);
	}

	public static String remapFieldName(String className, String fieldName) {
		// assumes there is only one field with this name (which is enforced by the compiler but not the JVM)
		initMappings();

		MappingTreeView.ClassMappingView classDef = mappings.getClass(className, official);
		if (classDef == null) return null;

		return classDef.getFields().stream()
			.filter(f -> fieldName.equals(f.getName(official)))
			.findFirst()
			.map(f -> f.getName(runtimeNamespace))
			.orElse(null);
	}

	public static String remapMethodName(String className, String methodName, String methodDesc) {
		// assumes there's only one method with this name and given parameter list (which is enforced by the compiler but not the JVM)
		// (doesn't check return type because reflection doesn't offer that value)
		initMappings();

		MappingTreeView.ClassMappingView classDef = mappings.getClass(className, official);
		if (classDef == null) return null;

		MappingTreeView.MethodMappingView methodDef = classDef.getMethod(methodName, methodDesc, official);
		return methodDef == null ? null : methodDef.getName(runtimeNamespace);
	}
}
