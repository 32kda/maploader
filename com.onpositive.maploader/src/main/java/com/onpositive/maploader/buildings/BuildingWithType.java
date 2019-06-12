package com.onpositive.maploader.buildings;

import com.onpositive.maploader.IHasId;
import com.osm2xp.classification.BuildingType;
import com.osm2xp.classification.annotations.Result;

public class BuildingWithType implements IHasId {
	
	public final String id;
	@Result
	public final BuildingType buildingType;

	public BuildingWithType(String id, BuildingType buildingType) {
		super();
		this.id = id;
		this.buildingType = buildingType;
	}

	@Override
	public String getId() {
		return id;
	}

}
