package com.onpositive.maploader;

import com.osm2xp.classification.annotations.Result;

public class AirfieldSurface implements IHasId{
	
	public final String id;
	@Result
	public final boolean hard;
	
	public AirfieldSurface(String id, boolean hard) {
		super();
		this.id = id;
		this.hard = hard;
	}

	@Override
	public String getId() {
		return id;
	}
	
}
