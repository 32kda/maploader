package com.onpositive.maploader;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;

import org.openstreetmap.josm.gui.layer.AbstractTileSourceLayer;

import com.osm2xp.classification.model.WayEntity;

public class DownloadWithDownscaleTask extends DownloadTask {

	private int maxSize;

	public DownloadWithDownscaleTask(File outFile, AbstractTileSourceLayer<?> sourceLayer, WayEntity wayEntity,
			ExecutorService service, int maxSize) {
		super(outFile, sourceLayer, wayEntity, service);
		this.maxSize = maxSize;
	}
	
	@Override
	protected void saveImage(BufferedImage img) throws IOException {
		int width = img.getWidth();
		int height = img.getHeight();
		int imgMaxSize = Math.max(width, height);
		if (imgMaxSize <= maxSize) {
			super.saveImage(img);
		} else {
			double ratio = imgMaxSize *  1.0 / maxSize;
			int newWidth = (int) (width >= height ? maxSize : Math.round(width / ratio));
			int newHeight = (int) (width >= height ? Math.round(height / ratio): maxSize);
			BufferedImage resized = new BufferedImage(newWidth, newHeight, img.getType());
			Graphics2D g = resized.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
			    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.drawImage(img, 0, 0, newWidth, newHeight, 0, 0, img.getWidth(),
			    img.getHeight(), null);
			g.dispose();
			super.saveImage(resized);
		}
	}

}
