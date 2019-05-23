// License: GPL. For details, see Readme.txt file.
package org.openstreetmap.gui.jmapviewer.interfaces;

import org.apache.commons.jcs.access.behavior.ICacheAccess;
import org.openstreetmap.josm.data.cache.BufferedImageCacheEntry;

/**
 * Interface that allow cleaning the tile cache without specifying exact type of loader
 */
public interface CachedTileLoader {
    void clearCache(TileSource source);
    
    public ICacheAccess<String, BufferedImageCacheEntry> getCacheAccess();
}
