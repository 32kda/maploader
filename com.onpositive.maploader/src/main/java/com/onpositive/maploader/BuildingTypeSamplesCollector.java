package com.onpositive.maploader;

import java.io.File;
import java.util.List;

import com.onpositive.maploader.buildings.BuildingWithType;
import com.osm2xp.classification.BuildingType;
import com.osm2xp.classification.TypeProvider;
import com.osm2xp.classification.model.WayEntity;
import com.osm2xp.core.model.osm.Tag;

public class BuildingTypeSamplesCollector extends SamplesCollector<BuildingWithType> {

	public BuildingTypeSamplesCollector(File basicFolder) {
		super(basicFolder, 0.4, 18);
	}

	@Override
	protected BuildingWithType convert(String fileName, WayEntity entity) {
		BuildingType buildingType = TypeProvider.getBuildingType(entity.getTags());
		if (buildingType != null) {
			return new BuildingWithType("" +entity.getId(), buildingType);
		}
		return null;
	}

	@Override
	protected boolean isGoodSample(List<Tag> tags) {
		return TypeProvider.getBuildingType(tags) != null;
	}

	@Override
	protected int getMinBoundingBoxMeters() {
		return 20;
	}


}
