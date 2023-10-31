package org.duvetmc.rgml;

import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.client.render.BlockRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.texture.TextureAtlasSprite;
import net.minecraft.client.render.texture.TextureManager;
import net.minecraft.client.resource.pack.TexturePacks;
import net.minecraft.crafting.CraftingManager;
import net.minecraft.crafting.SmeltingManager;
import net.minecraft.crafting.recipe.Recipe;
import net.minecraft.entity.Entities;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.mob.MobCategory;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.locale.LanguageManager;
import net.minecraft.resource.language.I18n;
import net.minecraft.stat.ItemStat;
import net.minecraft.stat.Stat;
import net.minecraft.stat.Stats;
import net.minecraft.stat.achievement.AchievementStat;
import net.minecraft.unmapped.C_9672678;
import net.minecraft.world.WorldView;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.HellBiome;
import net.minecraft.world.biome.TheEndBiome;
import net.minecraft.world.chunk.ChunkSource;
import org.duvetmc.mods.rgmlquilt.util.RuntimeRemapUtil;
import org.lwjgl.input.Keyboard;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.*;
import java.util.Map.Entry;
import java.util.logging.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@SuppressWarnings({"unchecked", "unused"})
public final class ModLoader {
	private static final List<TextureAtlasSprite> animList = new LinkedList<>();
	private static final Map<Integer, BaseMod> blockModels = new HashMap<>();
	private static final Map<Integer, Boolean> blockSpecialInv = new HashMap<>();
	private static final File cfgdir = new File(Minecraft.getRunDirectory(), "/config/");
	private static final File cfgfile = new File(cfgdir, "ModLoader.cfg");
	public static Level cfgLoggingLevel = Level.FINER;
	private static Map<String, Class<? extends Entity>> classMap = null;
	private static long clock = 0L;
	public static final boolean DEBUG = false;
	private static Field field_animList = null;
	private static Field field_armorList = null;
	private static Field field_modifiers = null;
	private static Field field_TileEntityRenderers = null;
	private static boolean hasInit = false;
	private static int highestEntityId = 3000;
	private static final Map<BaseMod, Boolean> inGameHooks = new HashMap<>();
	private static final Map<BaseMod, Boolean> inGUIHooks = new HashMap<>();
	private static Minecraft instance = null;
	private static int itemSpriteIndex = 0;
	private static int itemSpritesLeft = 0;
	private static final Map<BaseMod, Map<KeyBinding, boolean[]>> keyList = new HashMap<>();
	private static final File logfile = new File(Minecraft.getRunDirectory(), "ModLoader.txt");
	private static final org.apache.logging.log4j.Logger logger = org.apache.logging.log4j.LogManager.getLogger("ModLoader");
	private static FileHandler logHandler = null;
	private static Method method_RegisterEntityID = null;
	private static Method method_RegisterTileEntity = null;
	private static final File modDir = new File(Minecraft.getRunDirectory(), "/mods/");
	private static final LinkedList<BaseMod> modList = new LinkedList<>();
	private static int nextBlockModelID = 1000;
	private static final Map<Integer, Map<String, Integer>> overrides = new HashMap<>();
	public static final Properties props = new Properties();
	private static Biome[] standardBiomes;
	private static int terrainSpriteIndex = 0;
	private static int terrainSpritesLeft = 0;
	private static String texPack = null;
	private static boolean texturesAdded = false;
	private static final boolean[] usedItemSprites = new boolean[256];
	private static final boolean[] usedTerrainSprites = new boolean[256];
	public static final String VERSION = "ModLoader Beta 1.8.1";

	private static final MethodHandle G_BlockRenderer_cfgGrassFix;
	private static final MethodHandle S_BlockRenderer_cfgGrassFix;

