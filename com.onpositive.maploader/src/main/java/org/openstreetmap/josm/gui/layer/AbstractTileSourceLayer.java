// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.layer;


import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Toolkit;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.swing.AbstractAction;

import org.openstreetmap.gui.jmapviewer.AttributionSupport;
import org.openstreetmap.gui.jmapviewer.Coordinate;
import org.openstreetmap.gui.jmapviewer.MemoryTileCache;
import org.openstreetmap.gui.jmapviewer.OsmTileLoader;
import org.openstreetmap.gui.jmapviewer.Tile;
import org.openstreetmap.gui.jmapviewer.TileRange;
import org.openstreetmap.gui.jmapviewer.TileXY;
import org.openstreetmap.gui.jmapviewer.interfaces.CachedTileLoader;
import org.openstreetmap.gui.jmapviewer.interfaces.ICoordinate;
import org.openstreetmap.gui.jmapviewer.interfaces.TemplatedTileSource;
import org.openstreetmap.gui.jmapviewer.interfaces.TileCache;
import org.openstreetmap.gui.jmapviewer.interfaces.TileJob;
import org.openstreetmap.gui.jmapviewer.interfaces.TileLoader;
import org.openstreetmap.gui.jmapviewer.interfaces.TileLoaderListener;
import org.openstreetmap.gui.jmapviewer.interfaces.TileSource;
import org.openstreetmap.gui.jmapviewer.tilesources.AbstractTMSTileSource;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.imagery.CoordinateConversion;
import org.openstreetmap.josm.data.imagery.ImageryInfo;
import org.openstreetmap.josm.data.imagery.TMSCachedTileLoader;
import org.openstreetmap.josm.data.imagery.TileAnchor;
import org.openstreetmap.josm.data.imagery.TileLoaderFactory;
import org.openstreetmap.josm.data.preferences.IntegerProperty;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.tools.GeomUtils;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Utils;

import math.geom2d.Box2D;

/**
 * Base abstract class that supports displaying images provided by TileSource. It might be TMS source, WMS or WMTS
 * It implements all standard functions of tilesource based layers: autozoom, tile reloads, layer saving, loading,etc.
 *
 * @author Upliner
 * @author Wiktor Niesiobędzki
 * @param <T> Tile Source class used for this layer
 * @since 3715
 * @since 8526 (copied from TMSLayer)
 */
