// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.io;



import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.Optional;

import org.openstreetmap.josm.gui.progress.NullProgressMonitor;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.tools.CheckParameterUtil;

/**
 * Read from an other reader and increment an progress counter while on the way.
 * @author Imi
 */
public class ProgressInputStream extends InputStream {

    private final StreamProgressUpdater updater;
    private final InputStream in;

    /**
     * Constructs a new {@code ProgressInputStream}.
     *
     * @param in the stream to monitor. Must not be null
     * @param size the total size which will be sent
     * @param progressMonitor the monitor to report to
     * @since 9172
     */
    public ProgressInputStream(InputStream in, long size, ProgressMonitor progressMonitor) {
        CheckParameterUtil.ensureParameterNotNull(in, "in");
        this.updater = new StreamProgressUpdater(size,
                Optional.ofNullable(progressMonitor).orElse(NullProgressMonitor.INSTANCE), "Downloading data...");
        this.in = in;
    }

    @Override
    public void close() throws IOException {
        try {
            in.close();
        } finally {
            updater.finishTask();
        }
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int read = in.read(b, off, len);
        if (read != -1) {
            updater.advanceTicker(read);
        } else {
            updater.finishTask();
        }
        return read;
    }

    @Override
    public int read() throws IOException {
        int read = in.read();
        if (read != -1) {
            updater.advanceTicker(1);
        } else {
            updater.finishTask();
        }
        return read;
    }
}
