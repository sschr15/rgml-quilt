package org.duvetmc.mods.rgmlquilt.util;

import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AsmUtil {
	/**
	 * Generate a hash based on the method's name, descriptor, and instructions.
	 * @param node The method to hash.
	 * @return The hash as a byte array.
	 */
	public static byte[] hashMethod(MethodNode node) {
		// it doesn't have to be perfect or reasonable, just consistent
		// aka literally any implementation of this method should work (where collisions are rare)

		MessageDigest digest = getDigest();
		digest.update(node.name.getBytes());
		digest.update(node.desc.getBytes());

		// Map labels to consistent values (because label hash codes are not consistent)
		Map<LabelNode, Integer> labelMap = new HashMap<>();
		int labelIndex = 0;

		for (AbstractInsnNode insn : node.instructions) {
			if (insn instanceof LabelNode) {
				labelMap.put((LabelNode) insn, labelIndex++);
			}
		}

		for (AbstractInsnNode insn : node.instructions) {
			handleInsn(insn, digest, labelMap);
		}

		return digest.digest();
	}

	private static void handleInsn(AbstractInsnNode insn, MessageDigest digest, Map<LabelNode, Integer> labelMap) {
		digest.update(toBytes(insn.getOpcode()));
		if (insn instanceof FieldInsnNode) {
			FieldInsnNode fieldInsn = (FieldInsnNode) insn;
			digest.update(fieldInsn.owner.getBytes());
			digest.update(fieldInsn.name.getBytes());
			digest.update(fieldInsn.desc.getBytes());
		} else if (insn instanceof FrameNode) {
			FrameNode frame = (FrameNode) insn;
			digest.update(toBytes(frame.type));
			List<?> local = frame.local == null ? Collections.emptyList() : frame.local;
			for (Object o : local) {
				if (o == null) continue;
				digest.update(o.toString().getBytes());
			}
			List<?> stack = frame.stack == null ? Collections.emptyList() : frame.stack;
			for (Object o : stack) {
				if (o == null) continue;
				digest.update(o.toString().getBytes());
			}
		} else if (insn instanceof IincInsnNode) {
			IincInsnNode iinc = (IincInsnNode) insn;
			digest.update(toBytes(iinc.var));
			digest.update(toBytes(iinc.incr));
		} else if (insn instanceof IntInsnNode) {
			IntInsnNode intInsn = (IntInsnNode) insn;
			digest.update(toBytes(intInsn.operand));
		} else if (insn instanceof InvokeDynamicInsnNode) {
			InvokeDynamicInsnNode invokeDynamic = (InvokeDynamicInsnNode) insn;
			digest.update(invokeDynamic.name.getBytes());
			digest.update(invokeDynamic.desc.getBytes());
			digest.update(invokeDynamic.bsm.getOwner().getBytes());
			digest.update(invokeDynamic.bsm.getName().getBytes());
			digest.update(invokeDynamic.bsm.getDesc().getBytes());
			for (Object o : invokeDynamic.bsmArgs) {
				digest.update(o.toString().getBytes());
			}
		} else if (insn instanceof JumpInsnNode) {
			JumpInsnNode jump = (JumpInsnNode) insn;
			digest.update(toBytes(labelMap.get(jump.label)));
		} else if (insn instanceof LabelNode) {
			digest.update(toBytes(labelMap.get(insn)));
		} else if (insn instanceof LdcInsnNode) {
			Object cst = ((LdcInsnNode) insn).cst;
			if (cst instanceof String) {
				digest.update(((String) cst).getBytes());
			} else if (cst instanceof Integer) {
				digest.update(toBytes((Integer) cst));
			} else if (cst instanceof Float) {
				digest.update(toBytes(Float.floatToIntBits((Float) cst)));
			} else if (cst instanceof Long) {
				long l = (Long) cst;
				digest.update(toBytes((int) (l >> 32)));
				digest.update(toBytes((int) (l & 0xFFFFFFFFL)));
			} else if (cst instanceof Double) {
				long l = Double.doubleToLongBits((Double) cst);
				digest.update(toBytes((int) (l >> 32)));
				digest.update(toBytes((int) (l & 0xFFFFFFFFL)));
			} else if (cst instanceof Type) {
				Type type = (Type) cst;
				digest.update(toBytes(type.getSort()));
				digest.update(type.getDescriptor().getBytes());
			} else if (cst instanceof Handle) {
				Handle handle = (Handle) cst;
				digest.update(handle.getOwner().getBytes());
				digest.update(handle.getName().getBytes());
				digest.update(handle.getDesc().getBytes());
				digest.update(toBytes(handle.getTag()));
			} else if (cst instanceof ConstantDynamic) {
				ConstantDynamic condy = (ConstantDynamic) cst;
				digest.update(condy.getName().getBytes());
				digest.update(condy.getDescriptor().getBytes());
				digest.update(condy.getBootstrapMethod().getOwner().getBytes());
				digest.update(condy.getBootstrapMethod().getName().getBytes());
				digest.update(condy.getBootstrapMethod().getDesc().getBytes());
				for (int i = 0; i < condy.getBootstrapMethodArgumentCount(); i++) {
					digest.update(condy.getBootstrapMethodArgument(i).toString().getBytes());
				}
			} else {
				digest.update(cst.toString().getBytes());
			}
		} else if (insn instanceof LineNumberNode) {
			LineNumberNode line = (LineNumberNode) insn;
			digest.update(toBytes(line.line));
			digest.update(toBytes(labelMap.get(line.start)));
		} else if (insn instanceof LookupSwitchInsnNode) {
			LookupSwitchInsnNode lookupSwitch = (LookupSwitchInsnNode) insn;
			digest.update(toBytes(labelMap.get(lookupSwitch.dflt)));
			for (LabelNode label : lookupSwitch.labels) {
				digest.update(toBytes(labelMap.get(label)));
			}
			for (Integer key : lookupSwitch.keys) {
				digest.update(toBytes(key));
			}
		} else if (insn instanceof MethodInsnNode) {
			MethodInsnNode method = (MethodInsnNode) insn;
			digest.update(method.owner.getBytes());
			digest.update(method.name.getBytes());
			digest.update(method.desc.getBytes());
		} else if (insn instanceof MultiANewArrayInsnNode) {
			MultiANewArrayInsnNode multiANewArray = (MultiANewArrayInsnNode) insn;
			digest.update(multiANewArray.desc.getBytes());
			digest.update(toBytes(multiANewArray.dims));
		} else if (insn instanceof TableSwitchInsnNode) {
			TableSwitchInsnNode tableSwitch = (TableSwitchInsnNode) insn;
			digest.update(toBytes(labelMap.get(tableSwitch.dflt)));
			for (LabelNode label : tableSwitch.labels) {
				digest.update(toBytes(labelMap.get(label)));
			}
			digest.update(toBytes(tableSwitch.min));
			digest.update(toBytes(tableSwitch.max));
		} else if (insn instanceof TypeInsnNode) {
			TypeInsnNode type = (TypeInsnNode) insn;
			digest.update(type.desc.getBytes());
		} else if (insn instanceof VarInsnNode) {
			VarInsnNode var = (VarInsnNode) insn;
			digest.update(toBytes(var.var));
		} else {
			// Hash codes are inconsistent, so we use the class name instead.
			digest.update(insn.getClass().getName().getBytes());
		}
	}

	private static MessageDigest getDigest() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			Utils.unsafe().throwException(e);
			throw new IllegalStateException(e);
		}
	}

	private static byte[] toBytes(int i) {
		byte[] bytes = new byte[4];
		bytes[0] = (byte) (i >> 24);
		bytes[1] = (byte) (i >> 16);
		bytes[2] = (byte) (i >> 8);
		bytes[3] = (byte) i;
		return bytes;
	}
}
