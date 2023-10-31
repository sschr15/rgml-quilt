package org.duvetmc.rgml;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.GameRenderer;

public class EntityRendererProxy extends GameRenderer {
	private final Minecraft game;

	public EntityRendererProxy(Minecraft minecraft) {
		super(minecraft);
		game = minecraft;
	}

	@Override
	public void render(float tickDelta) {
		super.render(tickDelta);
		ModLoader.OnTick(tickDelta, game);
	}
}
