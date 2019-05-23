package com.osm2xp.generation.paths;

public class PathsService {

	private static IPathsProvider pathsProvider = new DefaultPathsProvider();

	public static IPathsProvider getPathsProvider() {
		return pathsProvider;
	}

	public static void setPathsProvider(IPathsProvider pathsProvider) {
		PathsService.pathsProvider = pathsProvider;
	}
	
}
