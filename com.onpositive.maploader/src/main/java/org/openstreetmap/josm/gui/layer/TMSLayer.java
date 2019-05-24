// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.layer;

import java.util.Collection;
import java.util.Collections;

import org.apache.commons.jcs.access.CacheAccess;
import org.openstreetmap.gui.jmapviewer.interfaces.TileLoader;
import org.openstreetmap.gui.jmapviewer.tilesources.AbstractTMSTileSource;
import org.openstreetmap.gui.jmapviewer.tilesources.ScanexTileSource;
import org.openstreetmap.gui.jmapviewer.tilesources.TMSTileSource;
import org.openstreetmap.gui.jmapviewer.tilesources.TemplatedTMSTileSource;
import org.openstreetmap.josm.data.cache.BufferedImageCacheEntry;
import org.openstreetmap.josm.data.imagery.CachedAttributionBingAerialTileSource;
import org.openstreetmap.josm.data.imagery.ImageryInfo;
import org.openstreetmap.josm.data.imagery.ImageryInfo.ImageryType;
import org.openstreetmap.josm.data.imagery.TMSCachedTileLoader;
import org.openstreetmap.josm.tools.Logging;

/**
 * Class that displays a slippy map layer.
 *
 * @author Frederik Ramm
 * @author LuVar &lt;lubomir.varga@freemap.sk&gt;
 * @author Dave Hansen &lt;dave@sr71.net&gt;
 * @author Upliner &lt;upliner@gmail.com&gt;
 * @since 3715
 */
public class TMSLayer extends AbstractCachedTileSourceLayer<TMSTileSource>  {
    private static final String CACHE_REGION_NAME = "TMS";

    private static final String PREFERENCE_PREFIX = "imagery.tms";

    /**
     * Create a layer based on ImageryInfo
     * @param info description of the layer
     */
    public TMSLayer(ImageryInfo info) {
        super(info);
    }

    /**
     * Creates and returns a new TileSource instance depending on the {@link ImageryType}
     * of the {@link ImageryInfo} object specified in the constructor.
     *
     * If no appropriate TileSource is found, null is returned.
     * Currently supported ImageryType are {@link ImageryType#TMS},
     * {@link ImageryType#BING}, {@link ImageryType#SCANEX}.
     *
     *
     * @return a new TileSource instance or null if no TileSource for the ImageryInfo/ImageryType could be found.
     * @throws IllegalArgumentException if url from imagery info is null or invalid
     */
    @Override
	public TMSTileSource getTileSource() {
        return getTileSourceStatic(info, () -> {
            Logging.debug("Attribution loaded, running loadAllErrorTiles");
            this.loadAllErrorTiles(false);
        });
    }

    @Override
    public Collection<String> getNativeProjections() {
        return Collections.singletonList("EPSG:3857");
    }

    /**
     * Creates and returns a new TileSource instance depending on the {@link ImageryType}
     * of the passed ImageryInfo object.
     *
     * If no appropriate TileSource is found, null is returned.
     * Currently supported ImageryType are {@link ImageryType#TMS},
     * {@link ImageryType#BING}, {@link ImageryType#SCANEX}.
     *
     * @param info imagery info
     * @return a new TileSource instance or null if no TileSource for the ImageryInfo/ImageryType could be found.
     * @throws IllegalArgumentException if url from imagery info is null or invalid
     */
    public static AbstractTMSTileSource getTileSourceStatic(ImageryInfo info) {
        return getTileSourceStatic(info, null);
    }

    /**
     * Creates and returns a new TileSource instance depending on the {@link ImageryType}
     * of the passed ImageryInfo object.
     *
     * If no appropriate TileSource is found, null is returned.
     * Currently supported ImageryType are {@link ImageryType#TMS},
     * {@link ImageryType#BING}, {@link ImageryType#SCANEX}.
     *
     * @param info imagery info
     * @param attributionLoadedTask task to be run once attribution is loaded, might be null, if nothing special shall happen
     * @return a new TileSource instance or null if no TileSource for the ImageryInfo/ImageryType could be found.
     * @throws IllegalArgumentException if url from imagery info is null or invalid
     */
    public static TMSTileSource getTileSourceStatic(ImageryInfo info, Runnable attributionLoadedTask) {
        if (info.getImageryType() == ImageryType.TMS) {
            TemplatedTMSTileSource.checkUrl(info.getUrl());
            TMSTileSource t = new TemplatedTMSTileSource(info);
            info.setAttribution(t);
            return t;
        } else if (info.getImageryType() == ImageryType.BING) {
            return new CachedAttributionBingAerialTileSource(info, attributionLoadedTask);
        } else if (info.getImageryType() == ImageryType.SCANEX) {
            return new ScanexTileSource(info);
        }
        return null;
    }

    @Override
    protected Class<? extends TileLoader> getTileLoaderClass() {
        return TMSCachedTileLoader.class;
    }

    @Override
    protected String getCacheName() {
        return CACHE_REGION_NAME;
    }

    /**
     * @return cache for TMS region
     */
    public static CacheAccess<String, BufferedImageCacheEntry> getCache() {
        return AbstractCachedTileSourceLayer.getCache(CACHE_REGION_NAME);
    }

}
