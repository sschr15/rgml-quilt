package org.duvetmc.mods.rgmlquilt.plugin;

import org.duvetmc.mods.rgmlquilt.util.AllOpenModLoadOption;
import org.duvetmc.mods.rgmlquilt.util.Utils;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.QuiltLoader;
import org.quiltmc.loader.api.Version;
import org.quiltmc.loader.api.gui.QuiltDisplayedError;
import org.quiltmc.loader.api.gui.QuiltLoaderGui;
import org.quiltmc.loader.api.gui.QuiltLoaderText;
import org.quiltmc.loader.api.gui.QuiltTreeNode;
import org.quiltmc.loader.api.plugin.ModLocation;
import org.quiltmc.loader.api.plugin.QuiltLoaderPlugin;
import org.quiltmc.loader.api.plugin.QuiltPluginContext;
import org.quiltmc.loader.api.plugin.gui.PluginGuiTreeNode;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;
import org.quiltmc.loader.impl.QuiltLoaderImpl;
import org.quiltmc.loader.impl.game.GameProvider;
import org.quiltmc.loader.impl.game.minecraft.MinecraftGameProvider;
import org.quiltmc.loader.impl.launch.common.QuiltLauncher;
import org.quiltmc.loader.impl.launch.common.QuiltLauncherBase;
import org.quiltmc.loader.impl.launch.knot.Knot;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RgmlQuiltPlugin implements QuiltLoaderPlugin {
	private static final String RGML_GH_LINK = "https://github.com/sschr15/rgml-quilt";

	private QuiltPluginContext context;

	@SuppressWarnings("unchecked")
	@Override
	public void load(QuiltPluginContext context, Map<String, LoaderValue> previousData) {
		// Ensure Minecraft is running on Java 8 (later versions break RGML, earlier versions can't run Quilt)
		String version = System.getProperty("java.version");
		if (!version.startsWith("8") && !version.startsWith("1.8")) {
			QuiltDisplayedError error = context.reportError(QuiltLoaderText.of("Java " + version + " detected!"));
			error.addOpenLinkButton(QuiltLoaderText.of("RGML GitHub"), RGML_GH_LINK);
			error.setIcon(QuiltLoaderGui.iconLevelError());
			error.appendDescription(
				QuiltLoaderText.of("RGML requires Java 8 due to technical limitations in later versions of Java."),
				QuiltLoaderText.of("You are currently using Java " + version)
			);
			error.appendReportText("RGML-Quilt is incompatible with Java " + version + ". Java 8 is required.");
			context.haltLoading();
			throw new RuntimeException("RGML currently does not support reflective limitations present in Java 9+.");
		}

		// Load necessary extra data
		this.context = context;

		if (QuiltLoader.isDevelopmentEnvironment()) {
			// Way too much reflection to remove mappingio and tinyremapper from classpath :)
			try {
				MethodHandle QuiltLauncherBase_getLauncher = Utils.lookup().findStatic(QuiltLauncherBase.class, "getLauncher", MethodType.methodType(QuiltLauncher.class));
				MethodHandle Knot_classPath = Utils.lookup().findGetter(Knot.class, "classPath", List.class);
				MethodHandle QuiltLoaderImpl_INSTANCE = Utils.lookup().findStaticGetter(QuiltLoaderImpl.class, "INSTANCE", QuiltLoaderImpl.class);
				MethodHandle QuiltLoaderImpl_getGameProvider = Utils.lookup().findVirtual(QuiltLoaderImpl.class, "getGameProvider", MethodType.methodType(GameProvider.class));
				MethodHandle MinecraftGameProvider_miscGameLibraries = Utils.lookup().findGetter(MinecraftGameProvider.class, "miscGameLibraries", List.class);

				Object knot = QuiltLauncherBase_getLauncher.invoke();
				List<Path> knotClassPath = (List<Path>) Knot_classPath.invoke(knot);


				Object quiltInstance = QuiltLoaderImpl_INSTANCE.invoke();
				Object mcProvider = QuiltLoaderImpl_getGameProvider.invoke(quiltInstance);
				List<Path> miscGameLibraries = (List<Path>) MinecraftGameProvider_miscGameLibraries.invoke(mcProvider);

				knotClassPath.removeIf(p -> p.getFileName().toString().startsWith("mapping-io-") || p.getFileName().toString().startsWith("tiny-remapper-"));
				miscGameLibraries.removeIf(p -> p.getFileName().toString().startsWith("mapping-io-") || p.getFileName().toString().startsWith("tiny-remapper-"));
			} catch (Throwable t) {
				throw Utils.rethrow(t);
			}

			return;
		}

		// Add all-open fake-mod
		try {
			QuiltTreeNode self = context.manager().getTreeNode(getSelf(context));
			context.addModLoadOption(new AllOpenModLoadOption(context), self.addChild(QuiltLoaderText.of("All Open fake mod")).icon(QuiltLoaderGui.iconQuilt()));
		} catch (IOException e) {
			throw Utils.rethrow(e);
		}
	}

	private ModLoadOption getSelf(QuiltPluginContext ctx) {
		Map<String, List<ModLoadOption>> map = ctx.manager().getModLoadOptions(context.manager().getParent(context.pluginPath()));
		List<ModLoadOption> options = map.values().stream().flatMap(Collection::stream).collect(Collectors.toList());
		if (options.size() != 1) throw new IllegalStateException(options.toString());
		return options.get(0);
	}

	@Override
	public ModLoadOption[] scanZip(Path root, ModLocation location, PluginGuiTreeNode guiNode) throws IOException {
		ModLoadOption[] found = new ModLoadOption[1];

		Files.list(root)
			.filter(path -> path.getFileName().toString().startsWith("mod_"))
			.filter(path -> path.getFileName().toString().endsWith(".class"))
			.filter(path -> !path.getFileName().toString().contains("$"))
			.filter(path -> !"no".equals(System.getProperty(path.getFileName().toString().split("\\.")[0])))
			.filter(path -> !"off".equals(System.getProperty(path.getFileName().toString().split("\\.")[0])))
			.forEach(path -> {
				// RGML (or possibly Forge) mod!
				try {
					byte[] bytes = Files.readAllBytes(path);
					ClassNode node = new ClassNode();
					new ClassReader(bytes).accept(node, 0);

					if (!"BaseMod".equals(node.superName)) {
						return; // Not a mod for RGML
					}

					String id = "rgml_" + path.getFileName().toString().split("\\.")[0].substring(4);

					MethodNode versionMethod = node.methods.stream()
						.filter(method -> "Version".equals(method.name))
						.findFirst()
						.orElse(null);

					String version;
					if (versionMethod == null || versionMethod.instructions.size() == 0) {
						version = "Unknown";
					} else {
						AbstractInsnNode first = versionMethod.instructions.getFirst();
						while (first.getOpcode() == -1) {
							// Labels, line numbers, etc. can be in the way
							first = first.getNext();
						}
						if (first.getOpcode() != Opcodes.LDC) {
							version = "Unknown";
						} else {
							version = ((LdcInsnNode) first).cst.toString();
						}
					}

					found[0] = new RisugamiModLoadOption(context, root, id, version, node.name);
				} catch (Throwable t) {
					Utils.unsafe().throwException(t);
				}
			});

		if (found[0] == null) {
			// Not a mod, still must add it in case other mods depend on it
			Path zipPath = context.manager().getParent(root);

			found[0] = new RisugamiModLoadOption(context, zipPath, root, "rgml_lib_" + sanitizeModId(zipPath.getFileName().toString()), "Unknown-Lib");
		}

		return found;
	}

	private String sanitizeModId(String id) {
		return id.endsWith(".zip")
			? sanitizeModId(id.substring(0, id.length() - 4))
			: id.replaceAll("[^a-zA-Z0-9_]", "_");
	}

	@Override
	public @Nullable Boolean isHigherPriorityThan(Path path, List<ModLoadOption> thisOptions, String otherPluginId, List<ModLoadOption> otherOptions) {
		return false;
	}

	@Override
	public void unload(Map<String, LoaderValue> data) {

	}
}
