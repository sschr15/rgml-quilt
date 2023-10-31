package org.duvetmc.mods.rgmlquilt.util;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.quiltmc.loader.api.plugin.QuiltPluginContext;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class MixinMaker {
	public static void modifyIfNecessary(QuiltPluginContext ctx, Path path, String mod, Path root, List<String> mixins) throws IOException {
		if (Files.isDirectory(path)) {
			Files.walk(path).collect(Collectors.toList()).stream() // Create snapshot
				.filter(p -> p.toString().endsWith(".class"))
				.forEach(p -> {
					try {
						modifyIfNecessary(ctx, p, mod, root, mixins);
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				});
			return;
		}

		ClassNode node = new ClassNode();
		try (InputStream is = Files.newInputStream(path)) {
			ClassReader reader = new ClassReader(is);
			reader.accept(node, 0);
		}

		if (!shouldMixin(node.name)) return;

		Optional<Path> optionalPath = ctx.manager().getModIds().stream()
			.flatMap(id -> ctx.manager().getAllMods(id).stream())
			.map(ModLoadOption::resourceRoot)
			.map(p -> p.resolve(node.name + ".class"))
			.filter(Files::exists)
			.findFirst();

		if (!optionalPath.isPresent()) return;

		Path targetPath = optionalPath.get();
		ClassNode target = new ClassNode();
		try (InputStream is = Files.newInputStream(targetPath)) {
			ClassReader reader = new ClassReader(is);
			reader.accept(target, 0);
		}

		convertToMixin(node, target, mod);
		mixins.add(target.name);

		Path newLocation = root.resolve(node.name + ".class");
		Files.createDirectories(newLocation.getParent());

		ClassWriter writer = new ClassWriter(0);
		node.accept(writer);
		Files.write(newLocation, writer.toByteArray());
	}

	public static boolean shouldMixin(String className) {
		className = className.replace('/', '.');
		return className.startsWith("net.minecraft.")
			|| className.startsWith("com.mojang.blaze3d.")
			|| className.startsWith("com.jcraft.")
			|| className.startsWith("paulscode.sound.")
			|| className.startsWith("argo.")
			;
	}

	// complaint about asm:
	// why is almost everything nullable
	// why can't all the fancy lists and things have default empty lists
	// that would make everything so much easier

	/**
	 * Convert a (traditionally completely overriding) class into a mixin.
	 * @param node The class to convert.
	 * @param target The original class (for comparing methods and fields).
	 */
	public static void convertToMixin(ClassNode node, ClassNode target, String mod) {
		node.access &= ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED);
		node.access |= Opcodes.ACC_PUBLIC;

		String originalName = node.name;
		node.name = mod + "/mixin/" + node.name;

		AnnotationNode mixin = new AnnotationNode("Lorg/spongepowered/asm/mixin/Mixin;");
		mixin.values = Arrays.asList(
			"remap", false,
			"priority", 1000,
			"value", Collections.singletonList(Type.getObjectType(originalName)),
			"targets", Collections.emptyList()
		);

		if (node.invisibleAnnotations == null) {
			node.invisibleAnnotations = new ArrayList<>();
		}

		node.invisibleAnnotations.add(mixin);

		AnnotationNode shadow = new AnnotationNode("Lorg/spongepowered/asm/mixin/Shadow;");
		shadow.values = Arrays.asList(
			"remap", false
		);
		AnnotationNode mutable = new AnnotationNode("Lorg/spongepowered/asm/mixin/Mutable;");
		AnnotationNode _final = new AnnotationNode("Lorg/spongepowered/asm/mixin/Final;");
		AnnotationNode pretendStatic = new AnnotationNode("LStatic;");

		List<FieldNode> targetFields = target.fields == null ? Collections.emptyList() : target.fields;
		Map<String, FieldNode> targetFieldMap = new HashMap<>();
		for (FieldNode field : targetFields) {
			String name = field.name + ':' + field.desc;
			targetFieldMap.put(name, field);
		}

		if (node.fields != null) for (FieldNode field : node.fields) {
			String name = field.name + ':' + field.desc;
			if (targetFieldMap.containsKey(name)) {
				FieldNode targetField = targetFieldMap.get(name);
				if (field.visibleAnnotations == null) {
					field.visibleAnnotations = new ArrayList<>();
				}

				field.visibleAnnotations.add(shadow);

				if ((targetField.access & Opcodes.ACC_FINAL) != 0) {
					field.access &= ~Opcodes.ACC_FINAL;
					field.visibleAnnotations.add(mutable);
					field.visibleAnnotations.add(_final); // Mixin dictates that this is required
				}

				if (field.invisibleAnnotations == null) {
					field.invisibleAnnotations = new ArrayList<>();
				}

				if ((targetField.access & Opcodes.ACC_STATIC) == 0 && (field.access & Opcodes.ACC_STATIC) != 0) {
					// forge still hates me, it made a non-static field static for some reason
					field.access &= ~Opcodes.ACC_STATIC;
					field.invisibleAnnotations.add(pretendStatic);
				}

//				int accessMask = Opcodes.ACC_PUBLIC | Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED;
//				if ((targetField.access & accessMask) != (field.access & accessMask)) {
//					AnnotationNode access = new AnnotationNode("LAccess;");
//					access.values = Arrays.asList("value", field.access & accessMask);
//					field.invisibleAnnotations.add(access);
//				}
			} else {
//				// Custom fields must be very hacky
				if ((field.access & Opcodes.ACC_STATIC) != 0 && (field.access & Opcodes.ACC_PRIVATE) == 0) {
					// Mixin declares that static fields must be private (but we know better)
					field.access &= ~Opcodes.ACC_STATIC;

					if (field.invisibleAnnotations == null) {
						field.invisibleAnnotations = new ArrayList<>();
					}

					field.invisibleAnnotations.add(pretendStatic);
				}
			}
		}

		AnnotationNode overwrite = new AnnotationNode("Lorg/spongepowered/asm/mixin/Overwrite;");
		overwrite.values = Arrays.asList(
			"remap", false
		);

		List<MethodNode> targetMethods = target.methods == null ? Collections.emptyList() : target.methods;
		Map<String, MethodNode> targetMethodMap = new HashMap<>();
		for (MethodNode method : targetMethods) {
			String name = method.name + method.desc;
			targetMethodMap.put(name, method);
		}

		if (node.methods != null) {
			List<MethodNode> toRemove = new ArrayList<>();
			for (MethodNode method : node.methods) {
				String name = method.name + method.desc;

				if (targetMethodMap.containsKey(name)) {
					MethodNode targetMethod = targetMethodMap.get(name);

					byte[] thisHash = AsmUtil.hashMethod(method);
					byte[] targetHash = AsmUtil.hashMethod(targetMethod);
					if (Arrays.equals(thisHash, targetHash) && method.access == targetMethod.access) {
						// No change was made, delete the method from the mixin
						toRemove.add(method);
						continue;
					}

					// If the method is a constructor, funky stuff must happen
					if (method.name.equals("<init>")) {
						method.name = "$rgml-quilt-overwrite-init$";
						continue; // work done in mixin plugin
					}

					// or class initializer
					if (method.name.equals("<clinit>")) {
						method.access &= ~Opcodes.ACC_STATIC; // static init regains its staticness later
						method.name = "$rgml-quilt-overwrite-clinit$";

						continue;
					}

					// Otherwise, overwrite time
					if (method.visibleAnnotations == null) {
						method.visibleAnnotations = new ArrayList<>();
					}

					method.visibleAnnotations.add(overwrite);

					// bugs with visibility as well
					method.access &= ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED);
					method.access |= Opcodes.ACC_PUBLIC;

					// and before you thought it was over, forge ruins me by making a non-static method static
					if ((targetMethod.access & Opcodes.ACC_STATIC) == 0 && (method.access & Opcodes.ACC_STATIC) != 0) {
						method.access &= ~Opcodes.ACC_STATIC;

						if (method.invisibleAnnotations == null) {
							method.invisibleAnnotations = new ArrayList<>();
						}

						method.invisibleAnnotations.add(pretendStatic);
					}
				} else {
					// If the method is a constructor, funky stuff must happen
					if (method.name.equals("<init>")) {
						method.name = "$rgml-quilt-overwrite-init$";
						continue; // work done in mixin plugin
					}

					// or class initializer
					if (method.name.equals("<clinit>")) {
						method.access &= ~Opcodes.ACC_STATIC; // static init regains its staticness later
						method.name = "$rgml-quilt-overwrite-clinit$";

						continue;
					}

					// endless pain
					if ((method.access & Opcodes.ACC_STATIC) != 0) {
						method.access &= ~Opcodes.ACC_STATIC;

						if (method.invisibleAnnotations == null) {
							method.invisibleAnnotations = new ArrayList<>();
						}

						method.invisibleAnnotations.add(pretendStatic);
					}
				}
			}

			node.methods.removeAll(toRemove);
		}
	}
}
