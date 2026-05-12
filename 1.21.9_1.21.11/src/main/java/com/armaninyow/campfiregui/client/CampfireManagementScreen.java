package com.armaninyow.campfiregui.client;

import com.armaninyow.campfiregui.CampfireGUI;
import com.armaninyow.campfiregui.CampfireGuiScreenTracker;
import com.armaninyow.campfiregui.network.CampfireGuiPacket;
import com.armaninyow.campfiregui.network.CampfireGuiRefreshPacket;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.List;

// 1.21.5_1.21.11
@Environment(EnvType.CLIENT)
public class CampfireManagementScreen extends Screen {

	// Container texture
	private static final Identifier CONTAINER_TEXTURE =
		Identifier.of(CampfireGUI.MOD_ID, "textures/gui/campfire_container.png");

	// Fire animation textures (3x13 each)
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

	// Fire animation columns (relative to container top-left, PNG pixels)
	// x=34..36, x=39..41, x=44..46  (each 3 px wide)
	private static final int[] FIRE_X = {34, 39, 44};
	private static final int   FIRE_Y = 44;

	// Container PNG dimensions
	private static final int PNG_WIDTH  = 84;
	private static final int PNG_HEIGHT = 93;

	// Slot positions (top-left corner of each slot, relative to container top-left)
	// Slots: (12,21), (56,21), (12,65), (56,65)  — all 16×16
	private static final int[] SLOT_X    = {12, 56, 12, 56};
	private static final int[] SLOT_Y    = {21, 21, 65, 65};
	private static final int   SLOT_SIZE = 16;

	// Title
	private static final int TITLE_Y    = 6;  // original 7, moved 1px up
	private static final int TITLE_COLOR = 0xFF3F3F3F;

	// Progress bar (drawn 1px below each slot, horizontally centered)
	private static final int BAR_WIDTH  = 14;
	private static final int BAR_HEIGHT = 2; // row 0 = progress, row 1 = shadow
	private static final int BAR_OFFSET_Y = 1; // gap between slot bottom and bar top

	// Tooltip colors
	private static final int TOOLTIP_ITEM_COLOR = 0xFFFCFCFC;
	private static final int TOOLTIP_TIME_COLOR = 0xFF545454;

	// Server refresh interval — kept short so item disappearance is detected quickly.
	// The client-side timers handle smooth interpolation between refreshes.
	private static final long REFRESH_INTERVAL_MS = 100;

	// Fire animation: re-roll frame every 3–5 ticks (at 20tps ≈ 150–250ms)
	private static final int FIRE_TICK_MIN = 3;
	private static final int FIRE_TICK_MAX = 5;

	private final BlockPos pos;
	private boolean isLit;
	private boolean isSoulCampfire;
	private List<CampfireGuiPacket.SlotInfo> slots;

	/**
	 * Per-slot client-side countdown in ticks (floats so they can be
	 * decremented by the real frame delta rather than a fixed 1-tick step).
	 * Anchored to server data on each refresh, then ticked down independently
	 * every render frame via the partial-tick delta.
	 *
	 * Value of -1 means "not yet initialised" — fall back to server data.
	 */
	private final float[] slotTicksLeft = {-1f, -1f, -1f, -1f};

	private int guiLeft;
	private int guiTop;
	private int hoveredSlot = -1;

	private long lastRefreshTime = 0;

	// Per-flame animation state: current texture index and ticks until next change
	private final int[] fireFrame      = {0, 1, 2};
	private final int[] fireTicksLeft  = {0, 0, 0};

	private final Random random = Random.create();

	public CampfireManagementScreen(boolean isLit, boolean isSoulCampfire, List<CampfireGuiPacket.SlotInfo> slots, BlockPos pos) {
		super(Text.empty());
		this.isLit          = isLit;
		this.isSoulCampfire = isSoulCampfire;
		this.slots          = slots;
		this.pos            = pos;
		// Anchor client timers to the initial server data
		anchorTimers(slots);
		// Initialise fire animation timers to random starting offsets
		for (int i = 0; i < 3; i++) {
			fireFrame[i]     = random.nextInt(LIT_TEXTURES.length);
			fireTicksLeft[i] = FIRE_TICK_MIN + random.nextInt(FIRE_TICK_MAX - FIRE_TICK_MIN + 1);
		}
	}

