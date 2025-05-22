package org.duvetmc.mods.rgmlquilt.util;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.quiltmc.loader.api.plugin.QuiltPluginContext;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

public class MinecraftEntryViewer {
	public static void generateAccessWidener(QuiltPluginContext ctx, Path destination, String currentNamespace) throws IOException {
		ModLoadOption minecraft = ctx.manager().getAllMods("minecraft").stream().findAny().orElseThrow(RuntimeException::new);
		Path root = minecraft.resourceRoot();

		List<String> list = new ArrayList<>();
		list.add("accessWidener\tv1\t" + currentNamespace);

		Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				if (!file.toString().endsWith(".class")) return FileVisitResult.CONTINUE;

				ClassNode node = new ClassNode();
				try (InputStream is = Files.newInputStream(file)) {
					ClassReader reader = new ClassReader(is);
					reader.accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
				}

				workClass(node, list);

				return FileVisitResult.CONTINUE;
			}
		});

		list.add("");

		Path resultLocation = destination.resolve("allopen.accesswidener");
		Files.write(resultLocation, list);
	}

	private static void workClass(ClassNode node, List<String> sb) {
		if ((node.access & Opcodes.ACC_PUBLIC) == 0) {
			sb.add("accessible\tclass\t" + node.name);
		}

		if (node.innerClasses != null) for (InnerClassNode inner : node.innerClasses) {
			if ((inner.access & Opcodes.ACC_PUBLIC) == 0) {
				sb.add("accessible\tclass\t" + inner.name);
			}
		}

		String format = "accessible\t%s\t%s\t%s\t%s";
		if (node.methods != null) for (MethodNode method : node.methods) {
			if ((method.access & Opcodes.ACC_PUBLIC) == 0) {
				sb.add(String.format(format, "method", node.name, method.name, method.desc));
			}
		}

		if (node.fields != null) for (FieldNode field : node.fields) {
			if ((field.access & Opcodes.ACC_PUBLIC) == 0) {
				sb.add(String.format(format, "field", node.name, field.name, field.desc));
			}
		}
	}
}
