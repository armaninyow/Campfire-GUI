package com.armaninyow.campfiregui.mixin;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CampfireBlockEntity.class)
public interface CampfireBlockEntityAccessor {

	@Accessor("cookingProgress")
	int[] campfiregui$getCookingTimes();

	@Accessor("cookingTime")
	int[] campfiregui$getCookingTotalTimes();

	@Accessor("items")
	NonNullList<ItemStack> campfiregui$getItems();
}