	static {
		try {
			//noinspection JavaLangInvokeHandleSignature
			G_BlockRenderer_cfgGrassFix = MethodHandles.lookup().findStaticGetter(BlockRenderer.class, "cfgGrassFix", boolean.class);
			//noinspection JavaLangInvokeHandleSignature
			S_BlockRenderer_cfgGrassFix = MethodHandles.lookup().findStaticSetter(BlockRenderer.class, "cfgGrassFix", boolean.class);
		} catch (NoSuchFieldException | IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

	public static void AddAchievementDesc(AchievementStat achievement, String name, String description) {
		try {
			if (achievement.name.contains(".")) {
				String[] split = achievement.name.split("\\.");
				if (split.length == 2) {
					String key = split[1];
					AddLocalization("achievement." + key, name);
					AddLocalization("achievement." + key + ".desc", description);
					setPrivateValue(Stat.class, achievement, 1, I18n.translate("achievement." + key));
					setPrivateValue(AchievementStat.class, achievement, 3, I18n.translate("achievement." + key + ".desc"));
				} else {
					setPrivateValue(Stat.class, achievement, 1, name);
					setPrivateValue(AchievementStat.class, achievement, 3, description);
				}
			} else {
				setPrivateValue(Stat.class, achievement, 1, name);
				setPrivateValue(AchievementStat.class, achievement, 3, description);
			}
		} catch (IllegalArgumentException | NoSuchFieldException | SecurityException var5) {
			logger.throwing(var5);
			ThrowException(var5);
		}
	}

	public static int AddAllFuel(int id, int metadata) {
		logger.trace("Finding fuel for " + id);
		int result = 0;
		Iterator<BaseMod> iter = modList.iterator();

		while(iter.hasNext() && result == 0) {
			result = iter.next().AddFuel(id, metadata);
		}

		if (result != 0) {
			logger.trace("Returned " + result);
		}

		return result;
	}

	public static void AddAllRenderers(Map<Class<? extends Entity>, EntityRenderer> renderers) {
		if (!hasInit) {
			init();
			logger.trace("Initialized");
		}

		for(BaseMod mod : modList) {
			mod.AddRenderer(renderers);
		}
	}

	public static void addAnimation(TextureAtlasSprite anim) {
		logger.trace("Adding animation " + anim.toString());

		for(TextureAtlasSprite oldAnim : animList) {
			if (oldAnim.sprite == anim.sprite && oldAnim.atlas == anim.atlas) {
				animList.remove(anim);
				break;
			}
		}

		animList.add(anim);
	}

	public static int AddArmor(String armor) {
		try {
			String[] existingArmor = (String[])field_armorList.get(null);
			List<String> existingArmorList = Arrays.asList(existingArmor);
			List<String> combinedList = new ArrayList<>(existingArmorList);
			if (!combinedList.contains(armor)) {
				combinedList.add(armor);
			}

			int index = combinedList.indexOf(armor);
			field_armorList.set(null, combinedList.toArray(new String[0]));
			return index;
		} catch (IllegalArgumentException | IllegalAccessException var5) {
			logger.error("Error in AddArmor", var5);
			ThrowException("An impossible error has occured!", var5);
		}

		return -1;
	}

	public static void AddLocalization(String key, String value) {
		Properties props = null;

		try {
			props = getPrivateValue(LanguageManager.class, LanguageManager.getInstance(), 1);
		} catch (SecurityException | NoSuchFieldException var4) {
			logger.error("Error in AddLocalization", var4);
			ThrowException(var4);
		}

		if (props != null) {
			props.put(key, value);
		}
	}

	private static void addMod(ClassLoader loader, String filename) {
		try {
			String name = filename.split("\\.")[0];
			if (name.contains("$")) {
				return;
			}

			if (props.containsKey(name) && (props.getProperty(name).equalsIgnoreCase("no") || props.getProperty(name).equalsIgnoreCase("off"))) {
				return;
			}

			Class<?> instclass = loader.loadClass(name);
			if (!BaseMod.class.isAssignableFrom(instclass)) {
				return;
			}

			setupProperties((Class<? extends BaseMod>) instclass);
			BaseMod mod = (BaseMod)instclass.newInstance();
			modList.add(mod);
			logger.trace("Mod Loaded: \"" + mod + "\" from " + filename);
			System.out.println("Mod Loaded: " + mod);
		} catch (Throwable var6) {
			logger.trace("Failed to load mod from \"" + filename + "\"");
			System.out.println("Failed to load mod from \"" + filename + "\"");
			logger.error("Error in addMod", var6);
			ThrowException(var6);
		}
	}

	public static void AddName(Object instance, String name) {
		String tag = null;
		if (instance instanceof Item) {
			Item item = (Item)instance;
			if (item.getTranslationKey() != null) {
				tag = item.getTranslationKey() + ".name";
			}
		} else if (instance instanceof Block) {
			Block block = (Block)instance;
			if (block.getTranslationKey() != null) {
				tag = block.getTranslationKey() + ".name";
			}
		} else if (instance instanceof ItemStack) {
			ItemStack stack = (ItemStack)instance;
			if (stack.getTranslationKey() != null) {
				tag = stack.getTranslationKey() + ".name";
			}
		} else {
			Exception e = new Exception(instance.getClass().getName() + " cannot have name attached to it!");
			logger.error("Error in AddName", e);
			ThrowException(e);
		}

		if (tag != null) {
			AddLocalization(tag, name);
		} else {
			Exception e = new Exception(instance + " is missing name tag!");
			logger.error("Error in AddName", e);
			ThrowException(e);
		}
	}

	public static int addOverride(String fileToOverride, String fileToAdd) {
		try {
			int i = getUniqueSpriteIndex(fileToOverride);
			addOverride(fileToOverride, fileToAdd, i);
			return i;
		} catch (Throwable var3) {
			logger.error("Error in addOverride", var3);
			ThrowException(var3);
			throw new RuntimeException(var3);
		}
	}

	public static void addOverride(String path, String overlayPath, int index) {
		int dst = -1;
		int left = 0;
		byte var6;
		if (path.equals("/terrain.png")) {
			var6 = 0;
			left = terrainSpritesLeft;
		} else {
			if (!path.equals("/gui/items.png")) {
				return;
			}

			var6 = 1;
			left = itemSpritesLeft;
		}

		System.out.println("Overriding " + path + " with " + overlayPath + " @ " + index + ". " + left + " left.");
		logger.trace("addOverride(" + path + "," + overlayPath + "," + index + "). " + left + " left.");
		Map<String, Integer> overlays = overrides.computeIfAbsent((int) var6, k -> new HashMap<>());

		overlays.put(overlayPath, index);
	}

	public static void AddRecipe(ItemStack output, Object... params) {
		CraftingManager.getInstance().registerShaped(output, params);
	}

	public static void AddShapelessRecipe(ItemStack output, Object... params) {
		CraftingManager.getInstance().registerShapeless(output, params);
	}

	public static void AddSmelting(int input, ItemStack output) {
		SmeltingManager.getInstance().register(input, output);
	}

	public static void AddSpawn(Class<? extends LivingEntity> entityClass, int weightedProb, int min, int max, MobCategory spawnList) {
		AddSpawn(entityClass, weightedProb, min, max, spawnList, (Biome[]) null);
	}

	public static void AddSpawn(Class<? extends LivingEntity> entityClass, int weightedProb, int min, int max, MobCategory spawnList, Biome... biomes) {
		if (entityClass == null) {
			throw new IllegalArgumentException("entityClass cannot be null");
		} else if (spawnList == null) {
			throw new IllegalArgumentException("spawnList cannot be null");
		} else {
			if (biomes == null) {
				biomes = standardBiomes;
			}

			for (Biome biome : biomes) {
				List<Biome.SpawnEntry> list = biome.getSpawnEntries(spawnList);
				if (list != null) {
					boolean exists = false;

					for (Biome.SpawnEntry entry : list) {
						if (entry.type == entityClass) {
							entry.weight = weightedProb;
							entry.minGroupSize = min;
							entry.maxGroupSize = max;
							exists = true;
							break;
						}
					}

					if (!exists) {
						list.add(new Biome.SpawnEntry(entityClass, weightedProb, min, max));
					}
				}
			}
		}
	}

	public static void AddSpawn(String entityName, int weightedProb, int min, int max, MobCategory spawnList) {
		AddSpawn(entityName, weightedProb, min, max, spawnList, (Biome[]) null);
	}

	public static void AddSpawn(String entityName, int weightedProb, int min, int max, MobCategory spawnList, Biome... biomes) {
		Class<? extends Entity> entityClass = classMap.get(entityName);
		if (entityClass != null && LivingEntity.class.isAssignableFrom(entityClass)) {
			AddSpawn((Class<? extends LivingEntity>) entityClass, weightedProb, min, max, spawnList, biomes);
		}
	}

	public static boolean DispenseEntity(World world, double x, double y, double z, int xVel, int zVel, ItemStack item) {
		boolean result = false;
		Iterator<BaseMod> iter = modList.iterator();

		while(iter.hasNext() && !result) {
			result = iter.next().DispenseEntity(world, x, y, z, xVel, zVel, item);
		}

		return result;
	}

	public static List<BaseMod> getLoadedMods() {
		return Collections.unmodifiableList(modList);
	}

	public static Logger getLogger() {
		return new Logger("ModLoader", null) {
			private org.apache.logging.log4j.Level fromJava(Level level) {
				if (level == Level.ALL) return org.apache.logging.log4j.Level.ALL;
				if (level == Level.CONFIG) return org.apache.logging.log4j.Level.INFO;
				if (level == Level.FINE || level == Level.FINER || level == Level.FINEST) return org.apache.logging.log4j.Level.DEBUG;
				if (level == Level.INFO) return org.apache.logging.log4j.Level.INFO;
				if (level == Level.SEVERE) return org.apache.logging.log4j.Level.ERROR;
				if (level == Level.WARNING) return org.apache.logging.log4j.Level.WARN;
				return org.apache.logging.log4j.Level.OFF;
			}
			@Override
			public void log(Level level, String msg) {
				logger.log(fromJava(level), msg);
			}
		};
	}

	public static Minecraft getMinecraftInstance() {
		if (instance == null) {
			try {
				ThreadGroup group = Thread.currentThread().getThreadGroup();
				int count = group.activeCount();
				Thread[] threads = new Thread[count];
				group.enumerate(threads);

				for (Thread value : threads) {
					System.out.println(value.getName());
				}

				for (Thread thread : threads) {
					if (thread.getName().equals("Minecraft main thread")) {
						instance = getPrivateValue(Thread.class, thread, "target");
						break;
					}
				}
			} catch (SecurityException | NoSuchFieldException var4) {
				logger.error("Error in getMinecraftInstance", var4);
				throw new RuntimeException(var4);
			}
		}

		return instance;
	}

	public static <T, E> T getPrivateValue(Class<? super E> instanceclass, E instance, int fieldindex) throws IllegalArgumentException, SecurityException, NoSuchFieldException {
		try {
			Field f = instanceclass.getDeclaredFields()[fieldindex];
			f.setAccessible(true);
			return (T)f.get(instance);
		} catch (IllegalAccessException var4) {
			logger.error("Error in getPrivateValue", var4);
			ThrowException("An impossible error has occured!", var4);
			return null;
		}
	}

	public static <T, E> T getPrivateValue(Class<? super E> instanceclass, E instance, String field) throws IllegalArgumentException, SecurityException, NoSuchFieldException {
		try {
			Field f = instanceclass.getDeclaredField(field);
			f.setAccessible(true);
			return (T)f.get(instance);
		} catch (IllegalAccessException var4) {
			logger.error("Error in getPrivateValue", var4);
			ThrowException("An impossible error has occured!", var4);
			return null;
		}
	}

	public static int getUniqueBlockModelID(BaseMod mod, boolean full3DItem) {
		int id = nextBlockModelID++;
		blockModels.put(id, mod);
		blockSpecialInv.put(id, full3DItem);
		return id;
	}

	public static int getUniqueEntityId() {
		return highestEntityId++;
	}

	private static int getUniqueItemSpriteIndex() {
		while(itemSpriteIndex < usedItemSprites.length) {
			if (!usedItemSprites[itemSpriteIndex]) {
				usedItemSprites[itemSpriteIndex] = true;
				--itemSpritesLeft;
				return itemSpriteIndex++;
			}

			++itemSpriteIndex;
		}

		Exception e = new Exception("No more empty item sprite indices left!");
		logger.error("Error in getUniqueItemSpriteIndex", e);
		ThrowException(e);
		return 0;
	}

	public static int getUniqueSpriteIndex(String path) {
		if (path.equals("/gui/items.png")) {
			return getUniqueItemSpriteIndex();
		} else if (path.equals("/terrain.png")) {
			return getUniqueTerrainSpriteIndex();
		} else {
			Exception e = new Exception("No registry for this texture: " + path);
			logger.error("Error in getUniqueItemSpriteIndex", e);
			ThrowException(e);
			return 0;
		}
	}

	private static int getUniqueTerrainSpriteIndex() {
		while(terrainSpriteIndex < usedTerrainSprites.length) {
			if (!usedTerrainSprites[terrainSpriteIndex]) {
				usedTerrainSprites[terrainSpriteIndex] = true;
				--terrainSpritesLeft;
				return terrainSpriteIndex++;
			}

			++terrainSpriteIndex;
		}

		Exception e = new Exception("No more empty terrain sprite indices left!");
		logger.error("Error in getUniqueItemSpriteIndex", e);
		ThrowException(e);
		return 0;
	}

	private static void init() {
		hasInit = true;
		String usedItemSpritesString = "1111111111111111111111111111111111111101111111011111111111111111111111111111111111111111111111111111110111110111111111000110001111111101100000110000000100000011000000010000001100000000000000110000000000000000000000000000000000000000000000001100000000000000";
		String usedTerrainSpritesString = "1111111111111111111111111111110111111111111111111111111111111111111111111111000111111111111111111111111111111111111111111111111111111111110011111111111110000000111111000000000011111100000000001111000000000111111000000000001101000000000001111111111111111111";

		for(int i = 0; i < 256; ++i) {
			usedItemSprites[i] = usedItemSpritesString.charAt(i) == '1';
			if (!usedItemSprites[i]) {
				++itemSpritesLeft;
			}

			usedTerrainSprites[i] = usedTerrainSpritesString.charAt(i) == '1';
			if (!usedTerrainSprites[i]) {
				++terrainSpritesLeft;
			}
		}

		try {
			instance = getPrivateValue(Minecraft.class, null, 1);
			assert instance != null;
			instance.gameRenderer = new EntityRendererProxy(instance);
			classMap = getPrivateValue(Entities.class, null, 0);
			field_modifiers = Field.class.getDeclaredField("modifiers");
			field_modifiers.setAccessible(true);
			field_TileEntityRenderers = BlockEntityRenderDispatcher.class.getDeclaredFields()[0];
			field_TileEntityRenderers.setAccessible(true);
			field_armorList = PlayerEntityRenderer.class.getDeclaredFields()[3];
			field_modifiers.setInt(field_armorList, field_armorList.getModifiers() & -17);
			field_armorList.setAccessible(true);
			field_animList = TextureManager.class.getDeclaredFields()[6];
			field_animList.setAccessible(true);
			Field[] fieldArray = Biome.class.getDeclaredFields();
			List<Biome> biomes = new LinkedList<>();

			for (Field field : fieldArray) {
				Class<?> fieldType = field.getType();
				if ((field.getModifiers() & 8) != 0 && fieldType.isAssignableFrom(Biome.class)) {
					Biome biome = (Biome) field.get(null);
					if (!(biome instanceof HellBiome) && !(biome instanceof TheEndBiome)) {
						biomes.add(biome);
					}
				}
			}

			standardBiomes = biomes.toArray(new Biome[0]);

			method_RegisterTileEntity = RuntimeRemapUtil.getRuntimeDeclaredMethod(BlockEntity.class, "a", new Class[] {Class.class, String.class});
			method_RegisterTileEntity.setAccessible(true);
			method_RegisterEntityID = RuntimeRemapUtil.getRuntimeDeclaredMethod(Entities.class, "a", new Class[] {Class.class, String.class, int.class});
			method_RegisterEntityID.setAccessible(true);
		} catch (SecurityException | IllegalAccessException | IllegalArgumentException | NoSuchMethodException |
				 NoSuchFieldException var10) {
			logger.error("Error in init", var10);
			ThrowException(var10);
			throw new RuntimeException(var10);
		}

		try {
			loadConfig();
			if (props.containsKey("grassFix")) {
				S_BlockRenderer_cfgGrassFix.invoke(Boolean.parseBoolean(props.getProperty("grassFix")));
			}

			if ((logfile.exists() || logfile.createNewFile()) && logfile.canWrite() && logHandler == null) {
				logHandler = new FileHandler(logfile.getPath());
				logHandler.setFormatter(new SimpleFormatter());
			}

			logger.trace("ModLoader Beta 1.8.1 Initializing...");
			System.out.println("ModLoader Beta 1.8.1 Initializing...");
//			File source = new File(ModLoader.class.getProtectionDomain().getCodeSource().getLocation().toURI());
			modDir.mkdirs();
			readFromModFolder(modDir);
//			readFromClassPath(source);
			System.out.println("Done.");
			props.setProperty("loggingLevel", cfgLoggingLevel.getName());
			props.setProperty("grassFix", Boolean.toString((boolean) G_BlockRenderer_cfgGrassFix.invoke()));

			for(BaseMod mod : modList) {
				mod.ModsLoaded();
				if (!props.containsKey(mod.getClass().getName())) {
					props.setProperty(mod.getClass().getName(), "on");
				}
			}

			instance.options.keyBindings = RegisterAllKeys(instance.options.keyBindings);
			instance.options.load();
			initStats();
			saveConfig();
		} catch (Throwable var9) {
			logger.error("Error in init", var9);
			ThrowException("ModLoader has failed to initialize.", var9);
			if (logHandler != null) {
				logHandler.close();
			}

			throw new RuntimeException(var9);
		}
	}

	private static void initStats() {
		for(int id = 0; id < Block.BY_ID.length; ++id) {
			if (!Stats.BY_KEY.containsKey(16777216 + id) && Block.BY_ID[id] != null && Block.BY_ID[id].hasStats()) {
				String str = LanguageManager.getInstance().translate("stat.mineBlock", Block.BY_ID[id].getName());
				Stats.BLOCKS_MINED[id] = new ItemStat(16777216 + id, str, id).register();
				Stats.MINED.add(Stats.BLOCKS_MINED[id]);
			}
		}

		for(int id = 0; id < Item.BY_ID.length; ++id) {
			if (!Stats.BY_KEY.containsKey(16908288 + id) && Item.BY_ID[id] != null) {
				String str = LanguageManager.getInstance().translate("stat.useItem", Item.BY_ID[id].getDisplayName());
				Stats.ITEMS_USED[id] = new ItemStat(16908288 + id, str, id).register();
				if (id >= Block.BY_ID.length) {
					Stats.USED.add(Stats.ITEMS_USED[id]);
				}
			}

			if (!Stats.BY_KEY.containsKey(16973824 + id) && Item.BY_ID[id] != null && Item.BY_ID[id].isDamageable()) {
				String str = LanguageManager.getInstance().translate("stat.breakItem", Item.BY_ID[id].getDisplayName());
				Stats.ITEMS_BROKEN[id] = new ItemStat(16973824 + id, str, id).register();
			}
		}

		HashSet<Integer> idHashSet = new HashSet<>();

		for(Object result : CraftingManager.getInstance().getRecipes()) {
			idHashSet.add(((Recipe)result).getResult().itemId);
		}

		for(Object result : SmeltingManager.getInstance().getRecipes().values()) {
			idHashSet.add(((ItemStack)result).itemId);
		}

		for(int id : idHashSet) {
			if (!Stats.BY_KEY.containsKey(16842752 + id) && Item.BY_ID[id] != null) {
				String str = LanguageManager.getInstance().translate("stat.craftItem", Item.BY_ID[id].getDisplayName());
				Stats.ITEMS_CRAFTED[id] = new ItemStat(16842752 + id, str, id).register();
			}
		}
	}

	public static boolean isGUIOpen(Class<? extends Screen> gui) {
		Minecraft game = getMinecraftInstance();
		if (gui == null) {
			return game.screen == null;
		} else {
			return gui.isInstance(game.screen);
		}
	}

	public static boolean isModLoaded(String modname) {
		Class<?> chk = null;

		try {
			chk = Class.forName(modname);
		} catch (ClassNotFoundException var4) {
			return false;
		}

		for (BaseMod mod : modList) {
			if (chk.isInstance(mod)) {
				return true;
			}
		}

		return false;
	}

	public static void loadConfig() throws IOException {
		cfgdir.mkdir();
		if (cfgfile.exists() || cfgfile.createNewFile()) {
			if (cfgfile.canRead()) {
				InputStream in = Files.newInputStream(cfgfile.toPath());
				props.load(in);
				in.close();
			}
		}
	}

	public static BufferedImage loadImage(TextureManager texCache, String path) throws Exception {
		TexturePacks pack = getPrivateValue(TextureManager.class, texCache, 11);
		assert pack != null;
		InputStream input = pack.selected.getResource(path);
		if (input == null) {
			throw new Exception("Image not found: " + path);
		} else {
			BufferedImage image = ImageIO.read(input);
			if (image == null) {
				throw new Exception("Image corrupted: " + path);
			} else {
				return image;
			}
		}
	}

	public static void OnItemPickup(PlayerEntity player, ItemStack item) {
		for(BaseMod mod : modList) {
			mod.OnItemPickup(player, item);
		}
	}

	public static void OnTick(float tick, Minecraft game) {
		if (!hasInit) {
			init();
			logger.trace("Initialized");
		}

		if (texPack == null || game.options.skin != texPack) {
			texturesAdded = false;
			texPack = game.options.skin;
		}

		if (!texturesAdded && game.textureManager != null) {
			RegisterAllTextureOverrides(game.textureManager);
			texturesAdded = true;
		}

		long newclock = 0L;
		if (game.world != null) {
			newclock = game.world.getTime();
			Iterator<Entry<BaseMod, Boolean>> iter = inGameHooks.entrySet().iterator();

			while(iter.hasNext()) {
				Entry<BaseMod, Boolean> modSet = iter.next();
				if ((clock != newclock || !modSet.getValue()) && !modSet.getKey().OnTickInGame(tick, game)) {
					iter.remove();
				}
			}
		}

		if (game.screen != null) {
			Iterator<Entry<BaseMod, Boolean>> iter = inGUIHooks.entrySet().iterator();

			while(iter.hasNext()) {
				Entry<BaseMod, Boolean> modSet = iter.next();
				if ((clock != newclock || !(modSet.getValue() & game.world != null)) && !modSet.getKey().OnTickInGUI(tick, game, game.screen)) {
					iter.remove();
				}
			}
		}

		if (clock != newclock) {
			for(Entry<BaseMod, Map<KeyBinding, boolean[]>> modSet : keyList.entrySet()) {
				for(Entry<KeyBinding, boolean[]> keySet : modSet.getValue().entrySet()) {
					boolean state = Keyboard.isKeyDown(keySet.getKey().keyCode);
					boolean[] keyInfo = keySet.getValue();
					boolean oldState = keyInfo[1];
					keyInfo[1] = state;
					if (state && (!oldState || keyInfo[0])) {
						modSet.getKey().KeyboardEvent(keySet.getKey());
					}
				}
			}
		}

		clock = newclock;
	}

	public static void OpenGUI(PlayerEntity player, Screen gui) {
		if (!hasInit) {
			init();
			logger.trace("Initialized");
		}

		Minecraft game = getMinecraftInstance();
		if (game.player == player) {
			if (gui != null) {
				game.openScreen(gui);
			}
		}
	}

	public static void PopulateChunk(ChunkSource generator, int chunkX, int chunkZ, World world) {
		if (!hasInit) {
			init();
			logger.trace("Initialized");
		}

		Random rnd = new Random(world.getSeed());
		long xSeed = rnd.nextLong() / 2L * 2L + 1L;
		long zSeed = rnd.nextLong() / 2L * 2L + 1L;
		rnd.setSeed((long)chunkX * xSeed + (long)chunkZ * zSeed ^ world.getSeed());

		for(BaseMod mod : modList) {
			if (generator.getName().equals("RandomLevelSource")) {
				mod.GenerateSurface(world, rnd, chunkX << 4, chunkZ << 4);
			} else if (generator.getName().equals("HellRandomLevelSource")) {
				mod.GenerateNether(world, rnd, chunkX << 4, chunkZ << 4);
			}
		}
	}

	private static void readFromClassPath(File source) throws IOException {
		logger.trace("Adding mods from " + source.getCanonicalPath());
		ClassLoader loader = ModLoader.class.getClassLoader();
		if (source.isFile() && (source.getName().endsWith(".jar") || source.getName().endsWith(".zip"))) {
			logger.trace("Zip found.");
			InputStream input = Files.newInputStream(source.toPath());
			ZipInputStream zip = new ZipInputStream(input);
			ZipEntry entry = null;

			while(true) {
				entry = zip.getNextEntry();
				if (entry == null) {
					input.close();
					break;
				}

				String name = entry.getName();
				if (!entry.isDirectory() && name.startsWith("mod_") && name.endsWith(".class")) {
					addMod(loader, name);
				}
			}
		} else if (source.isDirectory()) {
			Package pkg = ModLoader.class.getPackage();
			if (pkg != null) {
				String pkgdir = pkg.getName().replace('.', File.separatorChar);
				source = new File(source, pkgdir);
			}

			logger.trace("Directory found.");
			File[] files = source.listFiles();
			if (files != null) {
				for (File file : files) {
					String name = file.getName();
					if (file.isFile() && name.startsWith("mod_") && name.endsWith(".class")) {
						addMod(loader, name);
					}
				}
			}
		}
	}

	private static void readFromModFolder(File folder) throws IOException, IllegalArgumentException, IllegalAccessException, InvocationTargetException, SecurityException, NoSuchMethodException {
		ClassLoader loader = Minecraft.class.getClassLoader();
		Method addURL = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
		addURL.setAccessible(true);
		if (!folder.isDirectory()) {
			throw new IllegalArgumentException("folder must be a Directory.");
		} else {
			File[] sourcefiles = folder.listFiles();
			if (loader instanceof URLClassLoader) {
				assert sourcefiles != null;
				for (File source : sourcefiles) {
					if (source.isDirectory() || source.isFile() && (source.getName().endsWith(".jar") || source.getName().endsWith(".zip"))) {
						addURL.invoke(loader, source.toURI().toURL());
					}
				}
			}

			assert sourcefiles != null;
			for (File sourcefile : sourcefiles) {
				File source = sourcefile;
				if (source.isDirectory() || source.isFile() && (source.getName().endsWith(".jar") || source.getName().endsWith(".zip"))) {
					logger.trace("Adding mods from " + source.getCanonicalPath());
					if (!source.isFile()) {
						if (source.isDirectory()) {
							Package pkg = ModLoader.class.getPackage();
							if (pkg != null) {
								String pkgdir = pkg.getName().replace('.', File.separatorChar);
								source = new File(source, pkgdir);
							}

							logger.trace("Directory found.");
							File[] dirfiles = source.listFiles();
							if (dirfiles != null) {
								for (File dirfile : dirfiles) {
									String name = dirfile.getName();
									if (dirfile.isFile() && name.startsWith("mod_") && name.endsWith(".class")) {
										addMod(loader, name);
									}
								}
							}
						}
					} else {
						logger.trace("Zip found.");
						InputStream input = Files.newInputStream(source.toPath());
						ZipInputStream zip = new ZipInputStream(input);
						ZipEntry entry = null;

						while (true) {
							entry = zip.getNextEntry();
							if (entry == null) {
								zip.close();
								input.close();
								break;
							}

							String name = entry.getName();
							if (!entry.isDirectory() && name.startsWith("mod_") && name.endsWith(".class")) {
								addMod(loader, name);
							}
						}
					}
				}
			}
		}
	}

	public static KeyBinding[] RegisterAllKeys(KeyBinding[] keys) {
		List<KeyBinding> combinedList = new LinkedList<>(Arrays.asList(keys));

		for(Map<KeyBinding, boolean[]> keyMap : keyList.values()) {
			combinedList.addAll(keyMap.keySet());
		}

		return combinedList.toArray(new KeyBinding[0]);
	}

	public static void RegisterAllTextureOverrides(TextureManager cache) {
		animList.clear();
		Minecraft game = getMinecraftInstance();

		for(BaseMod mod : modList) {
			mod.RegisterAnimation(game);
		}

		for(TextureAtlasSprite anim : animList) {
			cache.addSprite(anim);
		}

		for(Entry<Integer, Map<String, Integer>> overlay : overrides.entrySet()) {
			for(Entry<String, Integer> overlayEntry : overlay.getValue().entrySet()) {
				String overlayPath = overlayEntry.getKey();
				int index = overlayEntry.getValue();
				int dst = overlay.getKey();

				try {
					BufferedImage im = loadImage(cache, overlayPath);
					TextureAtlasSprite anim = new ModTextureStatic(index, dst, im);
					cache.addSprite(anim);
				} catch (Exception var11) {
					logger.error("Error in RegisterAllTextureOverrides", var11);
					ThrowException(var11);
					throw new RuntimeException(var11);
				}
			}
		}
	}

	public static void RegisterBlock(Block block) {
		RegisterBlock(block, null);
	}

	public static void RegisterBlock(Block block, Class<? extends BlockItem> itemclass) {
		try {
			if (block == null) {
				throw new IllegalArgumentException("block parameter cannot be null.");
			}

			int id = block.id;
			BlockItem item = null;
			if (itemclass != null) {
				item = itemclass.getConstructor(Integer.TYPE).newInstance(id - 256);
			} else {
				item = new BlockItem(id - 256);
			}

			if (Block.BY_ID[id] != null && Item.BY_ID[id] == null) {
				Item.BY_ID[id] = item;
			}
		} catch (IllegalArgumentException | NoSuchMethodException | InvocationTargetException | InstantiationException |
				 SecurityException | IllegalAccessException var4) {
			logger.error("Error in RegisterBlock", var4);
			ThrowException(var4);
		}
	}

	public static void RegisterEntityID(Class<? extends Entity> entityClass, String entityName, int id) {
		try {
			method_RegisterEntityID.invoke(null, entityClass, entityName, id);
		} catch (IllegalArgumentException | InvocationTargetException | IllegalAccessException var4) {
			logger.error("Error in RegisterEntityID", var4);
			ThrowException(var4);
		}
	}

	public static void RegisterKey(BaseMod mod, KeyBinding keyHandler, boolean allowRepeat) {
		Map<KeyBinding, boolean[]> keyMap = keyList.get(mod);
		if (keyMap == null) {
			keyMap = new HashMap<>();
		}

		keyMap.put(keyHandler, new boolean[]{allowRepeat, false});
		keyList.put(mod, keyMap);
	}

	public static void RegisterTileEntity(Class<? extends BlockEntity> tileEntityClass, String id) {
		RegisterTileEntity(tileEntityClass, id, null);
	}

	public static void RegisterTileEntity(Class<? extends BlockEntity> tileEntityClass, String id, BlockEntityRenderer renderer) {
		try {
			method_RegisterTileEntity.invoke(null, tileEntityClass, id);
			if (renderer != null) {
				BlockEntityRenderDispatcher ref = BlockEntityRenderDispatcher.INSTANCE;
				Map<Class<? extends BlockEntity>, BlockEntityRenderer> renderers = (Map)field_TileEntityRenderers.get(ref);
				renderers.put(tileEntityClass, renderer);
				renderer.init(ref);
			}
		} catch (IllegalArgumentException | InvocationTargetException | IllegalAccessException var5) {
			logger.error("Error in RegisterTileEntity", var5);
			ThrowException(var5);
		}
	}

	public static void RemoveSpawn(Class<? extends LivingEntity> entityClass, MobCategory spawnList) {
		RemoveSpawn(entityClass, spawnList, (Biome[]) null);
	}

	public static void RemoveSpawn(Class<? extends LivingEntity> entityClass, MobCategory spawnList, Biome... biomes) {
		if (entityClass == null) {
			throw new IllegalArgumentException("entityClass cannot be null");
		} else if (spawnList == null) {
			throw new IllegalArgumentException("spawnList cannot be null");
		} else {
			if (biomes == null) {
				biomes = standardBiomes;
			}

			for (Biome biome : biomes) {
				List<Biome.SpawnEntry> list = biome.getSpawnEntries(spawnList);
				if (list != null) {
					list.removeIf(entry -> entry.type == entityClass);
				}
			}
		}
	}

	public static void RemoveSpawn(String entityName, MobCategory spawnList) {
		RemoveSpawn(entityName, spawnList, (Biome[]) null);
	}

	public static void RemoveSpawn(String entityName, MobCategory spawnList, Biome... biomes) {
		Class<? extends Entity> entityClass = classMap.get(entityName);
		if (entityClass != null && LivingEntity.class.isAssignableFrom(entityClass)) {
			RemoveSpawn((Class<? extends LivingEntity>) entityClass, spawnList, biomes);
		}
	}

	public static boolean RenderBlockIsItemFull3D(int modelID) {
		if (!blockSpecialInv.containsKey(modelID)) {
			return modelID == 16;
		} else {
			return blockSpecialInv.get(modelID);
		}
	}

	public static void RenderInvBlock(BlockRenderer renderer, Block block, int metadata, int modelID) {
		BaseMod mod = blockModels.get(modelID);
		if (mod != null) {
			mod.RenderInvBlock(renderer, block, metadata, modelID);
		}
	}

	public static boolean RenderWorldBlock(BlockRenderer renderer, WorldView world, int x, int y, int z, Block block, int modelID) {
		BaseMod mod = blockModels.get(modelID);
		return mod != null && mod.RenderWorldBlock(renderer, world, x, y, z, block, modelID);
	}

	public static void saveConfig() throws IOException {
		cfgdir.mkdir();
		if (cfgfile.exists() || cfgfile.createNewFile()) {
			if (cfgfile.canWrite()) {
				OutputStream out = Files.newOutputStream(cfgfile.toPath());
				props.store(out, "ModLoader Config");
				out.close();
			}
		}
	}

	public static void SetInGameHook(BaseMod mod, boolean enable, boolean useClock) {
		if (enable) {
			inGameHooks.put(mod, useClock);
		} else {
			inGameHooks.remove(mod);
		}
	}

	public static void SetInGUIHook(BaseMod mod, boolean enable, boolean useClock) {
		if (enable) {
			inGUIHooks.put(mod, useClock);
		} else {
			inGUIHooks.remove(mod);
		}
	}

	public static <T, E> void setPrivateValue(Class<? super T> instanceclass, T instance, int fieldindex, E value) throws IllegalArgumentException, SecurityException, NoSuchFieldException {
		try {
			Field f = instanceclass.getDeclaredFields()[fieldindex];
			f.setAccessible(true);
			int modifiers = field_modifiers.getInt(f);
			if ((modifiers & 16) != 0) {
				field_modifiers.setInt(f, modifiers & -17);
			}

			f.set(instance, value);
		} catch (IllegalAccessException var6) {
			logger.error("Error in setPrivateValue", var6);
			ThrowException("An impossible error has occurred!", var6);
		}
	}

	public static <T, E> void setPrivateValue(Class<? super T> instanceclass, T instance, String field, E value) throws IllegalArgumentException, SecurityException, NoSuchFieldException {
		try {
			Field f = instanceclass.getDeclaredField(field);
			int modifiers = field_modifiers.getInt(f);
			if ((modifiers & 16) != 0) {
				field_modifiers.setInt(f, modifiers & -17);
			}

			f.setAccessible(true);
			f.set(instance, value);
		} catch (IllegalAccessException var6) {
			logger.error("Error in setPrivateValue", var6);
			ThrowException("An impossible error has occurred!", var6);
		}
	}

	private static void setupProperties(Class<? extends BaseMod> mod) throws IllegalArgumentException, IllegalAccessException, IOException, SecurityException, NoSuchFieldException {
		Properties modprops = new Properties();
		File modcfgfile = new File(cfgdir, mod.getName() + ".cfg");
		if (modcfgfile.exists() && modcfgfile.canRead()) {
			modprops.load(Files.newInputStream(modcfgfile.toPath()));
		}

		StringBuilder helptext = new StringBuilder();

		Field[] var7;
		for(Field field : var7 = mod.getFields()) {
			if ((field.getModifiers() & 8) != 0 && field.isAnnotationPresent(MLProp.class)) {
				Class<?> type = field.getType();
				MLProp annotation = field.getAnnotation(MLProp.class);
				String key = annotation.name().length() == 0 ? field.getName() : annotation.name();
				Object currentvalue = field.get(null);
				StringBuilder range = new StringBuilder();
				if (annotation.min() != Double.NEGATIVE_INFINITY) {
					range.append(String.format(",>=%.1f", annotation.min()));
				}

				if (annotation.max() != Double.POSITIVE_INFINITY) {
					range.append(String.format(",<=%.1f", annotation.max()));
				}

				StringBuilder info = new StringBuilder();
				if (annotation.info().length() > 0) {
					info.append(" -- ");
					info.append(annotation.info());
				}

				helptext.append(String.format("%s (%s:%s%s)%s\n", key, type.getName(), currentvalue, range, info));
				if (modprops.containsKey(key)) {
					String strvalue = modprops.getProperty(key);
					Object value = null;
					if (type.isAssignableFrom(String.class)) {
						value = strvalue;
					} else if (type.isAssignableFrom(Integer.TYPE)) {
						value = Integer.parseInt(strvalue);
					} else if (type.isAssignableFrom(Short.TYPE)) {
						value = Short.parseShort(strvalue);
					} else if (type.isAssignableFrom(Byte.TYPE)) {
						value = Byte.parseByte(strvalue);
					} else if (type.isAssignableFrom(Boolean.TYPE)) {
						value = Boolean.parseBoolean(strvalue);
					} else if (type.isAssignableFrom(Float.TYPE)) {
						value = Float.parseFloat(strvalue);
					} else if (type.isAssignableFrom(Double.TYPE)) {
						value = Double.parseDouble(strvalue);
					}

					if (value != null) {
						if (value instanceof Number) {
							double num = ((Number)value).doubleValue();
							if (annotation.min() != Double.NEGATIVE_INFINITY && num < annotation.min()
								|| annotation.max() != Double.POSITIVE_INFINITY && num > annotation.max()) {
								continue;
							}
						}

						logger.trace(key + " set to " + value);
						if (!value.equals(currentvalue)) {
							field.set(null, value);
						}
					}
				} else {
					logger.trace(key + " not in config, using default: " + currentvalue);
					modprops.setProperty(key, currentvalue.toString());
				}
			}
		}

		if (!modprops.isEmpty() && (modcfgfile.exists() || modcfgfile.createNewFile()) && modcfgfile.canWrite()) {
			modprops.store(Files.newOutputStream(modcfgfile.toPath()), helptext.toString());
		}
	}

	public static void TakenFromCrafting(PlayerEntity player, ItemStack item, Inventory matrix) {
		for(BaseMod mod : modList) {
			mod.TakenFromCrafting(player, item, matrix);
		}
	}

	public static void TakenFromFurnace(PlayerEntity player, ItemStack item) {
		for(BaseMod mod : modList) {
			mod.TakenFromFurnace(player, item);
		}
	}

	public static void ThrowException(String message, Throwable e) {
		Minecraft game = getMinecraftInstance();
		if (game != null) {
			game.printCrashReport(new C_9672678(message, e));
		} else {
			throw new RuntimeException(e);
		}
	}

	private static void ThrowException(Throwable e) {
		ThrowException("Exception occurred in ModLoader", e);
	}

	private ModLoader() {
	}
}
