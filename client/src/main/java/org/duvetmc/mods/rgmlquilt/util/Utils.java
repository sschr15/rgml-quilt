package org.duvetmc.mods.rgmlquilt.util;

import sun.misc.Unsafe;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

public class Utils {
	private static final Unsafe UNSAFE;
	private static final MethodHandles.Lookup TRUSTED_LOOKUP;

	private static final MethodHandle computeHash;

	static {
		try {
			Field f = Unsafe.class.getDeclaredField("theUnsafe");
			f.setAccessible(true);
			UNSAFE = (Unsafe) f.get(null);

			Field f2 = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
			long offset = UNSAFE.staticFieldOffset(f2);
			Object base = UNSAFE.staticFieldBase(f2);
			TRUSTED_LOOKUP = (MethodHandles.Lookup) UNSAFE.getObject(base, offset);

			Class<?> HashUtil = Class.forName("org.quiltmc.loader.impl.util.HashUtil");
			computeHash = TRUSTED_LOOKUP.findStatic(HashUtil, "computeHash", MethodType.methodType(byte[].class, Path.class));
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	public static Unsafe unsafe() {
		return UNSAFE;
	}

	public static MethodHandles.Lookup lookup() {
		return TRUSTED_LOOKUP;
	}

	public static InputStream fileInputStream(Path p) {
		try {
			return Files.newInputStream(p);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static void deleteRecursively(Path p) {
		try {
			if (!Files.isDirectory(p)) {
				Files.deleteIfExists(p);
				return;
			}

			Files.list(p).forEach(Utils::deleteRecursively);
		} catch (IOException e) {
			unsafe().throwException(e);
			throw new RuntimeException(e);
		}
	}

	public static byte[] computeHash(Path p) {
		try {
			return (byte[]) computeHash.invokeExact(p);
		} catch (Throwable t) {
			unsafe().throwException(t);
			throw new RuntimeException(t);
		}
	}
}
