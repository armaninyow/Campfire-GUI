package com.armaninyow.campfiregui.client;

import com.armaninyow.campfiregui.CampfireGUI;
import com.armaninyow.campfiregui.CampfireGuiScreenTracker;
import com.armaninyow.campfiregui.network.CampfireGuiPacket;
import com.armaninyow.campfiregui.network.CampfireGuiRefreshPacket;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Environment(EnvType.CLIENT)
public class CampfireManagementScreen extends Screen {

	private static final Identifier CONTAINER_TEXTURE =
		Identifier.fromNamespaceAndPath(CampfireGUI.MOD_ID, "textures/gui/campfire_container.png");

	private static final Identifier[] LIT_TEXTURES = {
		Identifier.fromNamespaceAndPath(CampfireGUI.MOD_ID, "textures/gui/lit_animation_1.png"),
		Identifier.fromNamespaceAndPath(CampfireGUI.MOD_ID, "textures/gui/lit_animation_2.png"),
		Identifier.fromNamespaceAndPath(CampfireGUI.MOD_ID, "textures/gui/lit_animation_3.png"),
		Identifier.fromNamespaceAndPath(CampfireGUI.MOD_ID, "textures/gui/lit_animation_4.png"),
	};
	private static final Identifier[] SOUL_LIT_TEXTURES = {
		Identifier.fromNamespaceAndPath(CampfireGUI.MOD_ID, "textures/gui/soul_lit_animation_1.png"),
		Identifier.fromNamespaceAndPath(CampfireGUI.MOD_ID, "textures/gui/soul_lit_animation_2.png"),
		Identifier.fromNamespaceAndPath(CampfireGUI.MOD_ID, "textures/gui/soul_lit_animation_3.png"),
		Identifier.fromNamespaceAndPath(CampfireGUI.MOD_ID, "textures/gui/soul_lit_animation_4.png"),
	};
	private static final int LIT_TEX_W = 4;
	private static final int LIT_TEX_H = 14;

	private static final int[] FIRE_X = {34, 39, 44};
	private static final int   FIRE_Y = 44;

	private static final int PNG_WIDTH  = 84;
	private static final int PNG_HEIGHT = 93;

	private static final int[] SLOT_X    = {12, 56, 12, 56};
	private static final int[] SLOT_Y    = {21, 21, 65, 65};
	private static final int   SLOT_SIZE = 16;

	private static final int TITLE_Y    = 6;
	private static final int TITLE_COLOR = 0xFF3F3F3F;

	private static final int BAR_WIDTH    = 14;
	private static final int BAR_HEIGHT   = 2;
	private static final int BAR_OFFSET_Y = 1;

	private static final int TOOLTIP_ITEM_COLOR = 0xFFFCFCFC;
	private static final int TOOLTIP_TIME_COLOR = 0xFF545454;

	private static final long REFRESH_INTERVAL_MS = 100;

	private static final int FIRE_TICK_MIN = 3;
	private static final int FIRE_TICK_MAX = 5;

	private final BlockPos pos;
	private boolean isLit;
	private boolean isSoulCampfire;
	private List<CampfireGuiPacket.SlotInfo> slots;

	private final float[] slotTicksLeft = {-1f, -1f, -1f, -1f};

	private int guiLeft;
	private int guiTop;
	private int hoveredSlot = -1;

	private long lastRefreshTime = 0;

	private final int[] fireFrame     = {0, 1, 2};
	private final int[] fireTicksLeft = {0, 0, 0};

	private final RandomSource random = RandomSource.create();

	public CampfireManagementScreen(boolean isLit, boolean isSoulCampfire, List<CampfireGuiPacket.SlotInfo> slots, BlockPos pos) {
		super(Component.empty());
		this.isLit          = isLit;
		this.isSoulCampfire = isSoulCampfire;
		this.slots          = slots;
		this.pos            = pos;
		anchorTimers(slots);
		for (int i = 0; i < 3; i++) {
			fireFrame[i]     = random.nextInt(LIT_TEXTURES.length);
			fireTicksLeft[i] = FIRE_TICK_MIN + random.nextInt(FIRE_TICK_MAX - FIRE_TICK_MIN + 1);
		}
	}

	public void updateData(boolean isLit, boolean isSoulCampfire, List<CampfireGuiPacket.SlotInfo> slots) {
		this.isLit          = isLit;
		this.isSoulCampfire = isSoulCampfire;
		this.slots          = slots;
		anchorTimers(slots);
	}

	private void anchorTimers(List<CampfireGuiPacket.SlotInfo> slots) {
		for (int i = 0; i < 4; i++) {
			if (i < slots.size()) {
				CampfireGuiPacket.SlotInfo slot = slots.get(i);
				if (!slot.itemId().isEmpty() && slot.cookingTotalTime() > 0) {
					slotTicksLeft[i] = Math.max(0f, slot.cookingTotalTime() - slot.cookingTime());
				} else {
					slotTicksLeft[i] = -1f;
				}
			} else {
				slotTicksLeft[i] = -1f;
			}
		}
	}

	public BlockPos getPos() { return pos; }

	@Override
	protected void init() {
		super.init();
		this.guiLeft = (this.width  - PNG_WIDTH)  / 2;
		this.guiTop  = (this.height - PNG_HEIGHT) / 2;
		CampfireGuiScreenTracker.openScreenPositions.add(pos);
	}

	@Override
	public void removed() {
		super.removed();
		CampfireGuiScreenTracker.openScreenPositions.remove(pos);
		CampfireGUIClient.recentlyClosedScreens.put(pos, System.currentTimeMillis());
	}