	/**
	 * Called by CampfireGUIClient when a refresh payload arrives for this pos.
	 * Re-anchors each slot's client timer to the freshly-received server value.
	 */
	public void updateData(boolean isLit, boolean isSoulCampfire, List<CampfireGuiPacket.SlotInfo> slots) {
		this.isLit          = isLit;
		this.isSoulCampfire = isSoulCampfire;
		this.slots          = slots;
		anchorTimers(slots);
	}

	/**
	 * Snaps each slot's local countdown to the server-reported remaining ticks.
	 * After this the client ticks them down independently each frame.
	 */
	private void anchorTimers(List<CampfireGuiPacket.SlotInfo> slots) {
		for (int i = 0; i < 4; i++) {
			if (i < slots.size()) {
				CampfireGuiPacket.SlotInfo slot = slots.get(i);
				if (!slot.itemId().isEmpty() && slot.cookingTotalTime() > 0) {
					slotTicksLeft[i] = Math.max(0f, slot.cookingTotalTime() - slot.cookingTime());
				} else {
					slotTicksLeft[i] = -1f; // empty slot
				}
			} else {
				slotTicksLeft[i] = -1f;
			}
		}
	}

	/** Exposed so CampfireGUIClient can compare which campfire this screen is for. */
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
	public boolean keyPressed(KeyInput input) {
		if (this.client != null && this.client.options.inventoryKey.matchesKey(input)) {
			this.close();
			return true;
		}
		return super.keyPressed(input);
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		context.fill(0, 0, this.width, this.height, 0x80000000);
	}

