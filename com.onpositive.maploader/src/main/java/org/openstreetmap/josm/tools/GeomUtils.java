package org.openstreetmap.josm.tools;

import math.geom2d.Box2D;

public class GeomUtils {

	
	public static Double latLonDistance(double lat1, double lon1, double lat2,
			double lon2) {
		double earthRadius = 3958.75;
		double dLat = Math.toRadians(lat2 - lat1);
		double dLng = Math.toRadians(lon2 - lon1);
		double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
				+ Math.cos(Math.toRadians(lat1))
				* Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2)
				* Math.sin(dLng / 2);
		double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
		double dist = earthRadius * c;

		int meterConversion = 1609;

		return Double.valueOf(dist * meterConversion);
	}

	public static Box2D checkMinSizeAndGrow(Box2D boundingBox, int minBoundingBoxMeters, double percent) {
		return grow(checkMinSize(boundingBox, minBoundingBoxMeters), percent);
	}
	
	public static Box2D checkMinSize(Box2D boundingBox, int minBoundingBoxMeters) {
		double distLat = latLonDistance(boundingBox.getMinY(), boundingBox.getMinX(), boundingBox.getMaxY(), boundingBox.getMinX()); 
		if (distLat < minBoundingBoxMeters) {
			double percent =  minBoundingBoxMeters / distLat - 1;
			boundingBox = doGrow(boundingBox, percent, false, true);
		}
		double distLon = latLonDistance(boundingBox.getMinY(), boundingBox.getMinX(), boundingBox.getMinY(), boundingBox.getMaxX());
		if (distLon < minBoundingBoxMeters) {
			double percent =  minBoundingBoxMeters / distLon - 1;
			boundingBox = doGrow(boundingBox, percent, true, false);
		}
		return boundingBox;
	}
	
    public static Box2D grow(Box2D boundingBox, double percent) {
    	return doGrow(boundingBox, percent, true, true);
	}
    
    private static Box2D doGrow(Box2D boundingBox, double percent, boolean growX, boolean growY) {
    	double w = boundingBox.getMaxX() - boundingBox.getMinX();
    	double h = boundingBox.getMaxY() - boundingBox.getMinY();
    	double dx = growX ? 0.5 * w * percent : 0;
    	double dy = growY ? 0.5 * h * percent : 0;
    	return new Box2D(boundingBox.getMinX() - dx, boundingBox.getMaxX() + dx, boundingBox.getMinY() - dy, boundingBox.getMaxY() + dy);
    }

	
}
