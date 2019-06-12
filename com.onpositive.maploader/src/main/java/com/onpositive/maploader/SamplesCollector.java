package com.onpositive.maploader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.apache.commons.jcs.access.AbstractCacheAccess;
import org.apache.commons.jcs.access.behavior.ICacheAccess;
import org.apache.commons.jcs.engine.control.CompositeCache;
import org.openstreetmap.gui.jmapviewer.interfaces.CachedTileLoader;
import org.openstreetmap.josm.data.cache.BufferedImageCacheEntry;
import org.openstreetmap.josm.data.imagery.ImageryLayerInfo;
import org.openstreetmap.josm.data.preferences.JosmBaseDirectories;
import org.openstreetmap.josm.data.preferences.JosmUrls;
import org.openstreetmap.josm.data.preferences.Preferences;
import org.openstreetmap.josm.gui.layer.AbstractTileSourceLayer;
import org.openstreetmap.josm.gui.layer.ImageryLayer;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.GeomUtils;

import com.bbn.openmap.util.FileUtils;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.osm2xp.classification.model.WayEntity;
import com.osm2xp.classification.output.CSVWriter;
import com.osm2xp.classification.parsing.LearningDataParser;
import com.osm2xp.core.model.osm.Tag;
import com.osm2xp.generation.paths.PathsService;

public abstract class SamplesCollector<T extends IHasId> {
	
	private static final int MAX_IMG_SIZE = 768;

	private ExecutorService service = Executors.newFixedThreadPool(10, new ThreadFactoryBuilder().setDaemon(true).build());
	private List<ImageryLayer> layers;
	private List<Future<?>> futureList = new ArrayList<Future<?>>();

	private double growFactor = 0;

	private int scale;

	/**
	 * Constructs new SamplesCollector
	 * @param basicFolder Folder to store additional info (preferences, caches...) into
	 * @param growFactor - grow factor, image bounds will be taken this percent larger, than original entity bounds. E.g. 0.4 means bounding box will grow 1.4 x
	 */
	
	public SamplesCollector(File basicFolder, double growFactor, int scale) {
		this.growFactor = growFactor;
		this.scale = scale;
		PathsService.getPathsProvider().setBasicFolder(basicFolder);
		Preferences prefs = Preferences.main();
		Config.setPreferencesInstance(prefs);
		Config.setBaseDirectoriesProvider(JosmBaseDirectories.getInstance());
		Config.setUrlsProvider(JosmUrls.getInstance());
		prefs.init(false);
		ImageryLayerInfo.instance.load(false);
		if (ImageryLayerInfo.instance.getLayers().size() == 0) {
			ImageryLayerInfo.instance.loadDefaults(true,null,false);	
		}
		layers = ImageryLayerInfo.instance.getLayers().stream().map(info -> ImageryLayer.create(info)).collect(Collectors.toList());
	}

	/**
	 * Collect samples as image files and create .csv dataset file for them
	 * @param datasetID ID to use for dataset
	 * @param input input .osm/.pbf file or folder containing such files (all matching files from it will be used in such case)
	 * @param outFolder Output folder
	 */
	public void collectSamples(String datasetID, File input, File outFolder) {
		collectSamples(datasetID, input, outFolder, true);
	}
	
