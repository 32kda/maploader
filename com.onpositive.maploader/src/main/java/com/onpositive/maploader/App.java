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
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.commons.jcs.access.AbstractCacheAccess;
import org.apache.commons.jcs.access.behavior.ICacheAccess;
import org.apache.commons.jcs.engine.control.CompositeCache;
import org.apache.commons.lang.StringUtils;
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

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.osm2xp.classification.TagUtil;
import com.osm2xp.classification.TypeProvider;
import com.osm2xp.classification.model.WayEntity;
import com.osm2xp.classification.output.CSVWriter;
import com.osm2xp.classification.parsing.LearningDataParser;
import com.osm2xp.core.model.osm.Tag;
import com.osm2xp.generation.paths.PathsService;

/**
 * Import pics for buildings and other map features
 *
 */
public class App 
{
	
	private static final int MAX_IMG_SIZE = 768;

	private static final int MIN_BOUNDING_BOX_METERS = 200;
	
	private static ExecutorService service = Executors.newFixedThreadPool(10, new ThreadFactoryBuilder().setDaemon(true).build());
	private static List<ImageryLayer> layers;
	private static List<AirfieldSurface> data = new ArrayList<>();
	private static List<Future<?>> futureList = new ArrayList<Future<?>>();
	
    public static void main( String[] args )
    {
    	File input = new File("f:/tmp/osm/crimean-fed-district-latest.osm.pbf");
//    	Predicate<List<Tag>> predicate = getRunwayPredicate();
    	File outFolder = new File("F:/tmp/imagery_airfield");
//    	new AirfieldSamplesCollector(outFolder).collectSamples("runways", folder, outFolder);
    	File basicFolder = new File("F:/tmp/ds_collection");
		new BuildingTypeSamplesCollector(basicFolder).collectSamples("buildings", input, new File(basicFolder, "building_types"));
//    	collectSamples(folder, outFolder, predicate);
    	
    }

//	protected static void collectSamples(File folder, File outFolder, Predicate<List<Tag>> predicate) {
//		File[] inputs = folder.listFiles(file -> file.isFile() && (file.getName().endsWith(".pbf") || file.getName().endsWith(".osm")));
//    	
//    	PathsService.getPathsProvider().setBasicFolder(outFolder);
//    	Preferences prefs = Preferences.main();
//    	Config.setPreferencesInstance(prefs);
//    	Config.setBaseDirectoriesProvider(JosmBaseDirectories.getInstance());
//    	Config.setUrlsProvider(JosmUrls.getInstance());
//    	prefs.init(false);
//    	ImageryLayerInfo.instance.load(false);
//    	layers = ImageryLayerInfo.instance.getLayers().stream().map(info -> ImageryLayer.create(info)).collect(Collectors.toList());
//    	for (File input : inputs) {
//			collectData(input, predicate, outFolder);
//		}
//    	synchronized (futureList) {
//    		List<Future<?>> curList = futureList;
//    		while (!curList.isEmpty()) {
//    			try {
//					List<Future<?>> addedList = new ArrayList<Future<?>>();
//					for (Future<?> future : curList) {
//						Object result = future.get();
//						if (result instanceof Future<?>) {
//							addedList.add((Future<?>) result);
//						}
//					}
//					curList = addedList;
//				} catch (InterruptedException e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				} catch (ExecutionException e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}
//			}
//    		
//		}
//    	
//    	for (Iterator<AirfieldSurface> iterator = data.iterator(); iterator.hasNext();) {
//			AirfieldSurface airfieldSurface = iterator.next();
//			if (!new File(outFolder, airfieldSurface.id).exists()) {
//				iterator.remove();
//			}
//		}
//    	
//    	try (CSVWriter<AirfieldSurface> csvWriter = new CSVWriter<AirfieldSurface>(new File(outFolder, "runways.csv"), "runways")) {
//    		csvWriter.write(data);
//    	} catch (IOException e1) {
//    		// TODO Auto-generated catch block
//    		e1.printStackTrace();
//    	}
//    	for (ImageryLayer layer : layers) {
//			if (layer instanceof CachedTileLoader) {
//				ICacheAccess<String, BufferedImageCacheEntry> cache = ((CachedTileLoader) layer).getCacheAccess();
//				if (cache instanceof AbstractCacheAccess){
//					AbstractCacheAccess<?, ?> access = (AbstractCacheAccess<?, ?>) cache;
//					CompositeCache<?, ?> cacheControl = access.getCacheControl();
//					if (cacheControl != null) {
//						cacheControl.save();
//					}
//				}
//			}
//		}
//	}

//	protected static void collectData(File inputFile, Predicate<List<Tag>> predicate, File outFolder, IWayToResultConverter converter) {
//		System.out.println("Processing " + inputFile.getAbsolutePath());
//		LearningDataParser parser = new LearningDataParser(inputFile, predicate);
//    	
//    	
//    	List<WayEntity> runWays = parser.getCollectedWays(true);
//    	for (WayEntity wayEntity : runWays) {
//			wayEntity.setBoundingBox(GeomUtils.checkMinSizeAndGrow(wayEntity.getBoundingBox(), MIN_BOUNDING_BOX_METERS, 0.4));
//		}
//    	
////    	File folder = new File(outFolder, getDirName(inputFile));
//    	File folder = new File(outFolder, "images");
//    	folder.mkdirs();
//    	int layerIdx = 0;
//    	for (ImageryLayer layer : layers) {
//			if (layer instanceof AbstractTileSourceLayer && !layer.getInfo().getName().startsWith("OpenStreetMap")) { //XXX ugly hack here to avoid downloading OSM map 
//				AbstractTileSourceLayer<?> sourceLayer = (AbstractTileSourceLayer<?>) layer;
//				int idx = 0;
//				for (WayEntity entity : runWays) {
//					long key = entity.getId() > 0 ? entity.getId() : idx;
//					String fileName = key + "_" + layerIdx + ".png";
//					File outFile = new File(outFolder, fileName );
//					saveImgForWay(outFile, sourceLayer, idx, entity);
////					data.add(new AirfieldSurface(fileName, isHardSurface(TagUtil.getValue("surface", entity.getTags()))));
//					data.add(converter.convert(fileName, entity));
//					idx++;
//				}
//				layerIdx++;
//			}
//			
//		}
//	}
	
	
	private static boolean isHardSurface(String osmSurfaceType) {
		return ("asphalt".equals(osmSurfaceType) || "paved".equals(osmSurfaceType) || osmSurfaceType.startsWith("concrete") || "metal".equals(osmSurfaceType));
	}

	private static String getDirName(File inputFile) {
		String name = inputFile.getName();
		if (name.indexOf('.') > 0) {
			name = name.substring(0, name.indexOf('.'));
		}
		return name;
	}

	protected static void saveImgForWay(File outFile, AbstractTileSourceLayer<?> sourceLayer, int idx,
			WayEntity wayEntity) {
		synchronized (futureList) {
			futureList.add(service.submit(new DownloadWithDownscaleTask(outFile,sourceLayer,wayEntity,service, MAX_IMG_SIZE)));
		}
	}
    
	private static Predicate<List<Tag>> getBuildingPredicate() {
		return (tags) -> TypeProvider.isBuilding(tags);
	}
    
    private static Predicate<List<Tag>> getRunwayPredicate() {
    	return (tags) -> "runway".equalsIgnoreCase(TagUtil.getValue("aeroway", tags)) && StringUtils.stripToEmpty(TagUtil.getValue("surface", tags)).trim().length() > 0;
    }

}
