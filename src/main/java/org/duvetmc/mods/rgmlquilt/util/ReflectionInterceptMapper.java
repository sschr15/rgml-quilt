package org.duvetmc.mods.rgmlquilt.util;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.quiltmc.loader.api.FasterFiles;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

public class ReflectionInterceptMapper {
	public static void copyWithIntercepts(Path source, Path dest) throws IOException {
		Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				Path relative = source.relativize(dir);
				Path target = dest.resolve(relative.toString());

				if (FasterFiles.notExists(target)) {
					FasterFiles.createDirectories(target);
				}

				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Path relative = source.relativize(file);
				Path target = dest.resolve(relative.toString());

				if (FasterFiles.notExists(target.getParent())) {
					FasterFiles.createDirectories(target.getParent());
				}

				if (file.toString().endsWith(".class")) {
					ClassNode node = new ClassNode();
					try (InputStream is = Files.newInputStream(file)) {
						ClassReader reader = new ClassReader(is);
						reader.accept(node, 0);
					}

					if (node.methods != null) node.methods.forEach(ReflectionInterceptMapper::intercept);

					ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
					node.accept(writer);

					Files.write(target, writer.toByteArray());
				} else {
					FasterFiles.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
				}

				return FileVisitResult.CONTINUE;
			}
		});
	}

	public static void intercept(MethodNode method) {
		for (AbstractInsnNode insn : method.instructions) {
            if (!(insn instanceof MethodInsnNode)) continue;

            MethodInsnNode methodInsn = (MethodInsnNode) insn;
            if (!methodInsn.owner.equals("java/lang/Class")) continue;

			switch (methodInsn.name) {
				case "getField":
					methodInsn.name = "getRuntimeField";
					break;
				case "getDeclaredField":
					methodInsn.name = "getRuntimeDeclaredField";
					break;
				case "getMethod":
					methodInsn.name = "getRuntimeMethod";
					break;
				case "getDeclaredMethod":
					methodInsn.name = "getRuntimeDeclaredMethod";
					break;
				default:
					continue;
			}

			// Set to the interceptor class
			methodInsn.owner = "org/duvetmc/mods/rgmlquilt/util/RuntimeRemapUtil";

			// Add the class parameter (previously implicit this)
			methodInsn.desc = "(Ljava/lang/Class;" + methodInsn.desc.substring(1);

			// Change the invoke type
			methodInsn.setOpcode(Opcodes.INVOKESTATIC);
        }
	}
}
