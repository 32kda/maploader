package com.onpositive.maploader;

import java.io.File;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.osm2xp.classification.TagUtil;
import com.osm2xp.classification.model.WayEntity;
import com.osm2xp.core.model.osm.Tag;

public class AirfieldSamplesCollector extends SamplesCollector<AirfieldSurface> {

	public AirfieldSamplesCollector(File basicFolder) {
		super(basicFolder, 0.4);
	}

	@Override
	protected AirfieldSurface convert(String fileName, WayEntity entity) {
		return new AirfieldSurface(fileName, isHardSurface(TagUtil.getValue("surface", entity.getTags())));
	}

	@Override
	protected boolean isGoodSample(List<Tag> tags) {
		return "runway".equalsIgnoreCase(TagUtil.getValue("aeroway", tags)) && StringUtils.stripToEmpty(TagUtil.getValue("surface", tags)).trim().length() > 0;
	}
	
	private static boolean isHardSurface(String osmSurfaceType) {
		return ("asphalt".equals(osmSurfaceType) || "paved".equals(osmSurfaceType) || osmSurfaceType.startsWith("concrete") || "metal".equals(osmSurfaceType));
	}

}
