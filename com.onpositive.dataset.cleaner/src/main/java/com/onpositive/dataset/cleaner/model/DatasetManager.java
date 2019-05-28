package com.onpositive.dataset.cleaner.model;

public class DatasetManager {
	
	private Dataset dataset;
	private SizedStack<ICommand> commandStack = new SizedStack<>(100);
	private SizedStack<ICommand> redoStack = new SizedStack<>(100);

	public DatasetManager(Dataset dataset) {
		this.dataset = dataset;
	}
	
	public void executeCommand(ICommand command) {
		command.execute();
		commandStack.push(command);
		redoStack.clear();
	}
	
	public boolean undo() {
		if (commandStack.size() == 0) {
			return false;
		}
		ICommand cmd = commandStack.pop();
		cmd.undo();
		redoStack.push(cmd);
		return true;
	}
	
	public boolean redo() {
		if (redoStack.size() == 0) {
			return false;
		}
		ICommand cmd = redoStack.pop();
		cmd.execute();
		commandStack.push(cmd);
		return true;
	}

	public Dataset getDataset() {
		return dataset;
	}

	public boolean canUndo() {
		return commandStack.size() > 0;
	}

	public boolean canRedo() {
		return redoStack.size() > 0;
	}

}
