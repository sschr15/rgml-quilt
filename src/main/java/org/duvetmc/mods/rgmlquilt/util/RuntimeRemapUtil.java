package org.duvetmc.mods.rgmlquilt.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class RuntimeRemapUtil {
	private static final Map<Class<?>, String> PRIMITIVE_LOOKUP = new HashMap<>();

	static {
		PRIMITIVE_LOOKUP.put(Boolean.TYPE, "Z");
		PRIMITIVE_LOOKUP.put(Byte.TYPE, "B");
		PRIMITIVE_LOOKUP.put(Character.TYPE, "C");
		PRIMITIVE_LOOKUP.put(Short.TYPE, "S");
		PRIMITIVE_LOOKUP.put(Integer.TYPE, "I");
		PRIMITIVE_LOOKUP.put(Long.TYPE, "J");
		PRIMITIVE_LOOKUP.put(Float.TYPE, "F");
		PRIMITIVE_LOOKUP.put(Double.TYPE, "D");
		PRIMITIVE_LOOKUP.put(Void.TYPE, "V");
	}

	public static Class<?> getRuntimeClass(String name) throws ClassNotFoundException {
		try {
			return Class.forName(name);
		} catch (ClassNotFoundException e) {
			return Class.forName(Remap.remapClassName(name));
		}
	}

	public static Field getRuntimeField(Class<?> containingClass, String name) throws NoSuchFieldException {
		try {
			return containingClass.getField(name);
		} catch (NoSuchFieldException e) {
			String fieldName = Remap.remapFieldName(Remap.unmapClassName(containingClass.getName()), name);
			if (fieldName == null) throw e;
			return containingClass.getField(fieldName);
		}
	}

	public static Field getRuntimeDeclaredField(Class<?> containingClass, String name) throws NoSuchFieldException {
		try {
			return containingClass.getDeclaredField(name);
		} catch (NoSuchFieldException e) {
			String fieldName = Remap.remapFieldName(Remap.unmapClassName(containingClass.getName()), name);
			if (fieldName == null) throw e;
			return containingClass.getDeclaredField(fieldName);
		}
	}

	private static String toDesc(Class<?>[] params, boolean unmap) {
		StringBuilder builder = new StringBuilder();
		builder.append("(");
		for (Class<?> param : params) {
			if (param.isPrimitive()) {
				builder.append(PRIMITIVE_LOOKUP.get(param));
			} else {
				String unmappedName = Remap.unmapClassName(param.getName());
				if (unmappedName == null) unmappedName = param.getName();
				builder.append("L")
					.append((unmap ? unmappedName : param.getName()).replace('.', '/'))
					.append(";");
			}
		}
		builder.append(")");
		return builder.toString();
	}

	public static Method getRuntimeMethod(Class<?> containingClass, String name, Class<?>[] parameterTypes) throws NoSuchMethodException {
		try {
			return containingClass.getMethod(name, parameterTypes);
		} catch (NoSuchMethodException e) {
			String methodName = Remap.remapMethodName(Remap.unmapClassName(containingClass.getName()), name, toDesc(parameterTypes, true));
			if (methodName == null) throw e;
			return containingClass.getMethod(methodName, parameterTypes);
		}
	}

	public static Method getRuntimeDeclaredMethod(Class<?> containingClass, String name, Class<?>[] parameterTypes) throws NoSuchMethodException {
		try {
			return containingClass.getDeclaredMethod(name, parameterTypes);
		} catch (NoSuchMethodException e) {
			String methodName = Remap.remapMethodName(Remap.unmapClassName(containingClass.getName()), name, toDesc(parameterTypes, true));
			if (methodName == null) throw e;
			return containingClass.getDeclaredMethod(methodName, parameterTypes);
		}
	}
}
