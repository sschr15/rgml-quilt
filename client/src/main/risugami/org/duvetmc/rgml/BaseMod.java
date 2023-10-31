package org.duvetmc.rgml;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.client.render.BlockRenderer;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.world.WorldView;
import net.minecraft.world.World;

import java.util.Map;
import java.util.Random;

@SuppressWarnings("unused")
public abstract class BaseMod {
	public int AddFuel(int id, int metadata) {
		return 0;
	}

	public void AddRenderer(Map<Class<? extends Entity>, EntityRenderer> renderers) {
	}

	public boolean DispenseEntity(World world, double x, double y, double z, int xVel, int zVel, ItemStack item) {
		return false;
	}

	public void GenerateNether(World world, Random random, int chunkX, int chunkZ) {
	}

	public void GenerateSurface(World world, Random random, int chunkX, int chunkZ) {
	}

	public void KeyboardEvent(KeyBinding event) {
	}

	public void ModsLoaded() {
	}

	public boolean OnTickInGame(float tick, Minecraft game) {
		return this.OnTickInGame(game);
	}

	@Deprecated
	public boolean OnTickInGame(Minecraft game) {
		return false;
	}

	public boolean OnTickInGUI(float tick, Minecraft game, Screen gui) {
		return this.OnTickInGUI(game, gui);
	}

	@Deprecated
	public boolean OnTickInGUI(Minecraft game, Screen gui) {
		return false;
	}

	public void RegisterAnimation(Minecraft game) {
	}

	public void RenderInvBlock(BlockRenderer renderer, Block block, int metadata, int modelID) {
	}

	public boolean RenderWorldBlock(BlockRenderer renderer, WorldView world, int x, int y, int z, Block block, int modelID) {
		return false;
	}

	public void TakenFromCrafting(PlayerEntity player, ItemStack item, Inventory matrix) {
	}

	public void TakenFromFurnace(PlayerEntity player, ItemStack item) {
	}

	public void OnItemPickup(PlayerEntity player, ItemStack item) {
	}

	public String toString() {
		return this.getClass().getName() + " " + this.Version();
	}

	public abstract String Version();
}
