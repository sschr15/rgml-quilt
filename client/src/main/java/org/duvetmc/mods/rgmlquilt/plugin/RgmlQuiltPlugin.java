package org.duvetmc.mods.rgmlquilt.plugin;

import org.duvetmc.mods.rgmlquilt.util.AllOpenModLoadOption;
import org.duvetmc.mods.rgmlquilt.util.Utils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.QuiltLoader;
import org.quiltmc.loader.api.gui.QuiltLoaderGui;
import org.quiltmc.loader.api.gui.QuiltLoaderText;
import org.quiltmc.loader.api.plugin.QuiltLoaderPlugin;
import org.quiltmc.loader.api.plugin.QuiltPluginContext;
import org.quiltmc.loader.api.plugin.QuiltPluginTask;
import org.quiltmc.loader.api.plugin.gui.PluginGuiTreeNode;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class RgmlQuiltPlugin implements QuiltLoaderPlugin {
	private static MethodHandle QuiltPluginManagerImpl_loadZip0 = null;

	@Override
	public void load(QuiltPluginContext context, Map<String, LoaderValue> previousData) {
		// Prior to mod loading, make sure of some things:
		// 1. We're running on Java 8 (later versions break RGML, earlier versions can't run Quilt)
		String version = System.getProperty("java.version");
		if (!version.startsWith("8") && !version.startsWith("1.8")) {
			throw new RuntimeException("RGML currently does not support reflective limitations present in Java 9+.");
		}

		// 2. Experimental Chasm support is enabled
//		System.setProperty(SystemProperties.ENABLE_EXPERIMENTAL_CHASM, "true");

		// Now, load mods
		try {
			PluginGuiTreeNode treeNode = context.manager().getRootGuiNode()
				.addChild(QuiltLoaderText.of("Risugami's ModLoader"))
				.mainIcon(QuiltLoaderGui.iconZipFile());

			String namespace = QuiltLoader.isDevelopmentEnvironment() ? "named" : "intermediary";
			PluginGuiTreeNode namespaceNode = treeNode.addChild(QuiltLoaderText.of("All-Open"));

			context.addModLoadOption(new AllOpenModLoadOption(context, context.pluginPath(), namespace), namespaceNode);

			Path rootMods = context.manager().getGameDirectory().resolve("mods");
			if (!context.manager().getModFolders().contains(rootMods)) {
				// Older quilt versions don't include the root mods folder in the mod folders list (???)
				loadMods(context, rootMods, treeNode);
			}

			for (Path modFolder : context.manager().getModFolders()) {
				loadMods(context, modFolder, treeNode);
			}
		} catch (Throwable t) {
			Utils.unsafe().throwException(t);
		}
	}

	private void loadMods(QuiltPluginContext context, Path modFolder, PluginGuiTreeNode treeNode) throws Throwable {
		Iterable<Path> files = Files.list(modFolder)::iterator;
		for (Path file : files) {
			if (file.getFileName().toString().endsWith(".zip")) {
				//TODO: use the correct API (waiting for implementation under the hood)
//				QuiltPluginTask<Path> loadZip = context.manager().loadZip(file);

				if (QuiltPluginManagerImpl_loadZip0 == null) {
					Class<?> QuiltPluginManagerImpl = Class.forName("org.quiltmc.loader.impl.plugin.QuiltPluginManagerImpl", false, getClass().getClassLoader());
					QuiltPluginManagerImpl_loadZip0 = Utils.lookup().findVirtual(
						QuiltPluginManagerImpl,
						"loadZip0",
						MethodType.methodType(Path.class, Path.class)
					);
				}

				Path root = (Path) QuiltPluginManagerImpl_loadZip0.invoke(context.manager(), file);

				AtomicBoolean foundMod = new AtomicBoolean(false);

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

							PluginGuiTreeNode modNode = treeNode.addChild(QuiltLoaderText.of(id + " " + version))
								.mainIcon(QuiltLoaderGui.iconZipFile());

							context.addModLoadOption(new RisugamiModLoadOption(context, file, root, id, version), modNode);
							foundMod.set(true);
						} catch (Throwable t) {
							Utils.unsafe().throwException(t);
						}
					});

				if (!foundMod.get()) {
					// Not a mod, still must add it in case other mods depend on it
					if (context.manager().getModPaths().contains(file)) {
						// Already loaded by another plugin
						return;
					}
					context.addModLoadOption(new RisugamiModLoadOption(context, file, root, "rgml_lib_" + sanitizeModId(file.getFileName().toString()), "Unknown-Lib"), treeNode);
				}
			}
		}
	}

	private String sanitizeModId(String id) {
		return id.endsWith(".zip")
			? sanitizeModId(id.substring(0, id.length() - 4))
			: id.replaceAll("[^a-zA-Z0-9_]", "_");
	}

	@Override
	public void unload(Map<String, LoaderValue> data) {

	}
}
