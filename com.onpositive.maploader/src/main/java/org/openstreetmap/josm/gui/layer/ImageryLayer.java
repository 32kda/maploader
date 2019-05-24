// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.layer;



import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ImagingOpException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Icon;

import org.openstreetmap.josm.data.imagery.ImageryInfo;
import org.openstreetmap.josm.data.preferences.IntegerProperty;
import org.openstreetmap.josm.tools.ImageProcessor;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.ImageProvider.ImageSizes;
import org.openstreetmap.josm.tools.Logging;

/**
 * Abstract base class for background imagery layers ({@link WMSLayer}, {@link TMSLayer}, {@link WMTSLayer}).
 *
 * Handles some common tasks, like image filters, image processors, etc.
 */
public abstract class ImageryLayer  {

    /**
     * The default value for the sharpen filter for each imagery layer.
     */
    public static final IntegerProperty PROP_SHARPEN_LEVEL = new IntegerProperty("imagery.sharpen_level", 0);

    private final List<ImageProcessor> imageProcessors = new ArrayList<>();

    protected final ImageryInfo info;

    protected Icon icon;


    /**
     * Constructs a new {@code ImageryLayer}.
     * @param info imagery info
     */
    public ImageryLayer(ImageryInfo info) {
//        super(info.getName());
        this.info = info;
        if (info.getIcon() != null) {
            icon = new ImageProvider(info.getIcon()).setOptional(true).
                    setMaxSize(ImageSizes.LAYER).get();
        }
        if (icon == null) {
            icon = ImageProvider.get("imagery_small");
        }
//        for (ImageProcessor processor : filterSettings.getProcessors()) {
//            addImageProcessor(processor);
//        }
    }

    /**
     * Returns imagery info.
     * @return imagery info
     */
    public ImageryInfo getInfo() {
        return info;
    }
    

    /**
     * Create a new imagery layer
     * @param info The imagery info to use as base
     * @return The created layer
     */
    public static ImageryLayer create(ImageryInfo info) {
        switch(info.getImageryType()) {
        case WMS:
        case WMS_ENDPOINT:
            return new WMSLayer(info);
        case WMTS:
            return new WMTSLayer(info);
        case TMS:
        case BING:
        case SCANEX:
            return new TMSLayer(info);
        default:
            throw new AssertionError(MessageFormat.format("Unsupported imagery type: {0}", info.getImageryType()));
        }
    }


    /**
     * This method adds the {@link ImageProcessor} to this Layer if it is not {@code null}.
     *
     * @param processor that processes the image
     *
     * @return true if processor was added, false otherwise
     */
    public boolean addImageProcessor(ImageProcessor processor) {
        return processor != null && imageProcessors.add(processor);
    }

    /**
     * This method removes given {@link ImageProcessor} from this layer
     *
     * @param processor which is needed to be removed
     *
     * @return true if processor was removed
     */
    public boolean removeImageProcessor(ImageProcessor processor) {
        return imageProcessors.remove(processor);
    }

    /**
     * Wraps a {@link BufferedImageOp} to be used as {@link ImageProcessor}.
     * @param op the {@link BufferedImageOp}
     * @param inPlace true to apply filter in place, i.e., not create a new {@link BufferedImage} for the result
     *                (the {@code op} needs to support this!)
     * @return the {@link ImageProcessor} wrapper
     */
    public static ImageProcessor createImageProcessor(final BufferedImageOp op, final boolean inPlace) {
        return image -> op.filter(image, inPlace ? image : null);
    }

    /**
     * This method gets all {@link ImageProcessor}s of the layer
     *
     * @return list of image processors without removed one
     */
    public List<ImageProcessor> getImageProcessors() {
        return imageProcessors;
    }

    /**
     * Applies all the chosen {@link ImageProcessor}s to the image
     *
     * @param img - image which should be changed
     *
     * @return the new changed image
     */
    public BufferedImage applyImageProcessors(BufferedImage img) {
        for (ImageProcessor processor : imageProcessors) {
            try {
                img = processor.process(img);
            } catch (ImagingOpException e) {
                Logging.error(e);
            }
        }
        return img;
    }

    /**
     * An additional menu entry in the imagery offset menu.
     * @author Michael Zangl
     * @see ImageryLayer#getOffsetMenuEntries()
     * @since 13243
     */
    public interface OffsetMenuEntry {
        /**
         * Get the label to use for this menu item
         * @return The label to display in the menu.
         */
        String getLabel();

        /**
         * Test whether this bookmark is currently active
         * @return <code>true</code> if it is active
         */
        boolean isActive();

        /**
         * Load this bookmark
         */
        void actionPerformed();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [info=" + info + ']';
    }
}
