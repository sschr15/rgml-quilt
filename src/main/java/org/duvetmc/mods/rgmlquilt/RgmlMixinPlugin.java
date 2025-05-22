package org.duvetmc.mods.rgmlquilt;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.quiltmc.loader.api.QuiltLoader;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.*;

// because some RGML changes are just very big
public class RgmlMixinPlugin implements IMixinConfigPlugin {
	@Override
	public void onLoad(String mixinPackage) {

	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		if (
			mixinClassName.endsWith("BlockRendererMixin") ||
			mixinClassName.endsWith("FurnaceBlockEntityMixin")
		) {
			// Forge modifies these classes, so we can't apply our mixin to it (lest we get horrible crashes)
			return QuiltLoader.getAllMods().stream()
				.map(m -> m.metadata().id())
				.noneMatch(s -> s.startsWith("rgml_lib_minecraftforge"));
		}

		return true;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {

	}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
		String[] parts = mixinClassName.split("\\.");
		String mixinClassShortName = parts[parts.length - 1];
		if ("BlockRendererMixin".equals(mixinClassShortName)) {
            // prevent issues with cfgGrassFix being added twice (usually by Forge if done before us)
			// (it isn't a problem if Forge applies after us because its mixin plugin is special)
            targetClass.fields.removeIf(field -> "cfgGrassFix".equals(field.name));
		}
	}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
		String[] parts = mixinClassName.split("\\.");
		String mixinClassShortName = parts[parts.length - 1];
		if ("BlockRendererMixin".equals(mixinClassShortName)) {
			// Every field must become public
			for (FieldNode field : targetClass.fields) {
				field.access &= ~Opcodes.ACC_PRIVATE;
				field.access |= Opcodes.ACC_PUBLIC;

				if ("cfgGrassFix".equals(field.name)) {
					// this is a special added field but things break if mixin sees it wrong
					if (field.invisibleAnnotations == null) field.invisibleAnnotations = new ArrayList<>();
					field.invisibleAnnotations.add(new AnnotationNode("LStatic;"));
				}
			}
			// Same for methods
			for (MethodNode method : targetClass.methods) {
				method.access &= ~Opcodes.ACC_PRIVATE;
				method.access |= Opcodes.ACC_PUBLIC;
			}
		}

		if ("CraftingResultSlotMixin".equals(mixinClassShortName)) {
			// Usually this would be done with an @Inject, but it's at a really awkward-to-inject location
			MethodNode method = targetClass.methods.stream()
				.filter(m -> isOnStackRemovedByPlayer(m.name))
				.findFirst()
				.orElseThrow(RuntimeException::new);

			LabelNode newLabel = new LabelNode();

			Map<String, String> mappings = new HashMap<>();
			String[] intermediaries = {
				"C_9546418",
				"C_2454309",
				"C_9590849",
				"C_5818647",
				"C_9546418;f_6238814;Lnet/minecraft/unmapped/C_9590849;",
				"C_9546418;f_5933378;Lnet/minecraft/unmapped/C_5818647;",
			};

			for (String intermediary : intermediaries) {
				String[] partsOf = intermediary.split(";");
				String fullClass = "net.minecraft.unmapped." + partsOf[0];
				String mapped = QuiltLoader.getMappingResolver().mapClassName("intermediary", fullClass);
				mappings.put(intermediary, mapped.replace('.', '/'));

				if (partsOf.length == 3) {
					String memberName = partsOf[1];
					String desc = partsOf[2];
					String mappedMember = memberName.startsWith("f")
						? QuiltLoader.getMappingResolver().mapFieldName("intermediary", fullClass, memberName, desc)
						: QuiltLoader.getMappingResolver().mapMethodName("intermediary", fullClass, memberName, desc);

					mappings.put(memberName, mappedMember);
				}
			}

			AbstractInsnNode[] toAdd = new AbstractInsnNode[] {
				newLabel,
				new FrameNode(Opcodes.F_NEW, 2, new String[]{ mappings.get("C_9546418"), mappings.get("C_2454309") }, 0, new Object[0]),
				new VarInsnNode(Opcodes.ALOAD, 0),
				new FieldInsnNode(Opcodes.GETFIELD, mappings.get("C_9546418"), mappings.get("f_6238814"), "L" + mappings.get("C_9590849") + ";"),
				new VarInsnNode(Opcodes.ALOAD, 1),
				new VarInsnNode(Opcodes.ALOAD, 0),
				new FieldInsnNode(Opcodes.GETFIELD, mappings.get("C_9546418"), mappings.get("f_5933378"), "L" + mappings.get("C_5818647") + ";"),
				new MethodInsnNode(Opcodes.INVOKESTATIC, "org/duvetmc/rgml/ModLoader", "TakenFromCrafting", "(L" + mappings.get("C_9590849") + ";L" + mappings.get("C_2454309") + ";L" + mappings.get("C_5818647") + ";)V", false),
			};

			AbstractInsnNode nodeToInsertBefore = method.instructions.getFirst();
			while (true) {
				nodeToInsertBefore = nodeToInsertBefore.getNext();
				if (nodeToInsertBefore.getOpcode() != Opcodes.ISTORE) continue;
				if (((VarInsnNode) nodeToInsertBefore).var != 2) continue;
				break;
			}

			// Insert before the nearest label
			while (!(nodeToInsertBefore instanceof LabelNode)) {
				nodeToInsertBefore = nodeToInsertBefore.getPrevious();
			}

			LabelNode toLookFor = (LabelNode) nodeToInsertBefore;

			for (AbstractInsnNode node : toAdd) {
				method.instructions.insertBefore(toLookFor, node);
			}

			// Re-adjust references to the label prior to the insertion point
			for (AbstractInsnNode node : method.instructions.toArray()) {
				if (node instanceof JumpInsnNode) {
					JumpInsnNode jump = (JumpInsnNode) node;
					if (jump.label == toLookFor) {
						jump.label = newLabel;
					}
				}
				if (node == toLookFor) {
					break; // don't replace anything past the original label
				}
			}
		}
	}

	private boolean isOnStackRemovedByPlayer(String methodName) {
		switch (QuiltLoader.getMappingResolver().getCurrentRuntimeNamespace()) {
			case "official":
			case "clientOfficial":
				return "a".equals(methodName);
			case "intermediary":
				return "m_1468841".equals(methodName);
			case "named":
				return "onStackRemovedByPlayer".equals(methodName);
			default:
				throw new RuntimeException("Unknown runtime namespace: " + QuiltLoader.getMappingResolver().getCurrentRuntimeNamespace());
		}
	}
}
