package com.onpositive.dataset.cleaner.io;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import com.onpositive.dataset.cleaner.model.Dataset;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;

public class DatasetIO {

	public static Dataset fromCSV(File csvFile) throws IOException {
		try (CSVReader reader = new CSVReader(new BufferedReader(new FileReader(csvFile)))) {
			// Read CSV line by line and use the string array as you want
			List<String[]> allRows = reader.readAll();
			if (allRows.size() > 0) {
				return new Dataset(allRows.get(0), allRows.subList(1, allRows.size()), csvFile);	
			}
		} catch (IOException e) {
			throw e;
		}
		return null;
	}

	public static void toCSV(Dataset dataset) throws IOException {
		try (CSVWriter writer = new CSVWriter(new BufferedWriter(new FileWriter(dataset.getDatasetFile())))) {
			writer.writeNext(dataset.getColumnIds());
			writer.writeAll(dataset.getRows());
		} catch (IOException e) {
			throw e;
		}
	}
}
