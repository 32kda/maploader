package com.onpositive.maploader;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

import javax.imageio.ImageIO;

import org.openstreetmap.josm.gui.layer.AbstractTileSourceLayer;
import org.openstreetmap.josm.tools.Logging;

import com.osm2xp.classification.model.WayEntity;


public class DownloadTask implements Callable<Object> {
	
	private AbstractTileSourceLayer<?> sourceLayer;
	private WayEntity wayEntity;
	
	private volatile int retryCnt = 5;
	private ExecutorService service;
	private File outFile;

	public DownloadTask(File outFile, AbstractTileSourceLayer<?> sourceLayer, 
			WayEntity wayEntity, ExecutorService service) {
				this.outFile = outFile;
				this.sourceLayer = sourceLayer;
				this.wayEntity = wayEntity;
				this.service = service;
	}

	@Override
	public Object call() throws Exception {
		try {
			BufferedImage img = sourceLayer.paintImageFor(wayEntity.getBoundingBox(), 16,true, 0.4);
			if (img != null) {
				long id = wayEntity.getId();
				ImageIO.write(img,"png", outFile);
				Logging.info("Saved " + outFile.getAbsolutePath());
			} else {
				Logging.error("Failed to load all tiles for way " + wayEntity.getId());
				if (retryCnt > 0) {
					retryCnt--;
					return service.submit(this);
				}
			}
		} catch (Exception e) {
			Logging.error("Exception occured while saving image for " + wayEntity.getId());
			Logging.error(e);
		}
		return null;
	}

}
