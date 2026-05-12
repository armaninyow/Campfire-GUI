package com.armaninyow.campfiregui.client;

import com.armaninyow.campfiregui.CampfireGUI;
import com.armaninyow.campfiregui.CampfireGuiScreenTracker;
import com.armaninyow.campfiregui.network.CampfireGuiPacket;
import com.armaninyow.campfiregui.network.CampfireGuiRefreshPacket;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.List;

// 1.21.2_1.21.4
@Environment(EnvType.CLIENT)
public class CampfireManagementScreen extends Screen {

	private static final Identifier CONTAINER_TEXTURE =
		Identifier.of(CampfireGUI.MOD_ID, "textures/gui/campfire_container.png");

	private static final Identifier[] LIT_TEXTURES = {
		Identifier.of(CampfireGUI.MOD_ID, "textures/gui/lit_animation_1.png"),
		Identifier.of(CampfireGUI.MOD_ID, "textures/gui/lit_animation_2.png"),
		Identifier.of(CampfireGUI.MOD_ID, "textures/gui/lit_animation_3.png"),
		Identifier.of(CampfireGUI.MOD_ID, "textures/gui/lit_animation_4.png"),
	};
	private static final Identifier[] SOUL_LIT_TEXTURES = {
		Identifier.of(CampfireGUI.MOD_ID, "textures/gui/soul_lit_animation_1.png"),
		Identifier.of(CampfireGUI.MOD_ID, "textures/gui/soul_lit_animation_2.png"),
		Identifier.of(CampfireGUI.MOD_ID, "textures/gui/soul_lit_animation_3.png"),
		Identifier.of(CampfireGUI.MOD_ID, "textures/gui/soul_lit_animation_4.png"),
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

	private static final int TITLE_Y     = 6;
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

	private final Random random = Random.create();

	public CampfireManagementScreen(boolean isLit, boolean isSoulCampfire, List<CampfireGuiPacket.SlotInfo> slots, BlockPos pos) {
		super(Text.empty());
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
	}

	@Override
	public boolean shouldPause() { return false; }

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (this.client != null && this.client.options.inventoryKey.matchesKey(keyCode, scanCode)) {
			this.close();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
		if (this.client != null && this.client.options.inventoryKey.matchesKey(keyCode, scanCode)) {
			this.close();
			return true;
		}
		return super.keyReleased(keyCode, scanCode, modifiers);
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		context.fill(0, 0, this.width, this.height, 0x80000000);
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
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		long now = System.currentTimeMillis();
		if (now - lastRefreshTime >= REFRESH_INTERVAL_MS) {
			ClientPlayNetworking.send(new CampfireGuiRefreshPacket(pos));
			lastRefreshTime = now;
		}

		if (isLit) {
			for (int i = 0; i < 4; i++) {
				if (slotTicksLeft[i] > 0f) {
					slotTicksLeft[i] = Math.max(0f, slotTicksLeft[i] - delta);
				}
			}
		}

		// super.render() first — calls renderBackground() internally for the overlay,
		// then we draw all GUI content on top (matching BeeGUI pattern)
		super.render(context, mouseX, mouseY, delta);

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

		// 1.21.2–1.21.4: drawTexture(Function<Identifier,RenderLayer>, Identifier, x, y, u, v, width, height, texW, texH)
		context.drawTexture(RenderLayer::getGuiTextured, CONTAINER_TEXTURE,
			guiLeft, guiTop,
			0f, 0f,
			PNG_WIDTH, PNG_HEIGHT,
			PNG_WIDTH, PNG_HEIGHT);

		if (isLit) {
			for (int i = 0; i < 3; i++) {
				int fx = guiLeft + FIRE_X[i];
				int fy = guiTop  + FIRE_Y;
				Identifier[] fireTextures = isSoulCampfire ? SOUL_LIT_TEXTURES : LIT_TEXTURES;
				context.drawTexture(RenderLayer::getGuiTextured, fireTextures[fireFrame[i]],
					fx, fy,
					0f, 0f,
					LIT_TEX_W, LIT_TEX_H,
					LIT_TEX_W, LIT_TEX_H);
			}
		}

		String titleStr = isSoulCampfire ? "Soul Campfire" : "Campfire";
		int titleX = guiLeft + (PNG_WIDTH - this.textRenderer.getWidth(titleStr)) / 2 + 1;
		context.drawText(this.textRenderer, titleStr,
			titleX, guiTop + TITLE_Y,
			TITLE_COLOR, false);

		for (int i = 0; i < 4; i++) {
			int sx = guiLeft + SLOT_X[i];
			int sy = guiTop  + SLOT_Y[i];

			if (i < slots.size()) {
				CampfireGuiPacket.SlotInfo slot = slots.get(i);
				ItemStack stack = slot.toItemStack();

				if (!stack.isEmpty()) {
					context.drawItem(stack, sx, sy);

					float ticksRemaining = (slotTicksLeft[i] >= 0f)
						? slotTicksLeft[i]
						: Math.max(0f, slot.cookingTotalTime() - slot.cookingTime());
					float total    = slot.cookingTotalTime() > 0 ? slot.cookingTotalTime() : 1f;
					float progress = 1f - Math.min(1f, Math.max(0f, ticksRemaining / total));

					int barX = sx + (SLOT_SIZE - BAR_WIDTH) / 2;
					int barY = sy + SLOT_SIZE + BAR_OFFSET_Y;

					context.fill(barX, barY, barX + BAR_WIDTH, barY + 1, 0xFF000000);
					int filledW = Math.round(progress * BAR_WIDTH);
					if (filledW > 0) {
						context.fill(barX, barY, barX + filledW, barY + 1, 0xFFFFFFFF);
					}
					context.fill(barX, barY + 1, barX + BAR_WIDTH, barY + BAR_HEIGHT, 0xFF000000);
				}
			}
		}

		if (hoveredSlot >= 0 && hoveredSlot < slots.size()) {
			CampfireGuiPacket.SlotInfo slot = slots.get(hoveredSlot);
			if (!slot.toItemStack().isEmpty()) {
				renderSlotTooltip(context, hoveredSlot, slot, mouseX, mouseY);
			}
		}
	}

	private void renderSlotTooltip(DrawContext context, int slotIndex, CampfireGuiPacket.SlotInfo slot, int mouseX, int mouseY) {
		List<Text> lines = new ArrayList<>();
		lines.add(slot.toItemStack().getName().copy().styled(s -> s.withColor(TOOLTIP_ITEM_COLOR)));
		float ticksRemaining = (slotTicksLeft[slotIndex] >= 0f)
			? slotTicksLeft[slotIndex]
			: Math.max(0f, slot.cookingTotalTime() - slot.cookingTime());
		int seconds = (int) Math.ceil(ticksRemaining / 20.0f);
		lines.add(Text.literal(seconds + "s remaining").styled(s ->
		    s.withColor(TOOLTIP_TIME_COLOR)));
		context.drawTooltip(this.textRenderer, lines, mouseX, mouseY);
	}
}
