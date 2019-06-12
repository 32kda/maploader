package com.onpositive.maploader;

import java.io.File;

/**
 * Import pics for buildings and other map features
 *
 */
public class App 
{
	
    public static void main( String[] args )
    {
//    	File input = new File("f:/tmp/osm/crimean-fed-district-latest.osm.pbf");
    	File input = new File("f:\\util\\xplane\\1.pbf");
//    	Predicate<List<Tag>> predicate = getRunwayPredicate();
//    	File outFolder = new File("F:/tmp/imagery_airfield");
//    	new AirfieldSamplesCollector(outFolder).collectSamples("runways", folder, outFolder);
    	File basicFolder = new File("F:/tmp/ds_collection");
		File outFolder = new File(basicFolder, "building_types");
		
		new BuildingTypeSamplesCollector(basicFolder).collectSamples("buildings", input, outFolder, false);
//    	collectSamples(folder, outFolder, predicate);
    	
    }
    
}
