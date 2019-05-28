package com.onpositive.dataset.cleaner.model;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class Dataset {
	
	protected String[] columnIds;
	protected List<String[]> rows;
	protected File datasetFile;
	
	protected int imageColIdx = -1;
	
	public Dataset(String[] columnIds, List<String[]> rows, File datasetFile) {
		super();
		this.columnIds = columnIds;
		this.datasetFile = datasetFile;
		imageColIdx = tryGetImageCol(rows);
		int compareIdx = imageColIdx >= 0 ? imageColIdx : 0;
		Collections.sort(rows, new Comparator<String[]>() {

			@Override
			public int compare(String[] s1, String[] s2) {
				if (s1 != null && s2 != null && s1.length > compareIdx && s2.length > compareIdx) {
					return s1[compareIdx].compareTo(s2[compareIdx]);
				}
				return 0;
			}
		});
		this.rows = rows;
		
	}

	private int tryGetImageCol(List<String[]> rows) {
		for (String[] row : rows) {
			for (int i = 0; i < row.length; i++) {
				File curFile = new File(datasetFile.getParentFile(), row[i]);
				if (curFile.isFile()) {
					try {
						String contentType = Files.probeContentType(curFile.toPath());
						if (contentType.startsWith("image/")) {
							return i;
						}
					} catch (IOException e) {
						// Ignore and silently fail
					}
				}
			}
		}
		return -1;
	}
	
	public File getImageFile(int rowIdx) {
		if (imageColIdx >= 0) {
			String[] row = rows.get(rowIdx);
			File curFile = new File(datasetFile.getParentFile(), row[imageColIdx]);
			if (curFile.isFile()) {
				return curFile;
			}
		}
		return null;		
	}

	@Override
	public String toString() {
		return "Dataset " + datasetFile + " [" + Arrays.toString(columnIds) + "]";
	}

	public String[] getColumnIds() {
		return columnIds;
	}

	public void setColumnIds(String[] columnIds) {
		this.columnIds = columnIds;
	}

	public List<String[]> getRows() {
		return rows;
	}

	public void setRows(List<String[]> rows) {
		this.rows = rows;
	}

	public int getImageColIdx() {
		return imageColIdx;
	}

	public void setImageColIdx(int imageColIdx) {
		this.imageColIdx = imageColIdx;
	}

	public String[] remove(int index) {
		return rows.remove(index);
	}

	public void add(int index, String[] element) {
		rows.add(index, element);
	}

	public boolean add(String[] e) {
		return rows.add(e);
	}

	public File getDatasetFile() {
		return datasetFile;
	}

}
