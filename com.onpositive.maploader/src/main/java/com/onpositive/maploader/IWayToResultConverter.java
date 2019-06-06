package com.onpositive.maploader;

import com.osm2xp.classification.model.WayEntity;

public interface IWayToResultConverter<T> {
	T convert(String imageFileName, WayEntity entity);
}
