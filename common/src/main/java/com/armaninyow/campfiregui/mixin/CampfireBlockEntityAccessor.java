package com.armaninyow.campfiregui.mixin;

import net.minecraft.block.entity.CampfireBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CampfireBlockEntity.class)
public interface CampfireBlockEntityAccessor {

	@Accessor("cookingTimes")
	int[] campfiregui$getCookingTimes();

	@Accessor("cookingTotalTimes")
	int[] campfiregui$getCookingTotalTimes();
}