public abstract class AbstractTileSourceLayer<T extends AbstractTMSTileSource> extends ImageryLayer
implements ImageObserver, TileLoaderListener {
    private static final String PREFERENCE_PREFIX = "imagery.generic";

    /** maximum zoom level supported */
    public static final int MAX_ZOOM = 30;
    /** minium zoom level supported */
    public static final int MIN_ZOOM = 2;
    private static final Font InfoFont = new Font("sansserif", Font.BOLD, 13);

    /** additional layer menu actions */

    /** minimum zoom level to show to user */
    public static final IntegerProperty PROP_MIN_ZOOM_LVL = new IntegerProperty(PREFERENCE_PREFIX + ".min_zoom_lvl", 2);
    /** maximum zoom level to show to user */
    public static final IntegerProperty PROP_MAX_ZOOM_LVL = new IntegerProperty(PREFERENCE_PREFIX + ".max_zoom_lvl", 20);
    
    //public static final BooleanProperty PROP_DRAW_DEBUG = new BooleanProperty(PREFERENCE_PREFIX + ".draw_debug", false);
    /** Zoomlevel at which tiles is currently downloaded. Initial zoom lvl is set to bestZoom */
    private int currentZoomLevel;

    private final AttributionSupport attribution = new AttributionSupport();

    /**
     * Offset between calculated zoom level and zoom level used to download and show tiles. Negative values will result in
     * lower resolution of imagery useful in "retina" displays, positive values will result in higher resolution
     */
    public static final IntegerProperty ZOOM_OFFSET = new IntegerProperty(PREFERENCE_PREFIX + ".zoom_offset", 0);

    /*
     *  use MemoryTileCache instead of tileLoader JCS cache, as tileLoader caches only content (byte[] of image)
     *  and MemoryTileCache caches whole Tile. This gives huge performance improvement when a lot of tiles are visible
     *  in MapView (for example - when limiting min zoom in imagery)
     *
     *  Use per-layer tileCache instance, as the more layers there are, the more tiles needs to be cached
     */
    protected TileCache tileCache; // initialized together with tileSource
    protected T tileSource;
    protected TileLoader tileLoader;

    private final long minimumTileExpire;

    /**
     * Creates Tile Source based Imagery Layer based on Imagery Info
     * @param info imagery info
     */
    public AbstractTileSourceLayer(ImageryInfo info) {
        super(info);
        this.minimumTileExpire = info.getMinimumTileExpire();
        initializeIfRequired();
    }


    protected abstract TileLoaderFactory getTileLoaderFactory();

    /**
     * Get projections this imagery layer supports natively.
     *
     * For example projection of tiles that are downloaded from a server. Layer
     * may support even more projections (by reprojecting the tiles), but with a
     * certain loss in image quality and performance.
     * @return projections this imagery layer supports natively; null if layer is projection agnostic.
     */
    public abstract Collection<String> getNativeProjections();

    /**
     * Creates and returns a new {@link TileSource} instance depending on {@link #info} specified in the constructor.
     *
     * @return TileSource for specified ImageryInfo
     * @throws IllegalArgumentException when Imagery is not supported by layer
     */
    public abstract T getTileSource();

    protected Map<String, String> getHeaders(T tileSource) {
        if (tileSource instanceof TemplatedTileSource) {
            return ((TemplatedTileSource) tileSource).getHeaders();
        }
        return null;
    }

    protected void initTileSource(T tileSource) {
        attribution.initialize(tileSource);

        currentZoomLevel = getMaxZoomLvl();

        Map<String, String> headers = getHeaders(tileSource);

        tileLoader = getTileLoaderFactory().makeTileLoader(this, headers, minimumTileExpire);

        try {
            if ("file".equalsIgnoreCase(new URL(tileSource.getBaseUrl()).getProtocol())) {
                tileLoader = new OsmTileLoader(this);
            }
        } catch (MalformedURLException e) {
            // ignore, assume that this is not a file
            Logging.log(Logging.LEVEL_DEBUG, e);
        }

        if (tileLoader == null)
            tileLoader = new OsmTileLoader(this, headers);

        tileCache = new MemoryTileCache(1000); ///XXX debug
    }

    @Override
    public synchronized void tileLoadingFinished(Tile tile, boolean success) {
        if (tile.hasError()) {
            success = false;
            tile.setImage(null);
        }
        Logging.debug("tileLoadingFinished() tile: {0} success: {1}", tile, success);
    }

    /**
     * Clears the tile cache.
     */
    public void clearTileCache() {
        if (tileLoader instanceof CachedTileLoader) {
            ((CachedTileLoader) tileLoader).clearCache(tileSource);
        }
        tileCache.clear();
    }

    /**
     * Returns average number of screen pixels per tile pixel for current mapview
     * @param zoom zoom level
     * @return average number of screen pixels per tile pixel
     */
    public double getScaleFactor(int zoom) {
            return 1;
    }

    /**
     * Returns best zoom level.
     * @return best zoom level
     */
    public int getBestZoom() {
        double factor = getScaleFactor(1); // check the ratio between area of tilesize at zoom 1 to current view
        double result = Math.log(factor)/Math.log(2)/2;
        /*
         * Math.log(factor)/Math.log(2) - gives log base 2 of factor
         * We divide result by 2, as factor contains ratio between areas. We could do Math.sqrt before log, or just divide log by 2
         *
         * ZOOM_OFFSET controls, whether we work with overzoomed or underzoomed tiles. Positive ZOOM_OFFSET
         * is for working with underzoomed tiles (higher quality when working with aerial imagery), negative ZOOM_OFFSET
         * is for working with overzoomed tiles (big, pixelated), which is good when working with high-dpi screens and/or
         * maps as a imagery layer
         */
        int intResult = (int) Math.round(result + 1 + ZOOM_OFFSET.get() / 1.9);
        int minZoom = getMinZoomLvl();
        int maxZoom = getMaxZoomLvl();
        if (minZoom <= maxZoom) {
            intResult = Utils.clamp(intResult, minZoom, maxZoom);
        } else if (intResult > maxZoom) {
            intResult = maxZoom;
        }
        return intResult;
    }

    /**
     * Default implementation of {@link org.openstreetmap.josm.gui.layer.Layer.LayerAction#supportLayers(List)}.
     * @param layers layers
     * @return {@code true} is layers contains only a {@code TMSLayer}
     */
    public static boolean actionSupportLayers(List<ImageryLayer> layers) {
        return layers.size() == 1 && layers.get(0) instanceof TMSLayer;
    }

    private abstract static class AbstractTileAction extends AbstractAction {

        protected final AbstractTileSourceLayer<?> layer;
        protected final Tile tile;

        AbstractTileAction(String name, AbstractTileSourceLayer<?> layer, Tile tile) {
            super(name);
            this.layer = layer;
            this.tile = tile;
        }
    }

    private void initializeIfRequired() {
        if (tileSource == null) {
            tileSource = getTileSource();
            if (tileSource == null) {
                throw new IllegalArgumentException("Failed to create tile source");
            }
            // check if projection is supported
            initTileSource(this.tileSource);
        }
    }

    /**
     * Checks zoom level against settings
     * @param maxZoomLvl zoom level to check
     * @param ts tile source to crosscheck with
     * @return maximum zoom level, not higher than supported by tilesource nor set by the user
     */
    public static int checkMaxZoomLvl(int maxZoomLvl, TileSource ts) {
        if (maxZoomLvl > MAX_ZOOM) {
            maxZoomLvl = MAX_ZOOM;
        }
        if (maxZoomLvl < PROP_MIN_ZOOM_LVL.get()) {
            maxZoomLvl = PROP_MIN_ZOOM_LVL.get();
        }
        if (ts != null && ts.getMaxZoom() != 0 && ts.getMaxZoom() < maxZoomLvl) {
            maxZoomLvl = ts.getMaxZoom();
        }
        return maxZoomLvl;
    }

    /**
     * Checks zoom level against settings
     * @param minZoomLvl zoom level to check
     * @param ts tile source to crosscheck with
     * @return minimum zoom level, not higher than supported by tilesource nor set by the user
     */
    public static int checkMinZoomLvl(int minZoomLvl, TileSource ts) {
        if (minZoomLvl < MIN_ZOOM) {
            minZoomLvl = MIN_ZOOM;
        }
        if (minZoomLvl > PROP_MAX_ZOOM_LVL.get()) {
            minZoomLvl = getMaxZoomLvl(ts);
        }
        if (ts != null && ts.getMinZoom() > minZoomLvl) {
            minZoomLvl = ts.getMinZoom();
        }
        return minZoomLvl;
    }

    /**
     * @param ts TileSource for which we want to know maximum zoom level
     * @return maximum max zoom level, that will be shown on layer
     */
    public static int getMaxZoomLvl(TileSource ts) {
        return checkMaxZoomLvl(PROP_MAX_ZOOM_LVL.get(), ts);
    }

    /**
     * @param ts TileSource for which we want to know minimum zoom level
     * @return minimum zoom level, that will be shown on layer
     */
    public static int getMinZoomLvl(TileSource ts) {
        return checkMinZoomLvl(PROP_MIN_ZOOM_LVL.get(), ts);
    }

    /**
     * Sets maximum zoom level, that layer will attempt show
     * @param maxZoomLvl maximum zoom level
     */
    public static void setMaxZoomLvl(int maxZoomLvl) {
        PROP_MAX_ZOOM_LVL.put(checkMaxZoomLvl(maxZoomLvl, null));
    }

    /**
     * Sets minimum zoom level, that layer will attempt show
     * @param minZoomLvl minimum zoom level
     */
    public static void setMinZoomLvl(int minZoomLvl) {
        PROP_MIN_ZOOM_LVL.put(checkMinZoomLvl(minZoomLvl, null));
    }

    /**
     * This fires every time the user changes the zoom, but also (due to ZoomChangeListener) - on all
     * changes to visible map (panning/zooming)
     */
   
    protected int getMaxZoomLvl() {
        if (info.getMaxZoom() != 0)
            return checkMaxZoomLvl(info.getMaxZoom(), tileSource);
        else
            return getMaxZoomLvl(tileSource);
    }

    protected int getMinZoomLvl() {
        if (info.getMinZoom() != 0)
            return checkMinZoomLvl(info.getMinZoom(), tileSource);
        else
            return getMinZoomLvl(tileSource);
    }

    /**
     *
     * @return if its allowed to zoom in
     */
    public boolean zoomIncreaseAllowed() {
        boolean zia = currentZoomLevel < this.getMaxZoomLvl();
        Logging.debug("zoomIncreaseAllowed(): {0} {1} vs. {2}", zia, currentZoomLevel, this.getMaxZoomLvl());
        return zia;
    }

    /**
     * Zoom in, go closer to map.
     *
     * @return    true, if zoom increasing was successful, false otherwise
     */
    public boolean increaseZoomLevel() {
        if (zoomIncreaseAllowed()) {
            currentZoomLevel++;
            Logging.debug("increasing zoom level to: {0}", currentZoomLevel);
        } else {
            Logging.warn("Current zoom level ("+currentZoomLevel+") could not be increased. "+
                    "Max.zZoom Level "+this.getMaxZoomLvl()+" reached.");
            return false;
        }
        return true;
    }

    /**
     * Get the current zoom level of the layer
     * @return the current zoom level
     * @since 12603
     */
    public int getZoomLevel() {
        return currentZoomLevel;
    }

    /**
     * Sets the zoom level of the layer
     * @param zoom zoom level
     * @return true, when zoom has changed to desired value, false if it was outside supported zoom levels
     */
    public boolean setZoomLevel(int zoom) {
        return setZoomLevel(zoom, true);
    }

    private boolean setZoomLevel(int zoom, boolean invalidate) {
        if (zoom == currentZoomLevel) return true;
        if (zoom > this.getMaxZoomLvl()) return false;
        if (zoom < this.getMinZoomLvl()) return false;
        currentZoomLevel = zoom;
        return true;
    }

    /**
     * Check if zooming out is allowed
     *
     * @return    true, if zooming out is allowed (currentZoomLevel &gt; minZoomLevel)
     */
    public boolean zoomDecreaseAllowed() {
        boolean zda = currentZoomLevel > this.getMinZoomLvl();
        Logging.debug("zoomDecreaseAllowed(): {0} {1} vs. {2}", zda, currentZoomLevel, this.getMinZoomLvl());
        return zda;
    }

    /**
     * Zoom out from map.
     *
     * @return    true, if zoom increasing was successful, false othervise
     */
    public boolean decreaseZoomLevel() {
        if (zoomDecreaseAllowed()) {
            Logging.debug("decreasing zoom level to: {0}", currentZoomLevel);
            currentZoomLevel--;
        } else {
            return false;
        }
        return true;
    }

    private Tile getOrCreateTile(TilePosition tilePosition) {
        return getOrCreateTile(tilePosition.getX(), tilePosition.getY(), tilePosition.getZoom());
    }

    private Tile getOrCreateTile(int x, int y, int zoom) {
        Tile tile = getTile(x, y, zoom);
        if (tile == null) {
        	tile = new Tile(tileSource, x, y, zoom);
            tileCache.addTile(tile);
        }
        return tile;
    }

    private Tile getTile(TilePosition tilePosition) {
        return getTile(tilePosition.getX(), tilePosition.getY(), tilePosition.getZoom());
    }

    /**
     * Returns tile at given position.
     * This can and will return null for tiles that are not already in the cache.
     * @param x tile number on the x axis of the tile to be retrieved
     * @param y tile number on the y axis of the tile to be retrieved
     * @param zoom zoom level of the tile to be retrieved
     * @return tile at given position
     */
    private Tile getTile(int x, int y, int zoom) {
        if (x < tileSource.getTileXMin(zoom) || x > tileSource.getTileXMax(zoom)
         || y < tileSource.getTileYMin(zoom) || y > tileSource.getTileYMax(zoom))
            return null;
        return tileCache.getTile(tileSource, x, y, zoom);
    }

    private boolean loadTile(Tile tile, boolean force) {
        if (tile == null)
            return false;
        if (!force && tile.isLoaded())
            return false;
        if (tile.isLoading())
            return false;
        tileLoader.createTileLoaderJob(tile).submit(force);
        return true;
    }
    
    private CompletableFuture<BufferedImage> futureLoadTile(Tile tile, boolean force) {
    	if (!force && tile.isLoaded())
    		return CompletableFuture.completedFuture(tile.getImage());
    	if (tile.isLoading()) //Shoul already have listener
    		return null;
    	TileJob job = tileLoader.createTileLoaderJob(tile);    	
		
		return CompletableFuture.supplyAsync(() -> {
	        try {	        	
	        	Future<?> submitted = job.submit(force);
	        	if (submitted != null) {
	        		submitted.get();
				}
	        	return tile.getImage();
	        } catch (InterruptedException|ExecutionException e) {
	            throw new RuntimeException(e);
	        }
	    });
    }
    
    public void setTileSetBounds(TileXY t1, TileXY t2) {
//    	tileSet = new TileSet(t1, t2, getTileSource().getMaxZoom());
    	tileSet = new TileSet(t1, t2, 18);
    }

    /**
     * Load all visible tiles.
     * @param force {@code true} to force loading if auto-load is disabled
     * @since 11950
     */
    public void loadAllTiles(boolean force) {
//        TileSet ts = getVisibleTileSet();
        TileSet ts = tileSet;

        // if there is more than 18 tiles on screen in any direction, do not load all tiles!
//        if (ts.tooLarge()) {
//            Logging.warn("Not downloading all tiles because there is more than 18 tiles on an axis!");
//            return;
//        }
        ts.loadAllTiles(force);
    }
    
    /**
     * Load all visible tiles in error.
     * @param force {@code true} to force loading if auto-load is disabled
     * @since 11950
     */
    public void loadAllErrorTiles(boolean force) {
//        TileSet ts = getVisibleTileSet();
        TileSet ts = tileSet;
        ts.loadAllErrorTiles(force);
    }

    @Override
    public boolean imageUpdate(Image img, int infoflags, int x, int y, int width, int height) {
        boolean done = (infoflags & (ERROR | FRAMEBITS | ALLBITS)) != 0;
        Logging.debug("imageUpdate() done: {0} calling repaint", done);

        return !done;
    }

    private boolean imageLoaded(Image i) {
        if (i == null)
            return false;
        int status = Toolkit.getDefaultToolkit().checkImage(i, -1, -1, this);
        return (status & ALLBITS) != 0;
    }
    
    public BufferedImage paintImageFor(Box2D boundingBox, int zoom, boolean clipAndCenter, double growFactorPercent) {
    	
    	TileXY t1 = tileSource.latLonToTileXY(new Coordinate(boundingBox.getMinY(), boundingBox.getMinX()), zoom);
		TileXY t2 = tileSource.latLonToTileXY(new Coordinate(boundingBox.getMaxY(), boundingBox.getMaxX()), zoom);
		int sz = tileSource.getTileSize();
		TileSet tileSet = new TileSet(t1,t2, zoom);
		int xCnt = tileSet.getMaxX() - tileSet.getMinX();
		int yCnt = tileSet.getMaxY() - tileSet.getMinY();
		int xSize = xCnt * sz;
		int ySize = yCnt * sz;
		BufferedImage tilesImg = new BufferedImage(xSize, ySize, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics2d = tilesImg.createGraphics();
		tileSet.loadAllBlocking(false);
		List<Tile> missedTiles = paintTileImages(graphics2d, tileSet, zoom);
		if (!missedTiles.isEmpty()) {
			return null;
		}
		
		if (!clipAndCenter) {
			return tilesImg;
		}
		
		double tx1 = Math.min(t1.getX(), t2.getX());
		double tx2 = Math.max(t1.getX(), t2.getX());
		
		double ty1 = Math.min(t1.getY(), t2.getY());
		double ty2 = Math.max(t1.getY(), t2.getY());
		
		int xpx1 = (int) Math.round((tx1 - tileSet.getMinX()) * sz);
		int xpx2 = (int) Math.round((tx2 - tileSet.getMinX()) * sz);
		
		int ypx1 = (int) Math.round((ty1 - tileSet.getMinY()) * sz); //Because Y (lat) on map and y in screen coords has opposite direction
		int ypx2 = (int) Math.round((ty2 - tileSet.getMinY()) * sz);
		
//		int dx = (int) Math.round((xpx2 - xpx1) * growFactorPercent * 0.5);
//		int dy = (int) Math.round((ypx2 - ypx1) * growFactorPercent * 0.5);
//		
//		int x1 = Math.max(0, xpx1 - dx);
//		int width = Math.min(tilesImg.getWidth() - x1, xpx2 - xpx1 + 2 * dx);
//		
//		int y1 = Math.max(0, ypx1 - dy);
//		int height = Math.min(tilesImg.getHeight() - y1, ypx2 - ypx1 + 2 * dx);
		
		return tilesImg.getSubimage(xpx1, ypx1, xpx2 - xpx1, ypx2 - ypx1);
		
    }
    
    
    // This function is called for several zoom levels, not just the current one.
    // It should not trigger any tiles to be downloaded.
    // It should also avoid polluting the tile cache with any tiles since these tiles are not mandatory.
    //
    // The "border" tile tells us the boundaries of where we may drawn.
    // It will not be from the zoom level that is being drawn currently.
    // If drawing the displayZoomLevel, border is null and we draw the entire tile set.
    private List<Tile> paintTileImages(Graphics2D g, TileSet ts, int zoom) {
        if (zoom <= 0) return Collections.emptyList();
        List<Tile> missedTiles = new LinkedList<>();
        // The callers of this code *require* that we return any tiles that we do not draw in missedTiles.
        // ts.allExistingTiles() by default will only return already-existing tiles.
        // However, we need to return *all* tiles to the callers, so force creation here.
        for (Tile tile : ts.allTilesCreate()) {
            boolean miss = false;
            BufferedImage img = null;
            TileAnchor anchorImage = null;
            if (!tile.isLoaded() || tile.hasError()) {
                miss = true;
            } else {
                synchronized (tile) {
                    img = getLoadedTileImage(tile);
                    anchorImage = getAnchor(tile, img);
                }

                if (img == null || anchorImage == null) {
                    miss = true;
                }
            }
            if (miss) {
                missedTiles.add(tile);
                continue;
            }

            // applying all filters to this layer
            img = applyImageProcessors(img);

            int sz = tile.getTileSource().getTileSize();
            
			int xOrigin = (tile.getXtile() - ts.getMinX()) * sz;
			int yOrigin = (tile.getYtile() - ts.getMinY()) * sz;
			TileAnchor anchorScreen = new TileAnchor(new Point2D.Double(xOrigin, yOrigin),new Point2D.Double(xOrigin + sz, yOrigin + sz)); 
            drawImageInside(g, img, anchorImage, anchorScreen, null);
        }
        return missedTiles;
    }
    
    protected Shape getBorderShape(Bounds border) {
    	Point xy1 = getTileSource().latLonToXY(border.getMinLat(), border.getMinLon(), currentZoomLevel);
    	Point xy2 = getTileSource().latLonToXY(border.getMaxLat(), border.getMaxLon(), currentZoomLevel);
    	int x1 = (int) Math.min(xy1.getX(), xy2.getX());
    	int y1 = (int) Math.min(xy1.getY(), xy2.getY());
    	int w = (int) (Math.max(xy1.getX(), xy2.getX()) - x1);
    	int h = (int) (Math.max(xy1.getY(), xy2.getY()) * y1);
    	return new Rectangle(x1,y1,w,h);
    }


	/**
     * Draw a tile image on screen.
     * @param g the Graphics2D
     * @param toDrawImg tile image
     * @param anchorImage tile anchor in image coordinates
     * @param anchorScreen tile anchor in screen coordinates
     * @param clip clipping region in screen coordinates (can be null)
     */
    private void drawImageInside(Graphics2D g, BufferedImage toDrawImg, TileAnchor anchorImage, TileAnchor anchorScreen, Shape clip) {
        AffineTransform imageToScreen = anchorImage.convert(anchorScreen);
        Point2D screen0 = imageToScreen.transform(new Point.Double(0, 0), null);
        Point2D screen1 = imageToScreen.transform(new Point.Double(
                toDrawImg.getWidth(), toDrawImg.getHeight()), null);

        Shape oldClip = null;
        if (clip != null) {
            oldClip = g.getClip();
            g.clip(clip);
        }
        g.drawImage(toDrawImg, (int) Math.round(screen0.getX()), (int) Math.round(screen0.getY()),
                (int) Math.round(screen1.getX()) - (int) Math.round(screen0.getX()),
                (int) Math.round(screen1.getY()) - (int) Math.round(screen0.getY()), this);
        if (clip != null) {
            g.setClip(oldClip);
        }
    }
    
    private static TileAnchor getAnchor(Tile tile, BufferedImage image) {
        if (image != null) {
            return new TileAnchor(new Point.Double(0, 0), new Point.Double(image.getWidth(), image.getHeight()));
        } else {
            return null;
        }
    }

    /**
     * Returns the image for the given tile image is loaded.
     * Otherwise returns  null.
     *
     * @param tile the Tile for which the image should be returned
     * @return  the image of the tile or null.
     */
    private BufferedImage getLoadedTileImage(Tile tile) {
        BufferedImage img = tile.getImage();
        if (!imageLoaded(img))
            return null;
        return img;
    }

    private void myDrawString(Graphics g, String text, int x, int y) {
        Color oldColor = g.getColor();
        String textToDraw = text;
        if (g.getFontMetrics().stringWidth(text) > tileSource.getTileSize()) {
            // text longer than tile size, split it
            StringBuilder line = new StringBuilder();
            StringBuilder ret = new StringBuilder();
            for (String s: text.split(" ")) {
                if (g.getFontMetrics().stringWidth(line.toString() + s) > tileSource.getTileSize()) {
                    ret.append(line).append('\n');
                    line.setLength(0);
                }
                line.append(s).append(' ');
            }
            ret.append(line);
            textToDraw = ret.toString();
        }
        int offset = 0;
        for (String s: textToDraw.split("\n")) {
            g.setColor(Color.black);
            g.drawString(s, x + 1, y + offset + 1);
            g.setColor(oldColor);
            g.drawString(s, x, y + offset);
            offset += g.getFontMetrics().getHeight() + 3;
        }
    }

    private final TileSet nullTileSet = new TileSet();

	private TileSet tileSet;

    protected class TileSet extends TileRange {

        private volatile TileSetInfo info;

        protected TileSet(TileXY t1, TileXY t2, int zoom) {
            super(t1, t2, zoom);
            sanitize();
        }

		protected TileSet(TileRange range) {
            super(range);
            sanitize();
        }

        /**
         * null tile set
         */
        private TileSet() {
            // default
        }

        protected void sanitize() {
            minX = Utils.clamp(minX, tileSource.getTileXMin(zoom), tileSource.getTileXMax(zoom));
            maxX = Utils.clamp(maxX, tileSource.getTileXMin(zoom), tileSource.getTileXMax(zoom));
            minY = Utils.clamp(minY, tileSource.getTileYMin(zoom), tileSource.getTileYMax(zoom));
            maxY = Utils.clamp(maxY, tileSource.getTileYMin(zoom), tileSource.getTileYMax(zoom));
        }

        private boolean tooSmall() {
            return this.tilesSpanned() < 2.1;
        }

        private boolean tooLarge() {
            return insane() || this.tilesSpanned() > 20;
        }

        private boolean insane() {
            return tileCache == null; // || size() > tileCache.getCacheSize();
        }

        /**
         * Get all tiles represented by this TileSet that are already in the tileCache.
         * @return all tiles represented by this TileSet that are already in the tileCache
         */
        private List<Tile> allExistingTiles() {
            return allTiles(AbstractTileSourceLayer.this::getTile);
        }

        private List<Tile> allTilesCreate() {
            return allTiles(AbstractTileSourceLayer.this::getOrCreateTile);
        }

        private List<Tile> allTiles(Function<TilePosition, Tile> mapper) {
            return tilePositions().map(mapper).filter(Objects::nonNull).collect(Collectors.toList());
        }

        /**
         * Gets a stream of all tile positions in this set
         * @return A stream of all positions
         */
        public Stream<TilePosition> tilePositions() {
            if (zoom == 0 || this.insane()) {
                return Stream.empty(); // Tileset is either empty or too large
            } else {
                return IntStream.rangeClosed(minX, maxX).mapToObj(
                        x -> IntStream.rangeClosed(minY, maxY).mapToObj(y -> new TilePosition(x, y, zoom))
                        ).flatMap(Function.identity());
            }
        }

        private List<Tile> allLoadedTiles() {
            return allExistingTiles().stream().filter(Tile::isLoaded).collect(Collectors.toList());
        }

        /**
         * @return comparator, that sorts the tiles from the center to the edge of the current screen
         */
        private Comparator<Tile> getTileDistanceComparator() {
            final int centerX = (int) Math.ceil((minX + maxX) / 2d);
            final int centerY = (int) Math.ceil((minY + maxY) / 2d);
            return Comparator.comparingInt(t -> Math.abs(t.getXtile() - centerX) + Math.abs(t.getYtile() - centerY));
        }

        private void loadAllTiles(boolean force) {
        	tilePositions().map(AbstractTileSourceLayer.this::getOrCreateTile).filter(Objects::nonNull).forEach(t -> loadTile(t, force));
//            List<Tile> allTiles = allTilesCreate();
//            allTiles.sort(getTileDistanceComparator());
//            for (Tile t : allTiles) {
//                loadTile(t, force);
//            }
        }
        
        private void loadAllBlocking(boolean force) {
        	
        	List<CompletableFuture<BufferedImage>> futures = tilePositions().map(AbstractTileSourceLayer.this::getOrCreateTile).filter(Objects::nonNull).map(
        			t -> futureLoadTile(t, force)        			
        	).filter(feature -> feature != null).collect(Collectors.toList());
        	CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        }

        private void loadAllErrorTiles(boolean force) {
            for (Tile t : this.allTilesCreate()) {
                if (t.hasError()) {
                    tileLoader.createTileLoaderJob(t).submit(force);
                }
            }
        }

        /**
         * Call the given paint method for all tiles in this tile set.<p>
         * Uses a parallel stream.
         * @param visitor A visitor to call for each tile.
         * @param missed a consumer to call for each missed tile.
         */
        public void visitTiles(Consumer<Tile> visitor, Consumer<TilePosition> missed) {
            tilePositions().parallel().forEach(tp -> visitTilePosition(visitor, tp, missed));
        }

        private void visitTilePosition(Consumer<Tile> visitor, TilePosition tp, Consumer<TilePosition> missed) {
            Tile tile = getTile(tp);
            if (tile == null) {
                missed.accept(tp);
            } else {
                visitor.accept(tile);
            }
        }

        /**
         * Check if there is any tile fully loaded without error.
         * @return true if there is any tile fully loaded without error
         */
        public boolean hasVisibleTiles() {
            return getTileSetInfo().hasVisibleTiles;
        }

        /**
         * Check if there there is a tile that is overzoomed.
         * <p>
         * I.e. the server response for one tile was "there is no tile here".
         * This usually happens when zoomed in too much. The limit depends on
         * the region, so at the edge of such a region, some tiles may be
         * available and some not.
         * @return true if there there is a tile that is overzoomed
         */
        public boolean hasOverzoomedTiles() {
            return getTileSetInfo().hasOverzoomedTiles;
        }

        /**
         * Check if there are tiles still loading.
         * <p>
         * This is the case if there is a tile not yet in the cache, or in the
         * cache but marked as loading ({@link Tile#isLoading()}.
         * @return true if there are tiles still loading
         */
        public boolean hasLoadingTiles() {
            return getTileSetInfo().hasLoadingTiles;
        }

        /**
         * Check if all tiles in the range are fully loaded.
         * <p>
         * A tile is considered to be fully loaded even if the result of loading
         * the tile was an error.
         * @return true if all tiles in the range are fully loaded
         */
        public boolean hasAllLoadedTiles() {
            return getTileSetInfo().hasAllLoadedTiles;
        }

        private TileSetInfo getTileSetInfo() {
            if (info == null) {
                synchronized (this) {
                    if (info == null) {
                        List<Tile> allTiles = this.allExistingTiles();
                        TileSetInfo newInfo = new TileSetInfo();
                        newInfo.hasLoadingTiles = allTiles.size() < this.size();
                        newInfo.hasAllLoadedTiles = true;
                        for (Tile t : allTiles) {
                            if ("no-tile".equals(t.getValue("tile-info"))) {
                                newInfo.hasOverzoomedTiles = true;
                            }
                            if (t.isLoaded()) {
                                if (!t.hasError()) {
                                    newInfo.hasVisibleTiles = true;
                                }
                            } else {
                                newInfo.hasAllLoadedTiles = false;
                                if (t.isLoading()) {
                                    newInfo.hasLoadingTiles = true;
                                }
                            }
                        }
                        info = newInfo;
                    }
                }
            }
            return info;
        }

        @Override
        public String toString() {
            return getClass().getName() + ": zoom: " + zoom + " X(" + minX + ", " + maxX + ") Y(" + minY + ", " + maxY + ") size: " + size();
        }
    }

    /**
     * Data container to hold information about a {@code TileSet} class.
     */
    private static class TileSetInfo {
        boolean hasVisibleTiles;
        boolean hasOverzoomedTiles;
        boolean hasLoadingTiles;
        boolean hasAllLoadedTiles;
    }

//    /**
//     * Create a TileSet by EastNorth bbox taking a layer shift in account
//     * @param bounds the EastNorth bounds
//     * @param zoom zoom level
//     * @return the tile set
//     */
//    protected TileSet getTileSet(ProjectionBounds bounds, int zoom) {
//        if (zoom == 0)
//            return new TileSet();
//        TileXY t1, t2;
//            Projection projServer = Projections.getProjectionByCode(tileSource.getServerCRS());
//            if (projServer == null) {
//                throw new IllegalStateException(tileSource.toString());
//            }
//            ProjectionBounds projBounds = new ProjectionBounds(
//                    CoordinateConversion.projToEn(topLeftUnshifted),
//                    CoordinateConversion.projToEn(botRightUnshifted));
//            ProjectionBounds bbox = projServer.getEastNorthBoundsBox(projBounds, ProjectionRegistry.getProjection());
//            t1 = tileSource.projectedToTileXY(CoordinateConversion.enToProj(bbox.getMin()), zoom);
//            t2 = tileSource.projectedToTileXY(CoordinateConversion.enToProj(bbox.getMax()), zoom);
//        return new TileSet(t1, t2, zoom);
//    }



    /**
     * Task responsible for precaching imagery along the gpx track
     *
     */
    public class PrecacheTask implements TileLoaderListener {
        private final ProgressMonitor progressMonitor;
        private final int totalCount;
        private final AtomicInteger processedCount = new AtomicInteger(0);
        private final TileLoader tileLoader;
        private final Set<Tile> requestedTiles;

        /**
         * Constructs a new {@code PrecacheTask}.
         * @param progressMonitor that will be notified about progess of the task
         * @param bufferY buffer Y in degrees around which to download tiles
         * @param bufferX buffer X in degrees around which to download tiles
         * @param points list of points along which to download
         */
        public PrecacheTask(ProgressMonitor progressMonitor, List<LatLon> points, double bufferX, double bufferY) {
            this.progressMonitor = progressMonitor;
            this.tileLoader = getTileLoaderFactory().makeTileLoader(this, getHeaders(tileSource), minimumTileExpire);
            if (this.tileLoader instanceof TMSCachedTileLoader) {
                ((TMSCachedTileLoader) this.tileLoader).setDownloadExecutor(
                        TMSCachedTileLoader.getNewThreadPoolExecutor("Precache downloader"));
            }
            requestedTiles = new ConcurrentSkipListSet<>(
                    (o1, o2) -> String.CASE_INSENSITIVE_ORDER.compare(o1.getKey(), o2.getKey()));
            for (LatLon point: points) {
                TileXY minTile = tileSource.latLonToTileXY(point.lat() - bufferY, point.lon() - bufferX, currentZoomLevel);
                TileXY curTile = tileSource.latLonToTileXY(CoordinateConversion.llToCoor(point), currentZoomLevel);
                TileXY maxTile = tileSource.latLonToTileXY(point.lat() + bufferY, point.lon() + bufferX, currentZoomLevel);

                // take at least one tile of buffer
                int minY = Math.min(curTile.getYIndex() - 1, minTile.getYIndex());
                int maxY = Math.max(curTile.getYIndex() + 1, maxTile.getYIndex());
                int minX = Math.min(curTile.getXIndex() - 1, minTile.getXIndex());
                int maxX = Math.max(curTile.getXIndex() + 1, maxTile.getXIndex());

                for (int x = minX; x <= maxX; x++) {
                    for (int y = minY; y <= maxY; y++) {
                        requestedTiles.add(new Tile(tileSource, x, y, currentZoomLevel));
                    }
                }
            }

            this.totalCount = requestedTiles.size();
            this.progressMonitor.setTicksCount(requestedTiles.size());

        }

        /**
         * @return true, if all is done
         */
        public boolean isFinished() {
            return processedCount.get() >= totalCount;
        }

        /**
         * @return total number of tiles to download
         */
        public int getTotalCount() {
            return totalCount;
        }

        /**
         * cancel the task
         */
        public void cancel() {
            if (tileLoader instanceof TMSCachedTileLoader) {
                ((TMSCachedTileLoader) tileLoader).cancelOutstandingTasks();
            }
        }

        @Override
        public void tileLoadingFinished(Tile tile, boolean success) {
            int processed = this.processedCount.incrementAndGet();
            if (success) {
                synchronized (progressMonitor) {
                    if (!this.progressMonitor.isCanceled()) {
                        this.progressMonitor.worked(1);
                        this.progressMonitor.setCustomText(MessageFormat.format("Downloaded {0}/{1} tiles", processed, totalCount));
                    }
                }
            } else {
                Logging.warn("Tile loading failure: " + tile + " - " + tile.getErrorMessage());
            }
        }

        /**
         * @return tile loader that is used to load the tiles
         */
        public TileLoader getTileLoader() {
            return tileLoader;
        }

        /**
         * Execute the download
         */
        public void run() {
            TileLoader loader = getTileLoader();
            for (Tile t: requestedTiles) {
                if (!progressMonitor.isCanceled()) {
                    loader.createTileLoaderJob(t).submit();
                }
            }

        }
    }

    /**
     * Calculates tiles, that needs to be downloaded to cache, gets a current tile loader and creates a task to download
     * all of the tiles. Buffer contains at least one tile.
     *
     * To prevent accidental clear of the queue, new download executor is created with separate queue
     *
     * @param progressMonitor progress monitor for download task
     * @param points lat/lon coordinates to download
     * @param bufferX how many units in current Coordinate Reference System to cover in X axis in both sides
     * @param bufferY how many units in current Coordinate Reference System to cover in Y axis in both sides
     * @return precache task representing download task
     */
    public AbstractTileSourceLayer<T>.PrecacheTask getDownloadAreaToCacheTask(final ProgressMonitor progressMonitor, List<LatLon> points,
            double bufferX, double bufferY) {
        return new PrecacheTask(progressMonitor, points, bufferX, bufferY);
    }

}
