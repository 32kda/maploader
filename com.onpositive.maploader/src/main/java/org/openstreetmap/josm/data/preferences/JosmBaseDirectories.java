// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.data.preferences;

import static org.openstreetmap.josm.tools.Utils.getSystemProperty;

import java.io.File;

import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.spi.preferences.IBaseDirectories;
import org.openstreetmap.josm.tools.Logging;

import com.osm2xp.generation.paths.PathsService;

/**
 * Class provides base directory locations for JOSM.
 * @since 13021
 */
public final class JosmBaseDirectories implements IBaseDirectories {

    private JosmBaseDirectories() {
        // hide constructor
    }

    private static class InstanceHolder {
        static final JosmBaseDirectories INSTANCE = new JosmBaseDirectories();
    }

    /**
     * Returns the unique instance.
     * @return the unique instance
     */
    public static JosmBaseDirectories getInstance() {
        return InstanceHolder.INSTANCE;
    }

    /**
     * Internal storage for the preference directory.
     */
    private File preferencesDir;

    /**
     * Internal storage for the cache directory.
     */
    private File cacheDir;

    /**
     * Internal storage for the user data directory.
     */
    private File userdataDir;

    @Override
    public File getPreferencesDirectory(boolean createIfMissing) {
        if (preferencesDir == null) {
            String path = getSystemProperty("josm.pref");
            if (path != null) {
                preferencesDir = new File(path).getAbsoluteFile();
            } else {
                path = getSystemProperty("josm.home");
                if (path != null) {
                    preferencesDir = new File(path).getAbsoluteFile();
                } else {
                    preferencesDir = PathsService.getPathsProvider().getBasicFolder();
                }
            }
        }
        try {
            if (createIfMissing && !preferencesDir.exists() && !preferencesDir.mkdirs()) {
                Logging.warn("Failed to create missing preferences directory: {0}", preferencesDir.getAbsoluteFile());
            }
        } catch (SecurityException e) {
            Logging.log(Logging.LEVEL_ERROR, "Unable to check if preferences dir must be created", e);
        }
        return preferencesDir;
    }

    @Override
    public File getUserDataDirectory(boolean createIfMissing) {
        if (userdataDir == null) {
            String path = getSystemProperty("josm.userdata");
            if (path != null) {
                userdataDir = new File(path).getAbsoluteFile();
            } else {
                path = getSystemProperty("josm.home");
                if (path != null) {
                    userdataDir = new File(path).getAbsoluteFile();
                } else {
                    userdataDir = PathsService.getPathsProvider().getBasicFolder();
                }
            }
        }
        try {
            if (createIfMissing && !userdataDir.exists() && !userdataDir.mkdirs()) {
                Logging.warn("Failed to create missing user data directory: {0}", userdataDir.getAbsoluteFile());
            }
        } catch (SecurityException e) {
            Logging.log(Logging.LEVEL_ERROR, "Unable to check if user data dir must be created", e);
        }
        return userdataDir;
    }

    @Override
    public File getCacheDirectory(boolean createIfMissing) {
        if (cacheDir == null) {
            String path = getSystemProperty("josm.cache");
            if (path != null) {
                cacheDir = new File(path).getAbsoluteFile();
            } else {
                path = getSystemProperty("josm.home");
                if (path != null) {
                    cacheDir = new File(path, "cache");
                } else {
                    path = Config.getPref().get("cache.folder", null);
                    if (path != null) {
                        cacheDir = new File(path).getAbsoluteFile();
                    } else {
                        cacheDir = PathsService.getPathsProvider().getCacheFolder();
                    }
                }
            }
        }
        try {
            if (createIfMissing && !cacheDir.exists() && !cacheDir.mkdirs()) {
                Logging.warn("Failed to create missing cache directory: {0}", cacheDir.getAbsoluteFile());                
            }
        } catch (SecurityException e) {
            Logging.log(Logging.LEVEL_ERROR, "Unable to check if cache dir must be created", e);
        }
        return cacheDir;
    }

    /**
     * Clears any previously calculated values used for {@link #getPreferencesDirectory(boolean)},
     * {@link #getCacheDirectory(boolean)} or {@link #getUserDataDirectory(boolean)}. Useful for tests.
     * @since 14052
     */
    public void clearMemos() {
        this.preferencesDir = null;
        this.cacheDir = null;
        this.userdataDir = null;
    }
}
