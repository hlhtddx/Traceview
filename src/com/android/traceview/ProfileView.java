package com.android.traceview;

import java.util.ArrayList;
import java.util.Observable;
import java.util.Observer;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeViewerListener;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeExpansionEvent;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.FontMetrics;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.swt.widgets.TreeItem;

public class ProfileView extends Composite implements Observer {
	private TreeViewer mTreeViewer;
	private Text mSearchBox;
	private SelectionController mSelectionController;
	private ProfileProvider mProfileProvider;
	private Color mColorNoMatch;
	private Color mColorMatch;
	private MethodData mCurrentHighlightedMethod;
	private MethodHandler mMethodHandler;

	public ProfileView(Composite parent, TraceReader reader,
			SelectionController selectController) {
		super(parent, 0);
		setLayout(new GridLayout(1, false));
		this.mSelectionController = selectController;
		this.mSelectionController.addObserver(this);

		this.mTreeViewer = new TreeViewer(this, 2);
		this.mTreeViewer.setUseHashlookup(true);
		this.mProfileProvider = reader.getProfileProvider();
		this.mProfileProvider.setTreeViewer(this.mTreeViewer);
		SelectionAdapter listener = this.mProfileProvider.getColumnListener();
		final Tree tree = this.mTreeViewer.getTree();
		tree.setHeaderVisible(true);
		tree.setLayoutData(new GridData(1808));

		String[] columnNames = this.mProfileProvider.getColumnNames();
		int[] columnWidths = this.mProfileProvider.getColumnWidths();
		int[] columnAlignments = this.mProfileProvider.getColumnAlignments();
		for (int ii = 0; ii < columnWidths.length; ii++) {
			TreeColumn column = new TreeColumn(tree, 16384);
			column.setText(columnNames[ii]);
			column.setWidth(columnWidths[ii]);
			column.setMoveable(true);
			column.addSelectionListener(listener);
			column.setAlignment(columnAlignments[ii]);
		}

		tree.addListener(41, new Listener() {
			public void handleEvent(Event event) {
				int fontHeight = event.gc.getFontMetrics().getHeight();
				event.height = fontHeight;
			}

		});
		this.mTreeViewer.setContentProvider(this.mProfileProvider);
		this.mTreeViewer.setLabelProvider(this.mProfileProvider
				.getLabelProvider());
		this.mTreeViewer.setInput(this.mProfileProvider.getRoot());

		Composite composite = new Composite(this, 0);
		composite.setLayout(new GridLayout(2, false));
		composite.setLayoutData(new GridData(768));

		Label label = new Label(composite, 0);
		label.setText("Find:");

		this.mSearchBox = new Text(composite, 2048);
		this.mSearchBox.setLayoutData(new GridData(768));

		Display display = getDisplay();
		this.mColorNoMatch = new Color(display, 255, 200, 200);
		this.mColorMatch = this.mSearchBox.getBackground();

		this.mSearchBox.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent ev) {
				String query = ProfileView.this.mSearchBox.getText();
				if (query.length() == 0)
					return;
				ProfileView.this.findName(query);

			}

		});
		this.mSearchBox.addKeyListener(new KeyAdapter() {
			public void keyPressed(KeyEvent event) {
				if (event.keyCode == 27) {
					ProfileView.this.mSearchBox.setText("");
				} else if (event.keyCode == 13) {
					String query = ProfileView.this.mSearchBox.getText();
					if (query.length() == 0)
						return;
					ProfileView.this.findNextName(query);
				}

			}

		});
		tree.addKeyListener(new KeyAdapter() {
			public void keyPressed(KeyEvent event) {
				if (event.keyCode == 27) {
					ProfileView.this.mSearchBox.setText("");
				} else if (event.keyCode == 8) {
					String text = ProfileView.this.mSearchBox.getText();
					int len = text.length();
					String chopped;
					if (len <= 1) {
						chopped = "";
					} else
						chopped = text.substring(0, len - 1);
					ProfileView.this.mSearchBox.setText(chopped);
				} else if (event.keyCode == 13) {
					String query = ProfileView.this.mSearchBox.getText();
					if (query.length() == 0)
						return;
					ProfileView.this.findNextName(query);
				} else {
					String str = String.valueOf(event.character);
					ProfileView.this.mSearchBox.append(str);
				}
				event.doit = false;

			}

		});
		this.mTreeViewer
				.addSelectionChangedListener(new ISelectionChangedListener() {
					public void selectionChanged(SelectionChangedEvent ev) {
						ISelection sel = ev.getSelection();
						if (sel.isEmpty())
							return;
						if ((sel instanceof IStructuredSelection)) {
							IStructuredSelection selection = (IStructuredSelection) sel;
							Object element = selection.getFirstElement();
							if (element == null)
								return;
							if ((element instanceof MethodData)) {
								MethodData md = (MethodData) element;
								ProfileView.this.highlightMethod(md, true);
							}
							if ((element instanceof ProfileData)) {
								MethodData md = ((ProfileData) element)
										.getMethodData();

								ProfileView.this.highlightMethod(md, true);
							}

						}

					}

				});
		this.mTreeViewer.addTreeListener(new ITreeViewerListener() {
			public void treeExpanded(TreeExpansionEvent event) {
				Object element = event.getElement();
				if ((element instanceof MethodData)) {
					MethodData md = (MethodData) element;
					ProfileView.this.expandNode(md);
				}
			}

			public void treeCollapsed(TreeExpansionEvent event) {
			}
		});
		tree.addListener(3, new Listener() {
			public void handleEvent(Event event) {
				Point point = new Point(event.x, event.y);
				TreeItem treeItem = tree.getItem(point);
				MethodData md = ProfileView.this.mProfileProvider
						.findMatchingTreeItem(treeItem);
				if (md == null)
					return;
				ArrayList<Selection> selections = new ArrayList<Selection>();
				selections.add(Selection.highlight("MethodData", md));
				ProfileView.this.mSelectionController.change(selections,
						"ProfileView");

				if ((ProfileView.this.mMethodHandler != null)
						&& ((event.stateMask & SWT.MOD1) != 0)) {
					ProfileView.this.mMethodHandler.handleMethod(md);
				}
			}
		});
	}

	public void setMethodHandler(MethodHandler handler) {
		this.mMethodHandler = handler;
	}

	private void findName(String query) {
		MethodData md = this.mProfileProvider.findMatchingName(query);
		selectMethod(md);
	}

	private void findNextName(String query) {
		MethodData md = this.mProfileProvider.findNextMatchingName(query);
		selectMethod(md);
	}

	private void selectMethod(MethodData md) {
		if (md == null) {
			this.mSearchBox.setBackground(this.mColorNoMatch);
			return;
		}
		this.mSearchBox.setBackground(this.mColorMatch);
		highlightMethod(md, false);
	}

	public void update(Observable objservable, Object arg) {
		if (arg == "ProfileView") {
			return;
		}

		ArrayList<Selection> selections = this.mSelectionController
				.getSelections();
		for (Selection selection : selections) {
			Selection.Action action = selection.getAction();
			if (action == Selection.Action.Highlight) {
				String name = selection.getName();
				if (name == "MethodData") {
					MethodData md = (MethodData) selection.getValue();
					highlightMethod(md, true);
					return;
				}
				if (name == "Call") {
					Call call = (Call) selection.getValue();
					MethodData md = call.getMethodData();
					highlightMethod(md, true);
					return;
				}
			}
		}
	}

	private void highlightMethod(MethodData md, boolean clearSearch) {
		if (md == null) {
			return;
		}
		if (md == this.mCurrentHighlightedMethod)
			return;
		if (clearSearch) {
			this.mSearchBox.setText("");
			this.mSearchBox.setBackground(this.mColorMatch);
		}
		this.mCurrentHighlightedMethod = md;
		this.mTreeViewer.collapseAll();

		expandNode(md);
		StructuredSelection sel = new StructuredSelection(md);
		this.mTreeViewer.setSelection(sel, true);
		Tree tree = this.mTreeViewer.getTree();
		TreeItem[] items = tree.getSelection();
		if (items.length != 0) {
			tree.setTopItem(items[0]);

			tree.showItem(items[0]);
		}
	}

	private void expandNode(MethodData md) {
		ProfileNode[] nodes = md.getProfileNodes();
		this.mTreeViewer.setExpandedState(md, true);

		if (nodes != null) {
			for (ProfileNode node : nodes) {
				if (!node.isRecursive()) {
					this.mTreeViewer.setExpandedState(node, true);
				}
			}
		}
	}

	public static abstract interface MethodHandler {
		public abstract void handleMethod(MethodData paramMethodData);
	}
}

/*
 * Location:
 * /Users/frank/Applications/android-sdk-macosx/tools/lib/traceview.jar
 * !/com/android/traceview/ProfileView.class Java compiler version: 6 (50.0)
 * JD-Core Version: 0.7.1
 */