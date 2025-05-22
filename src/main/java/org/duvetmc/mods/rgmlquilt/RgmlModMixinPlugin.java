package org.duvetmc.mods.rgmlquilt;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class RgmlModMixinPlugin implements IMixinConfigPlugin {
    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
		// Any mod-added static fields must lose their static during mixin application
		// to prevent conflicts with other mods that add the same field
		for (FieldNode field : targetClass.fields) {
			if (field.invisibleAnnotations != null) for (AnnotationNode annotation : field.invisibleAnnotations) {
				if (annotation.desc.equals("LStatic;")) {
					field.access &= ~Opcodes.ACC_STATIC;
				}
			}
		}

		// Same with methods (though I think mixin would crash and burn anyway)
		for (MethodNode method : targetClass.methods) {
			if (method.invisibleAnnotations != null) for (AnnotationNode annotation : method.invisibleAnnotations) {
				if (annotation.desc.equals("LStatic;")) {
					method.access &= ~Opcodes.ACC_STATIC;
				}
			}
		}
	}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        if (targetClass.methods == null) return;

		List<MethodNode> toRemove = new ArrayList<>();
        for (MethodNode method : targetClass.methods) {
            if (method.name.equals("$rgml-quilt-overwrite-init$")) {
                Optional<MethodNode> existingInit = targetClass.methods.stream()
                        .filter(m -> m.name.equals("<init>"))
                        .filter(m -> m.desc.equals(method.desc))
                        .findFirst();

                existingInit.ifPresent(toRemove::add);
                method.name = "<init>";
            } else if (method.name.equals("$rgml-quilt-overwrite-clinit$")) {
                Optional<MethodNode> existingInit = targetClass.methods.stream()
                        .filter(m -> m.name.equals("<clinit>"))
                        .filter(m -> m.desc.equals(method.desc)) // in theory this should always be ()V and almost always present
                        .findFirst();

                existingInit.ifPresent(toRemove::add);
                method.name = "<clinit>";
				method.access |= Opcodes.ACC_STATIC; // removed earlier because endless pain
            }

			if (method.invisibleAnnotations != null) for (AnnotationNode annotation : method.invisibleAnnotations) {
				if (annotation.desc.equals("LStatic;")) {
					method.access |= Opcodes.ACC_STATIC;
				}
			}
        }

		targetClass.methods.removeAll(toRemove);

		for (FieldNode field : targetClass.fields) {
			if (field.invisibleAnnotations != null) for (AnnotationNode annotation : field.invisibleAnnotations) {
				if (annotation.desc.equals("LStatic;")) {
					field.access |= Opcodes.ACC_STATIC;
				}

				if (annotation.desc.equals("LAccess;")) {
					field.access &= ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED | Opcodes.ACC_PUBLIC);
					field.access |= (Integer) annotation.values.get(1);
				}
			}
		}
    }
}
