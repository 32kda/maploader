// License: GPL. For details, see Readme.txt file.
package org.openstreetmap.gui.jmapviewer.interfaces;

import java.util.concurrent.Future;

/**
 * Interface for implementing a tile loading job. Tiles are usually loaded via HTTP
 * or from a file.
 *
 * @author Dirk Stöcker
 */
public interface TileJob extends Runnable {

    /**
     * submits download job to backend.
     */
    void submit();

    /**
     * submits download job to backend.
     * @param force true if the load should skip all the caches (local &amp; remote)
     * @return 
     */
    Future<?> submit(boolean force);
}
