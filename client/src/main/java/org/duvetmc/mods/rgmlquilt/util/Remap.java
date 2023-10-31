package org.duvetmc.mods.rgmlquilt.util;

import net.fabricmc.mapping.tree.ClassDef;
import net.fabricmc.mapping.tree.TinyTree;
import net.fabricmc.tinyremapper.IMappingProvider;
import net.fabricmc.tinyremapper.TinyRemapper;
import org.quiltmc.loader.api.FasterFiles;
import org.quiltmc.loader.api.QuiltLoader;
import org.quiltmc.loader.api.plugin.QuiltPluginContext;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Remap {
//	private static final Logger LOGGER = LogManager.getLogger("RGML-Remap");
	private static Path unmappedTemp = null;
	private static final List<Path> classpath = new ArrayList<>();
	private static TinyTree mappings = null;
	private static String runtimeNamespace = "intermediary"; // "named" or "intermediary"

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

		String toNamespace = QuiltLoader.isDevelopmentEnvironment() ? "named" : "intermediary";
		runtimeNamespace = toNamespace;

		try {
			Class<?> QuiltLauncherBase = Class.forName("org.quiltmc.loader.impl.launch.common.QuiltLauncherBase");
			Class<?> QuiltLauncher = Class.forName("org.quiltmc.loader.impl.launch.common.QuiltLauncher");
			Class<?> MappingConfiguration = Class.forName("org.quiltmc.loader.impl.launch.common.MappingConfiguration");
			Object launcher = Utils.lookup().findStatic(QuiltLauncherBase, "getLauncher", MethodType.methodType(QuiltLauncher)).invoke();
			Object mappingConfiguration = Utils.lookup().findVirtual(QuiltLauncher, "getMappingConfiguration", MethodType.methodType(MappingConfiguration)).invoke(launcher);
			mappings = (TinyTree) Utils.lookup().findVirtual(MappingConfiguration, "getMappings", MethodType.methodType(TinyTree.class)).invoke(mappingConfiguration);
		} catch (Throwable t) {
			Utils.unsafe().throwException(t);
			throw new RuntimeException(t);
		}
	}

	public static void remap(QuiltPluginContext ctx, Path from, Path to) throws IOException {
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

//		TinyTree mappings = QuiltLauncherBase.getLauncher().getMappingConfiguration().getMappings();
		// apparently quilt is being dumb right now
		// not allowing me to access a LEGACY_EXPOSED class method (because this is a plugin)
		IMappingProvider officialToNamed;
		IMappingProvider namedToOfficial;
		try {
			MethodHandle helperCreate = Utils.lookup().findStatic(
				Class.forName("org.quiltmc.loader.impl.util.mappings.TinyRemapperMappingsHelper"),
				"create",
				MethodType.methodType(IMappingProvider.class, TinyTree.class, String.class, String.class)
			);
			officialToNamed = (IMappingProvider) helperCreate.invokeExact(mappings, "official", runtimeNamespace);
			namedToOfficial = (IMappingProvider) helperCreate.invokeExact(mappings, runtimeNamespace, "official");
        } catch (Throwable t) {
			Utils.unsafe().throwException(t);
			throw new RuntimeException(t);
		}

        TinyRemapper remapper = TinyRemapper.newRemapper()
			.withMappings(officialToNamed)
			.withMappings(out -> {
				for (String cls : RGML_CLASSES) {
					out.acceptClass(cls, "org/duvetmc/rgml/" + cls);
				}
			})
			.build();

		List<Path> mods = new ArrayList<>();
		if (unmappedTemp == null) {
			// whelp this is a hack
			unmappedTemp = ctx.manager().createMemoryFileSystem("unmapped");
			TinyRemapper backwardsRemapper = TinyRemapper.newRemapper()
				.withMappings(namedToOfficial)
				.ignoreConflicts(true)
				.build();

			backwardsRemapper.readInputs(ctx.manager().getModPaths().toArray(new Path[0]));
			backwardsRemapper.apply((name, bytes) -> {
				if (name.matches("^(?:javax?/|sun/|org/|com/(?!mojang|jcraft)|jdk/).+$")) return; // don't copy java classes that's too much
				try {
					Path path = unmappedTemp.resolve(name + ".class");
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

			// add all the mods in the mod folder to the classpath
			for (Path path : ctx.manager().getModFolders()) {
				Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
					@Override
					public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
						if (file.toString().endsWith(".zip")) {
							mods.add(file);
						}
						return FileVisitResult.CONTINUE;
					}

					@Override
					public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
						if (dir.getFileName().toString().startsWith(".")) return FileVisitResult.SKIP_SUBTREE;
						return FileVisitResult.CONTINUE;
					}
				});
			}
		}

		remapper.readClassPath(classpath.stream().filter(Objects::nonNull).toArray(Path[]::new));
		remapper.readClassPath(mods.stream().filter(p -> !p.equals(from)).toArray(Path[]::new));
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

	public static String remapClassName(String className) {
		initMappings();

		ClassDef classDef = mappings.getDefaultNamespaceClassMap().get(className);
		if (classDef == null) return null;

		return classDef.getName(runtimeNamespace);
	}

	public static String unmapClassName(String className) {
		initMappings();
		return mappings.getClasses().stream()
			.filter(c -> c.getName(runtimeNamespace).equals(className.replace('.', '/')))
			.findFirst()
			.map(c -> c.getName("official"))
			.orElse(null);
	}

	public static String remapFieldName(String className, String fieldName, String fieldDesc) {
		initMappings();

		ClassDef classDef = mappings.getDefaultNamespaceClassMap().get(className);
		if (classDef == null) return null;

		return classDef.getFields().stream()
			.filter(f -> f.getName("official").equals(fieldName) && f.getDescriptor("official").equals(fieldDesc))
			.findFirst()
			.map(f -> f.getName(runtimeNamespace))
			.orElse(null);
	}

	public static String remapFieldName(String className, String fieldName) {
		// assumes there is only one field with this name (which is enforced by the compiler but not the JVM)
		initMappings();

		ClassDef classDef = mappings.getDefaultNamespaceClassMap().get(className);
		if (classDef == null) return null;

		return classDef.getFields().stream()
			.filter(f -> f.getName("official").equals(fieldName))
			.findFirst()
			.map(f -> f.getName(runtimeNamespace))
			.orElse(null);
	}

	public static String remapMethodName(String className, String methodName, String methodDesc) {
		// assumes there's only one method with this name and given parameter list (which is enforced by the compiler but not the JVM)
		// (doesn't check return type because reflection doesn't offer that value)
		initMappings();

		ClassDef classDef = mappings.getDefaultNamespaceClassMap().get(className);
		if (classDef == null) return null;

		return classDef.getMethods().stream()
			.filter(m -> m.getName("official").equals(methodName) && m.getDescriptor("official").startsWith(methodDesc))
			.findFirst()
			.map(m -> m.getName(runtimeNamespace))
			.orElse(null);
	}
}
