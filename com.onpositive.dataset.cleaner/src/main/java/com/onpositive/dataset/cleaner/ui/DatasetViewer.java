package com.onpositive.dataset.cleaner.ui;

import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.ImageIcon;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JToolBar;
import javax.swing.ListSelectionModel;
import javax.swing.WindowConstants;
import javax.swing.filechooser.FileFilter;

import com.onpositive.dataset.cleaner.io.DatasetIO;
import com.onpositive.dataset.cleaner.model.Dataset;
import com.onpositive.dataset.cleaner.model.DatasetManager;
import com.onpositive.dataset.cleaner.model.DeleteCommand;


@SuppressWarnings("serial")
public class DatasetViewer extends JFrame {
	
	private static final String LAST_FILE= "LAST_FILE";
	private JList<String[]> list;
	private JLabel label;
	private Image currentImage;
	private AbstractAction refreshAction;
	private Dataset dataset;
	private DatasetManager datasetManager;
	private AbstractAction deleteAction;
	private AbstractAction undoAction;
	private AbstractAction redoAction;
	private int selectedIndex = -1;
	private AbstractAction saveAction;
	private boolean modified = false;
	private List<File> toDelete = new ArrayList<File>();

	public DatasetViewer() {
		
		setTitle(getLabel());
		setSize(800, 600);
		setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		setIconImage(new ImageIcon(getClass().getResource("/icons/list_32.png")).getImage());
		
		AbstractAction openAction = new AbstractAction("Choose dataset", new ImageIcon(getClass().getResource("/icons/open_32.png"))) {
			
			@Override
			public void actionPerformed(ActionEvent e) {
				doOpen();
			}
		};
		saveAction = new AbstractAction("Save dataset", new ImageIcon(getClass().getResource("/icons/save_32.png"))) {
			
			@Override
			public void actionPerformed(ActionEvent e) {
				save();
			}
		};
		refreshAction = new AbstractAction("Refresh", new ImageIcon(getClass().getResource("/icons/refresh_32.png"))) {
			
			@Override
			public void actionPerformed(ActionEvent e) {
				doRefresh();
			}
		};
		AbstractAction exitAction = new AbstractAction("Exit", new ImageIcon(getClass().getResource("/icons/exit_32.png"))) {
			
			@Override
			public void actionPerformed(ActionEvent e) {
				System.exit(0);
			}
		};
		deleteAction = new AbstractAction("Delete", new ImageIcon(getClass().getResource("/icons/delete_32.png"))) {
			
			@Override
			public void actionPerformed(ActionEvent e) {
				deleteSelected();
			}
		};
		undoAction = new AbstractAction("Undo", new ImageIcon(getClass().getResource("/icons/undo_32.png"))) {
			
			@Override
			public void actionPerformed(ActionEvent e) {
				undo();
			}
		};
		redoAction = new AbstractAction("Redo", new ImageIcon(getClass().getResource("/icons/redo_32.png"))) {
			
			@Override
			public void actionPerformed(ActionEvent e) {
				redo();
			}
		};
		
		deleteAction.setEnabled(false);
		undoAction.setEnabled(false);
		redoAction.setEnabled(false);
		refreshAction.setEnabled(false);
		saveAction.setEnabled(false);
		
		JToolBar toolBar = new JToolBar("Main");
		toolBar.add(openAction);
		toolBar.add(saveAction);
		toolBar.add(deleteAction);
		toolBar.add(undoAction);
		toolBar.add(redoAction);
		toolBar.add(refreshAction);
		toolBar.add(exitAction);
		
		Container contentPane = getContentPane();
		contentPane.add(toolBar, "North");
		
		list = new JList<>(); //data has type Object[]
		list.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
		list.setLayoutOrientation(JList.VERTICAL);
		list.setVisibleRowCount(-1);
		list.setCellRenderer(new SimpleListCellRenderer());
		list.addListSelectionListener(event -> {
			doSelectRecord(list.getSelectedIndex());
		});
		JScrollPane listScroller = new JScrollPane(list);
		contentPane.add(listScroller,"West");
		list.addKeyListener(new KeyAdapter() {
			
			@Override
			public void keyReleased(KeyEvent e) {
				if(e.getKeyCode() == KeyEvent.VK_DELETE && deleteAction.isEnabled()) {
					deleteSelected();
				} else if(e.getKeyCode() == KeyEvent.VK_Z && e.isControlDown() && undoAction.isEnabled()) {
					undo();
				} else if(e.getKeyCode() == KeyEvent.VK_Y && e.isControlDown() && redoAction.isEnabled()) {
					redo();
				} else if(e.getKeyCode() == KeyEvent.VK_S && e.isControlDown() && saveAction.isEnabled()) {
					save();
				} else if(e.getKeyCode() == KeyEvent.VK_O && e.isControlDown()) {
					doOpen();
				} else {
					super.keyReleased(e);
				}
			}
			
		});
		
		label = new JLabel() {
			private static final long serialVersionUID = 1L;

			@Override
		    public Dimension getPreferredSize(){
				if (currentImage != null) {
					return new Dimension(currentImage.getWidth(null), currentImage.getHeight(null));
				}
				return super.getPreferredSize();
		    }
		};
		
		JScrollPane jsp = new JScrollPane(label);
		contentPane.add(jsp);
		
		String lastFile = Preferences.userNodeForPackage(this.getClass()).get(LAST_FILE, null);
		if (lastFile != null && new File(lastFile).isDirectory()) {
			doSelectInput(new File(lastFile));
		}
		
		
		
	}

	protected String getLabel() {
		return "Dataset Viewer/Editor";
	}
	
	protected void undo() {
		datasetManager.undo();
		modified = true;
		updateAndReSelect();
	}
	
