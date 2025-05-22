package org.duvetmc.rgml;

import net.minecraft.client.render.texture.TextureAtlas;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.awt.image.BufferedImage;

public class ModTextureAnimation extends TextureAtlas {
	private final int tickRate;
	private final byte[][] images;
	private int index = 0;
	private int ticks = 0;

	public ModTextureAnimation(int slot, int dst, BufferedImage source, int rate) {
		this(slot, 1, dst, source, rate);
	}

	public ModTextureAnimation(int slot, int size, int dst, BufferedImage source, int rate) {
		super(slot);
		this.resolution = size;
		this.type = dst;
		this.tickRate = rate;
		this.ticks = rate;
		this.bind(ModLoader.getMinecraftInstance().textureManager);
		int targetWidth = GL11.glGetTexLevelParameteri(3553, 0, 4096) / 16;
		int targetHeight = GL11.glGetTexLevelParameteri(3553, 0, 4097) / 16;
		int width = source.getWidth();
		int height = source.getHeight();
		int images = (int)Math.floor((double)(height / width));
		if (images <= 0) {
			throw new IllegalArgumentException("source has no complete images");
		} else {
			this.images = new byte[images][];
			if (width != targetWidth) {
				BufferedImage img = new BufferedImage(targetWidth, targetHeight * images, 6);
				Graphics2D gfx = img.createGraphics();
				gfx.drawImage(source, 0, 0, targetWidth, targetHeight * images, 0, 0, width, height, null);
				gfx.dispose();
				source = img;
			}

			for(int i = 0; i < images; ++i) {
				int[] temp = new int[targetWidth * targetHeight];
				source.getRGB(0, targetHeight * i, targetWidth, targetHeight, temp, 0, targetWidth);
				this.images[i] = new byte[targetWidth * targetHeight * 4];

				for(int j = 0; j < temp.length; ++j) {
					int a = temp[j] >> 24 & 0xFF;
					int r = temp[j] >> 16 & 0xFF;
					int g = temp[j] >> 8 & 0xFF;
					int b = temp[j] >> 0 & 0xFF;
					this.images[i][j * 4 + 0] = (byte)r;
					this.images[i][j * 4 + 1] = (byte)g;
					this.images[i][j * 4 + 2] = (byte)b;
					this.images[i][j * 4 + 3] = (byte)a;
				}
			}
		}
	}

	public void method_256() {
		if (this.ticks >= this.tickRate) {
			++this.index;
			if (this.index >= this.images.length) {
				this.index = 0;
			}

			this.buffer = this.images[this.index];
			this.ticks = 0;
		}

		++this.ticks;
	}
}