	@Override
	public void tick() {
		super.tick();
		if (!isLit) return;
		int texCount = LIT_TEXTURES.length; // same count for soul variant
		for (int i = 0; i < 3; i++) {
			fireTicksLeft[i]--;
			if (fireTicksLeft[i] <= 0) {
				// Pick a random frame that is different from the current one
				int next = random.nextInt(texCount - 1);
				if (next >= fireFrame[i]) next++;
				fireFrame[i]     = next;
				fireTicksLeft[i] = FIRE_TICK_MIN + random.nextInt(FIRE_TICK_MAX - FIRE_TICK_MIN + 1);
			}
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		// ── Server refresh (re-anchors timers) ──────────────────────────────
		long now = System.currentTimeMillis();
		if (now - lastRefreshTime >= REFRESH_INTERVAL_MS) {
			ClientPlayNetworking.send(new CampfireGuiRefreshPacket(pos));
			lastRefreshTime = now;
		}

		// ── Tick client-side timers forward by the real frame delta ──────────
		// delta is the partial tick (0..1) from the last full game tick.
		// We tick by 1 full game-tick equivalent per tick() call, but here we
		// use the render delta to smoothly interpolate between ticks, giving us
		// sub-tick precision every frame.
		if (isLit) {
			for (int i = 0; i < 4; i++) {
				if (slotTicksLeft[i] > 0f) {
					slotTicksLeft[i] = Math.max(0f, slotTicksLeft[i] - delta);
				}
			}
		}

		this.renderBackground(context, mouseX, mouseY, delta);

		// ── Hover detection ─────────────────────────────────────────────────
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

		RenderPipeline pipeline = RenderPipelines.GUI_TEXTURED;

		// ── Container background ─────────────────────────────────────────────
		context.drawTexture(pipeline, CONTAINER_TEXTURE,
			guiLeft, guiTop,
			0f, 0f,
			PNG_WIDTH, PNG_HEIGHT,
			PNG_WIDTH, PNG_HEIGHT);

		// ── Fire animations (only when lit) ──────────────────────────────────
		if (isLit) {
			for (int i = 0; i < 3; i++) {
				int fx = guiLeft + FIRE_X[i];
				int fy = guiTop  + FIRE_Y;
				Identifier[] fireTextures = isSoulCampfire ? SOUL_LIT_TEXTURES : LIT_TEXTURES;
				context.drawTexture(pipeline, fireTextures[fireFrame[i]],
					fx, fy,
					0f, 0f,
					LIT_TEX_W, LIT_TEX_H,
					LIT_TEX_W, LIT_TEX_H);
			}
		}

		// ── Title ────────────────────────────────────────────────────────────
		String titleStr = isSoulCampfire ? "Soul Campfire" : "Campfire";
		int titleX = guiLeft + (PNG_WIDTH - this.textRenderer.getWidth(titleStr)) / 2 + 1;
		context.drawText(this.textRenderer, titleStr,
			titleX, guiTop + TITLE_Y,
			TITLE_COLOR, false);

		// ── Items and timers ─────────────────────────────────────────────────
		for (int i = 0; i < 4; i++) {
			int sx = guiLeft + SLOT_X[i];
			int sy = guiTop  + SLOT_Y[i];

			if (i < slots.size()) {
				CampfireGuiPacket.SlotInfo slot = slots.get(i);
				ItemStack stack = slot.toItemStack();

				if (!stack.isEmpty()) {
					context.drawItem(stack, sx, sy);

					// Use the independent client-side timer for display.
					// Fall back to server data if the timer hasn't been anchored yet.
					float ticksRemaining = (slotTicksLeft[i] >= 0f)
						? slotTicksLeft[i]
						: Math.max(0f, slot.cookingTotalTime() - slot.cookingTime());
					float total = slot.cookingTotalTime() > 0 ? slot.cookingTotalTime() : 1f;
					float progress = 1f - Math.min(1f, Math.max(0f, ticksRemaining / total));

					// Bar is centered horizontally on the slot, 1px below slot bottom
					int barX = sx + (SLOT_SIZE - BAR_WIDTH) / 2;
					int barY = sy + SLOT_SIZE + BAR_OFFSET_Y;

					// Row 0: black background then white fill
					context.fill(barX, barY, barX + BAR_WIDTH, barY + 1, 0xFF000000);
					int filledW = Math.round(progress * BAR_WIDTH);
					if (filledW > 0) {
						context.fill(barX, barY, barX + filledW, barY + 1, 0xFFFFFFFF);
					}
					// Row 1: shadow (always black)
					context.fill(barX, barY + 1, barX + BAR_WIDTH, barY + BAR_HEIGHT, 0xFF000000);
				}
			}
		}

		super.render(context, mouseX, mouseY, delta);

		// ── Tooltip ───────────────────────────────────────────────────────────
		if (hoveredSlot >= 0 && hoveredSlot < slots.size()) {
			CampfireGuiPacket.SlotInfo slot = slots.get(hoveredSlot);
			if (!slot.toItemStack().isEmpty()) {
				renderSlotTooltip(context, hoveredSlot, slot, mouseX, mouseY);
			}
		}
	}

	private void renderSlotTooltip(DrawContext context, int slotIndex, CampfireGuiPacket.SlotInfo slot, int mouseX, int mouseY) {
		List<Text> lines = new ArrayList<>();
		// Line 1: item name
		lines.add(slot.toItemStack().getName().copy().styled(s -> s.withColor(TOOLTIP_ITEM_COLOR)));
		// Line 2: time remaining — use the live client-side timer
		float ticksRemaining = (slotTicksLeft[slotIndex] >= 0f)
			? slotTicksLeft[slotIndex]
			: Math.max(0f, slot.cookingTotalTime() - slot.cookingTime());
		int seconds = (int) Math.ceil(ticksRemaining / 20.0f);
		lines.add(Text.literal(seconds + "s remaining").styled(s ->
			s.withColor(TOOLTIP_TIME_COLOR).withShadowColor(0xFF151515)));
		context.drawTooltip(this.textRenderer, lines, mouseX, mouseY);
	}
}