	protected void redo() {
		datasetManager.redo();
		modified = true;
		updateAndReSelect();
	}
	
	protected void save() {
		File datasetFile = dataset.getDatasetFile();
		if (datasetFile.exists()) {
			int dialogResult = JOptionPane.showConfirmDialog(this,
					"File " + datasetFile + " already exists. Overwrite?", "File already exists",
					JOptionPane.YES_NO_OPTION);
			if (dialogResult != JOptionPane.YES_OPTION) {
				return;
			}
		}
		try {
			DatasetIO.toCSV(dataset);
		} catch (IOException e) {
			JOptionPane.showMessageDialog(this, "Error saving " + datasetFile.getAbsolutePath() + ": " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
		}
		for (File file : toDelete) {
			file.delete();
		}
		modified = false;
		updateTitle();	
	}

	protected void deleteSelected() {
		toDelete.add(dataset.getImageFile(selectedIndex));
		datasetManager.executeCommand(new DeleteCommand(dataset, selectedIndex));
		modified = true;
		updateAndReSelect();
	}
	
	private void updateAndReSelect() {
		List<String[]> rows = dataset.getRows();
		list.setListData(rows.toArray(new String[0][]));
		if (rows.size() > selectedIndex) {
			list.setSelectedIndex(selectedIndex);
		} else {
			list.setSelectedIndex(rows.size() - 1);
		}
		updateTitle();	
	}

	private void updateTitle() {
		String title = getLabel();
		if (dataset != null) {
			title += " - ";
			if (modified) {
				title += "*";
			} 
			title += dataset.getDatasetFile().getName();
		}
		setTitle(title);
	}

	private void doSelectRecord(int selectedIndex) {
		if (selectedIndex < 0) {
			return;
		}
		this.selectedIndex = selectedIndex;
		File imageFile = dataset.getImageFile(selectedIndex);
		Image previewImage;
		if (imageFile != null && imageFile.exists()) {
			try {
				previewImage = ImageIO.read(imageFile);
				if (previewImage != null && label.getWidth() > 0) {
					previewImage = downscale((BufferedImage) previewImage, label.getSize());
				}
				if (previewImage != null) {
					label.setIcon(new ImageIcon(previewImage));
				}
				currentImage = previewImage;
			} catch (IOException e) {
				e.printStackTrace();
				previewImage = null;
			}
		}
		refreshActionEnablement();
	}
	
	protected Image downscale(BufferedImage image, Dimension newBounds) {
		int width = image.getWidth();
		int height = image.getHeight();
		if (width <= newBounds.width && height <= newBounds.height) {
			return image;
		} else {
			double xRatio = width *  1.0 / newBounds.width;
			double yRatio = height *  1.0 / newBounds.height;
			double ratio = Math.max(xRatio, yRatio);
			int newWidth = (int)  Math.round(width / ratio);
			int newHeight = (int) Math.round(height / ratio);
			BufferedImage resized = new BufferedImage(newWidth, newHeight, image.getType());
			Graphics2D g = resized.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
			    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.drawImage(image, 0, 0, newWidth, newHeight, 0, 0, image.getWidth(),
			    image.getHeight(), null);
			g.dispose();
			return resized;
		}
	}

	public static void main(String[] args) {
		JFrame frame = new DatasetViewer();
		frame.setVisible(true);
	}
	
	protected void doOpen() {
		Preferences prefs = Preferences.userNodeForPackage(this.getClass());
		JFileChooser chooser = new JFileChooser();
		chooser.setFileFilter(new FileFilter() {

			   public String getDescription() {
			       return "Comma-Separated Values (*.csv)";
			   }

			   public boolean accept(File f) {
			       if (f.isDirectory()) {
			           return true;
			       } else {
			           String filename = f.getName().toLowerCase();
			           return filename.endsWith(".csv");
			       }
			   }
			});
		chooser.setCurrentDirectory(new File(prefs.get(LAST_FILE, ".")));

		int r = chooser.showOpenDialog(this);
		if (r == JFileChooser.APPROVE_OPTION) {
			File selectedFile = chooser.getSelectedFile();
			if (selectedFile.isFile()) {
				prefs.put(LAST_FILE, selectedFile.getAbsolutePath());
				doSelectInput(selectedFile);
				selectedIndex = -1;
			}
		}
	}

	private void doSelectInput(File selectedInput) {
		refreshAction.setEnabled(true);
		saveAction.setEnabled(true);
		Dataset dataset = null;
		try {
			dataset = DatasetIO.fromCSV(selectedInput);
		} catch (IOException e) {
			JOptionPane.showMessageDialog(this, "Error reading " + selectedInput.getAbsolutePath(), "Error reading dataset", JOptionPane.ERROR_MESSAGE);
		}
		if (dataset != null) {
			this.dataset = dataset;
			datasetManager = new DatasetManager(dataset);
			list.setListData(dataset.getRows().toArray(new String[0][]));
		} 
		label.setIcon(null);
		refreshActionEnablement();
	}
	
	private void refreshActionEnablement() {
		refreshAction.setEnabled(dataset != null);
		if (datasetManager == null) {
			deleteAction.setEnabled(false);
			undoAction.setEnabled(false);
			redoAction.setEnabled(false);
		}
		deleteAction.setEnabled(selectedIndex > -1);
		undoAction.setEnabled(datasetManager.canUndo());
		redoAction.setEnabled(datasetManager.canRedo());
	}

	protected void doRefresh() {
		if (dataset != null) {
			doSelectInput(dataset.getDatasetFile());
			if (list.getModel().getSize() > selectedIndex) {
				list.setSelectedIndex(selectedIndex);
			} else {
				selectedIndex = -1;
			}
		}
	}

}
