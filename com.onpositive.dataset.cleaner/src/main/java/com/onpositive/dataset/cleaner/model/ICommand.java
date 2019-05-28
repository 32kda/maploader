package com.onpositive.dataset.cleaner.model;

public interface ICommand {
	public void execute();
	public void undo();
}
