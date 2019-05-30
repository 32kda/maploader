package com.onpositive.dataset.cleaner.io;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.onpositive.dataset.cleaner.model.Dataset;

public class Separator {
	
	
	public Separator(File toSeparate, double valRatio) {
		List<ListMultimap<String, String[]>> classMultimaps = new ArrayList<>();
		for (int i = 0; i < getNumClasses(); i++) {
			classMultimaps.add(ArrayListMultimap.create());
		}
		try {
			Dataset originalDataset = DatasetIO.fromCSV(toSeparate);
			List<String[]> rows = originalDataset.getRows();
			int imageCol = originalDataset.getImageColIdx();
			for (String[] row : rows) {
				String imgPath = row[imageCol];
				String name = extractName(imgPath);
				int classIdx = extractClass(row);
				classMultimaps.get(classIdx).put(name, row);
			}

			List<String[]> train = new ArrayList<>();
			List<String[]> test = new ArrayList<>();
			for (ListMultimap<String, String[]> multimap : classMultimaps) {
				List<String> keys = new ArrayList<>(multimap.keySet());
				int divIdx = (int) Math.round(keys.size() * valRatio);
				for (int i = 0; i < divIdx; i++) {
					List<String[]> recs = multimap.get(keys.get(i));
					for (String[] rec : recs) {
						test.add(rec);
					}
				}
				for (int i = divIdx; i < keys.size(); i++) {
					List<String[]> recs = multimap.get(keys.get(i));
					for (String[] rec : recs) {
						train.add(rec);
					}
				}
			}
			
			Dataset trainDs = new Dataset(originalDataset.getColumnIds(), train, new File(originalDataset.getDatasetFile().getParentFile(), "train.csv"));
			Dataset testDs = new Dataset(originalDataset.getColumnIds(), test, new File(originalDataset.getDatasetFile().getParentFile(), "test.csv"));
			DatasetIO.toCSV(trainDs);
			DatasetIO.toCSV(testDs);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private int getNumClasses() {
		return 2;
	}

	private int extractClass(String[] row) {
		return "true".equals(row[1]) ? 0 : 1;
	}

	public static void main(String[] args) {
		new Separator(new File("f:\\tmp\\imagery_airfield\\runways.csv"), 0.2);
	}

	private static String extractName(String imgPath) {
		String name = new File(imgPath).getName();
		int idx = 0;
		while(idx < name.length() && (Character.isDigit(name.charAt(idx)) || Character.isAlphabetic(name.charAt(idx))))
			idx++;
		return name.substring(0, idx);
	}
	
	
}
