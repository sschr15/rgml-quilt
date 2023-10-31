package org.duvetmc.rgml.mixin;

import net.minecraft.block.DispenserBlock;
import net.minecraft.block.entity.DispenserBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.duvetmc.rgml.ModLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.Random;

@Mixin(DispenserBlock.class)
public class DispenserBlockMixin {
	@Inject(method = "dispense", at = @At(value = "FIELD", target = "Lnet/minecraft/item/Item;ARROW:Lnet/minecraft/item/Item;"), cancellable = true, locals = LocalCapture.CAPTURE_FAILHARD)
	private void rgml$dispense(World world, int x, int y, int z, Random random, CallbackInfo ci, int i, int xVel, int zVel, DispenserBlockEntity e, ItemStack stack, double d, double f, double g) {
		boolean handled = ModLoader.DispenseEntity(world, x, y, z, xVel, zVel, stack);
		if (handled) {
			ci.cancel();
		}
	}
}
