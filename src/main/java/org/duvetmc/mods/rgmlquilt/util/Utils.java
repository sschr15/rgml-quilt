package org.duvetmc.mods.rgmlquilt.util;

import org.quiltmc.loader.api.plugin.QuiltPluginContext;
import sun.misc.Unsafe;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class Utils {
	private static final Unsafe UNSAFE;
	private static final MethodHandles.Lookup TRUSTED_LOOKUP;

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

	public static <T extends Throwable> RuntimeException rethrow(Throwable t) throws T {
		unsafe().throwException(t);
		throw (T) t;
	}

	public static InputStream fileInputStream(Path p) {
		try {
			return Files.newInputStream(p);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static Path loadZip(QuiltPluginContext context, Path zip) {
		try {
			return context.manager().loadZipNow(zip);
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
