package org.duvetmc.rgml.mixin;

import net.minecraft.block.FurnaceBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.FurnaceBlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(FurnaceBlockEntity.class)
public abstract class FurnaceBlockEntityMixin extends BlockEntity implements Inventory {
	@Shadow
	public int fuelTime;

	@Shadow
	protected abstract boolean canCook();

	@Shadow
	public int totalFuelTime;

	@Shadow
	protected abstract int getFuelTime(ItemStack itemStack);

	@Shadow
	private ItemStack[] inventory;

	@Shadow
	public abstract boolean hasFuel();

	@Shadow
	public int cookTime;

	@Shadow
	public abstract void finishCooking();

	/**
	 * @author sschr15
	 * @reason items that shouldn't disappear upon successful smelting
	 */
	@Overwrite
	public void tick() {
		boolean var1 = this.fuelTime > 0;
		boolean dirty = false;
		if (this.fuelTime > 0) {
			--this.fuelTime;
		}

		if (!this.world.isMultiplayer) {
			if (this.fuelTime == 0 && this.canCook()) {
				this.totalFuelTime = this.fuelTime = this.getFuelTime(this.inventory[1]);
				if (this.fuelTime > 0) {
					dirty = true;
					if (this.inventory[1] != null) {
						if (this.inventory[1].getItem().hasRecipeRemainder()) {
							this.inventory[1] = new ItemStack(this.inventory[1].getItem().getRecipeRemainder());
						} else {
							--this.inventory[1].size;
						}

						if (this.inventory[1].size == 0) {
							this.inventory[1] = null;
						}
					}
				}
			}

			if (this.hasFuel() && this.canCook()) {
				++this.cookTime;
				if (this.cookTime == 200) {
					this.cookTime = 0;
					this.finishCooking();
					dirty = true;
				}
			} else {
				this.cookTime = 0;
			}

			if (var1 != this.fuelTime > 0) {
				dirty = true;
				FurnaceBlock.updateLitState(this.fuelTime > 0, this.world, this.x, this.y, this.z);
			}
		}

		if (dirty) {
			this.markDirty();
		}
	}
}
