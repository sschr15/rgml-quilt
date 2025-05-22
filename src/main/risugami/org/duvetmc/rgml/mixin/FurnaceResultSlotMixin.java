package org.duvetmc.rgml.mixin;

import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.inventory.slot.FurnaceResultSlot;
import net.minecraft.item.ItemStack;
import org.duvetmc.rgml.ModLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FurnaceResultSlot.class)
public class FurnaceResultSlotMixin {
	@Shadow
	private PlayerEntity player;

	@Inject(method = "onStackRemovedByPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/inventory/slot/InventorySlot;onStackRemovedByPlayer(Lnet/minecraft/item/ItemStack;)V"))
	private void rgml$onStackRemovedByPlayer(ItemStack stack, CallbackInfo ci) {
		ModLoader.TakenFromFurnace(player, stack);
	}
}