	@Override
	public boolean isPauseScreen() { return false; }

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (this.minecraft != null && this.minecraft.options.keyInventory.matches(event)) {
			this.onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void tick() {
		super.tick();
		if (!isLit) return;
		int texCount = LIT_TEXTURES.length;
		for (int i = 0; i < 3; i++) {
			fireTicksLeft[i]--;
			if (fireTicksLeft[i] <= 0) {
				int next = random.nextInt(texCount - 1);
				if (next >= fireFrame[i]) next++;
				fireFrame[i]     = next;
				fireTicksLeft[i] = FIRE_TICK_MIN + random.nextInt(FIRE_TICK_MAX - FIRE_TICK_MIN + 1);
			}
		}
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, this.width, this.height, 0x80000000);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		long now = System.currentTimeMillis();
		if (now - lastRefreshTime >= REFRESH_INTERVAL_MS) {
			ClientPlayNetworking.send(new CampfireGuiRefreshPacket(pos));
			lastRefreshTime = now;
		}

		if (isLit) {
			for (int i = 0; i < 4; i++) {
				if (slotTicksLeft[i] > 0f) {
					slotTicksLeft[i] = Math.max(0f, slotTicksLeft[i] - partialTick);
				}
			}
		}

		hoveredSlot = -1;
		for (int i = 0; i < 4; i++) {
			int sx = guiLeft + SLOT_X[i];
			int sy = guiTop  + SLOT_Y[i];
			if (mouseX >= sx && mouseX < sx + SLOT_SIZE
				&& mouseY >= sy && mouseY < sy + SLOT_SIZE) {
				hoveredSlot = i;
				break;
			}
		}

		graphics.blit(RenderPipelines.GUI_TEXTURED, CONTAINER_TEXTURE,
			guiLeft, guiTop,
			0f, 0f,
			PNG_WIDTH, PNG_HEIGHT,
			PNG_WIDTH, PNG_HEIGHT,
			0xFFFFFFFF);

		if (isLit) {
			for (int i = 0; i < 3; i++) {
				int fx = guiLeft + FIRE_X[i];
				int fy = guiTop  + FIRE_Y;
				Identifier[] fireTextures = isSoulCampfire ? SOUL_LIT_TEXTURES : LIT_TEXTURES;
				graphics.blit(RenderPipelines.GUI_TEXTURED, fireTextures[fireFrame[i]],
					fx, fy,
					0f, 0f,
					LIT_TEX_W, LIT_TEX_H,
					LIT_TEX_W, LIT_TEX_H,
					0xFFFFFFFF);
			}
		}

		Component title = Component.literal(isSoulCampfire ? "Soul Campfire" : "Campfire");
		int titleX = guiLeft + (PNG_WIDTH - this.font.width(title)) / 2 + 1;
		graphics.text(this.font, title, titleX, guiTop + TITLE_Y, TITLE_COLOR, false);

		for (int i = 0; i < 4; i++) {
			int sx = guiLeft + SLOT_X[i];
			int sy = guiTop  + SLOT_Y[i];

			if (i < slots.size()) {
				CampfireGuiPacket.SlotInfo slot = slots.get(i);
				ItemStack stack = slot.toItemStack();

				if (!stack.isEmpty()) {
					graphics.item(stack, sx, sy);

					float ticksRemaining = (slotTicksLeft[i] >= 0f)
						? slotTicksLeft[i]
						: Math.max(0f, slot.cookingTotalTime() - slot.cookingTime());
					float total    = slot.cookingTotalTime() > 0 ? slot.cookingTotalTime() : 1f;
					float progress = 1f - Math.min(1f, Math.max(0f, ticksRemaining / total));

					int barX = sx + (SLOT_SIZE - BAR_WIDTH) / 2;
					int barY = sy + SLOT_SIZE + BAR_OFFSET_Y;

					graphics.fill(barX, barY, barX + BAR_WIDTH, barY + 1, 0xFF000000);
					int filledW = Math.round(progress * BAR_WIDTH);
					if (filledW > 0) {
						graphics.fill(barX, barY, barX + filledW, barY + 1, 0xFFFFFFFF);
					}
					graphics.fill(barX, barY + 1, barX + BAR_WIDTH, barY + BAR_HEIGHT, 0xFF000000);
				}
			}
		}

		super.extractRenderState(graphics, mouseX, mouseY, partialTick);

		if (hoveredSlot >= 0 && hoveredSlot < slots.size()) {
			CampfireGuiPacket.SlotInfo slot = slots.get(hoveredSlot);
			if (!slot.toItemStack().isEmpty()) {
				List<Component> lines = new ArrayList<>();
				lines.add(slot.toItemStack().getHoverName().copy()
					.withStyle(s -> s.withColor(TOOLTIP_ITEM_COLOR)));
				float ticksRemaining = (slotTicksLeft[hoveredSlot] >= 0f)
					? slotTicksLeft[hoveredSlot]
					: Math.max(0f, slot.cookingTotalTime() - slot.cookingTime());
				int seconds = (int) Math.ceil(ticksRemaining / 20.0f);
				lines.add(Component.literal(seconds + "s remaining")
					.withStyle(s -> s.withColor(TOOLTIP_TIME_COLOR).withShadowColor(0xFF151515)));
				graphics.setTooltipForNextFrame(this.font, lines, Optional.empty(), mouseX, mouseY);
			}
		}
	}
}