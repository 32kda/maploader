package com.onpositive.dataset.cleaner.model;


public class DeleteCommand implements ICommand {
	
	private Dataset dataset;
	private int index;
	private String[] row;

	public DeleteCommand(Dataset dataset, int index) {
		this.dataset = dataset;
		this.index = index;
	}

	@Override
	public void execute() {
		row = dataset.remove(index);
	}

	@Override
	public void undo() {
		dataset.add(index,row);
	}

}
