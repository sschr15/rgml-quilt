package org.duvetmc.mods.rgmlquilt.util;

import org.quiltmc.loader.api.plugin.QuiltPluginContext;
import sun.misc.Unsafe;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class Utils {
	private static final Unsafe UNSAFE;
	private static final MethodHandles.Lookup TRUSTED_LOOKUP;
	private static MethodHandle QuiltPluginManagerImpl_loadZip0;

	static {
		try {
			Field f = Unsafe.class.getDeclaredField("theUnsafe");
			f.setAccessible(true);
			UNSAFE = (Unsafe) f.get(null);

			Field f2 = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
			long offset = UNSAFE.staticFieldOffset(f2);
			Object base = UNSAFE.staticFieldBase(f2);
			TRUSTED_LOOKUP = (MethodHandles.Lookup) UNSAFE.getObject(base, offset);
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

	public static Path loadZip(QuiltPluginContext context, Path zip) {
		try {
			if (QuiltPluginManagerImpl_loadZip0 == null) {
					QuiltPluginManagerImpl_loadZip0 = Utils.lookup().findVirtual(
						Class.forName("org.quiltmc.loader.impl.plugin.QuiltPluginManagerImpl"),
						"loadZip0",
						MethodType.methodType(Path.class, Path.class)
					);
			}

			return (Path) QuiltPluginManagerImpl_loadZip0.invoke(context.manager(), zip);
		} catch (Throwable t) {
			unsafe().throwException(t);
			throw new RuntimeException(t);
		}
	}

	public static Stream<Path> walk(Path path) {
		try {
			return Files.walk(path);
		} catch (IOException e) {
			unsafe().throwException(e);
			throw new RuntimeException(e);
		}
	}
}