	/**
	 * Collect samples as image files and create .csv dataset file for them
	 * @param datasetID ID to use for dataset
	 * @param input input .osm/.pbf file or folder containing such files (all matching files from it will be used in such case)
	 * @param outFolder Output folder
	 * @param clearOutput Clear output folder prior to collection or not. If <code>false</code> - already existing images wouldn't be re-downloaded
	 */
	public void collectSamples(String datasetID, File input, File outFolder, boolean clearOutput) {
		if (clearOutput) {
			try {
				FileUtils.deleteFile(outFolder);
			} catch (IOException e) {
				throw new RuntimeException("Unable to clear target folder", e);
			}
		}
		
		File[] inputs;
		if (input.isFile() && (input.getName().endsWith(".pbf") || input.getName().endsWith(".osm"))) {
			inputs = new File[] {input};
		} else if (input.isDirectory()) {			
			inputs = input.listFiles(file -> file.isFile() && (file.getName().endsWith(".pbf") || file.getName().endsWith(".osm")));
		} else {
			throw new IllegalArgumentException("Input File object should be either .osm.pbf file or directory");
		}
    	
    	List<T> data = new ArrayList<>();
    	for (File curInput : inputs) {
			data.addAll(collectData(curInput, outFolder));
		}

    	synchronized (futureList) {
    		List<Future<?>> curList = futureList;
    		while (!curList.isEmpty()) {
    			try {
					List<Future<?>> addedList = new ArrayList<Future<?>>();
					for (Future<?> future : curList) {
						Object result = future.get();
						if (result instanceof Future<?>) {
							addedList.add((Future<?>) result);
						}
					}
					curList = addedList;
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (ExecutionException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
    		
		}
    	
    	for (Iterator<T> iterator = data.iterator(); iterator.hasNext();) {
			T current = iterator.next();
			if (!new File(outFolder, current.getId()).exists()) {
				iterator.remove();
			}
		}
    	
    	try (CSVWriter<T> csvWriter = new CSVWriter<T>(new File(outFolder, datasetID + ".csv"), datasetID)) {
    		csvWriter.write(data);
    	} catch (IOException e1) {
    		// TODO Auto-generated catch block
    		e1.printStackTrace();
    	}
    	for (ImageryLayer layer : layers) {
			if (layer instanceof CachedTileLoader) {
				ICacheAccess<String, BufferedImageCacheEntry> cache = ((CachedTileLoader) layer).getCacheAccess();
				if (cache instanceof AbstractCacheAccess){
					AbstractCacheAccess<?, ?> access = (AbstractCacheAccess<?, ?>) cache;
					CompositeCache<?, ?> cacheControl = access.getCacheControl();
					if (cacheControl != null) {
						cacheControl.save();
					}
				}
			}
		}
	}

	protected List<T> collectData(File inputFile, File outFolder) {
		System.out.println("Processing " + inputFile.getAbsolutePath());
		LearningDataParser parser = new LearningDataParser(inputFile, list -> isGoodSample(list));
		List<T> data = new ArrayList<>();    	
    	
    	List<WayEntity> ways = parser.getCollectedWays(true);
    	for (WayEntity wayEntity : ways) {
			wayEntity.setBoundingBox(GeomUtils.checkMinSizeAndGrow(wayEntity.getBoundingBox(), getMinBoundingBoxMeters(), growFactor));
		}
    	
//    	File folder = new File(outFolder, getDirName(inputFile));
    	outFolder.mkdirs();
    	int layerIdx = 0;
    	for (ImageryLayer layer : layers) {
			if (layer instanceof AbstractTileSourceLayer && !layer.getInfo().getName().startsWith("OpenStreetMap")) { //XXX ugly hack here to avoid downloading OSM map 
				AbstractTileSourceLayer<?> sourceLayer = (AbstractTileSourceLayer<?>) layer;
				int idx = 0;
				for (WayEntity entity : ways) {
					long key = entity.getId() > 0 ? entity.getId() : idx;
					String fileName = key + "_" + layerIdx + ".png";
					File outFile = new File(outFolder, fileName);
					if (!outFile.exists()) {
						saveImgForWay(outFile, sourceLayer, idx, entity);
					}
//					data.add(new AirfieldSurface(fileName, isHardSurface(TagUtil.getValue("surface", entity.getTags()))));
					data.add(convert(fileName, entity));
					idx++;
				}
				layerIdx++;
			}
			
		}
    	return data;
	}
	
	protected abstract int getMinBoundingBoxMeters();

	protected abstract T convert(String fileName, WayEntity entity);
	
	protected abstract boolean isGoodSample(List<Tag> tags);

	protected void saveImgForWay(File outFile, AbstractTileSourceLayer<?> sourceLayer, int idx,
			WayEntity wayEntity) {
		synchronized (futureList) {
			futureList.add(service.submit(new DownloadWithDownscaleTask(outFile,sourceLayer,wayEntity,service, growFactor, scale, MAX_IMG_SIZE)));
		}
	}
	
}
