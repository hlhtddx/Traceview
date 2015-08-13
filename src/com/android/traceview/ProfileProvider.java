package com.android.traceview;

import com.android.utils.SdkUtils;
import java.io.InputStream;
import java.util.Arrays;
import org.eclipse.jface.viewers.IColorProvider;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.swt.widgets.TreeItem;

class ProfileProvider implements ITreeContentProvider {
	private MethodData[] mRoots;
	private SelectionAdapter mListener;
	private TreeViewer mTreeViewer;
	private TraceReader mReader;
	private Image mSortUp;
	private Image mSortDown;
	private String[] mColumnNames = { "Name", "Incl Cpu Time %",
			"Incl Cpu Time", "Excl Cpu Time %", "Excl Cpu Time",
			"Incl Real Time %", "Incl Real Time", "Excl Real Time %",
			"Excl Real Time", "Calls+Recur\nCalls/Total", "Cpu Time/Call",
			"Real Time/Call" };

	private int[] mColumnWidths = { 370, 100, 100, 100, 100, 100, 100, 100,
			100, 100, 100, 100 };

	private int[] mColumnAlignments = { 16384, 131072, 131072, 131072, 131072,
			131072, 131072, 131072, 131072, 16777216, 131072, 131072 };

	private static final int COL_NAME = 0;

	private static final int COL_INCLUSIVE_CPU_TIME_PER = 1;

	private static final int COL_INCLUSIVE_CPU_TIME = 2;
	private static final int COL_EXCLUSIVE_CPU_TIME_PER = 3;
	private static final int COL_EXCLUSIVE_CPU_TIME = 4;
	private static final int COL_INCLUSIVE_REAL_TIME_PER = 5;
	private static final int COL_INCLUSIVE_REAL_TIME = 6;
	private static final int COL_EXCLUSIVE_REAL_TIME_PER = 7;
	private static final int COL_EXCLUSIVE_REAL_TIME = 8;
	private static final int COL_CALLS = 9;
	private static final int COL_CPU_TIME_PER_CALL = 10;
	private static final int COL_REAL_TIME_PER_CALL = 11;
	private long mTotalCpuTime;
	private long mTotalRealTime;
	private int mPrevMatchIndex = -1;

	public ProfileProvider(TraceReader reader) {
		this.mRoots = reader.getMethods();
		this.mReader = reader;
		this.mTotalCpuTime = reader.getTotalCpuTime();
		this.mTotalRealTime = reader.getTotalRealTime();
		Display display = Display.getCurrent();
		InputStream in = getClass().getClassLoader().getResourceAsStream(
				"icons/sort_up.png");

		this.mSortUp = new Image(display, in);
		in = getClass().getClassLoader().getResourceAsStream(
				"icons/sort_down.png");

		this.mSortDown = new Image(display, in);
	}

	private MethodData doMatchName(String name, int startIndex) {
		boolean hasUpper = SdkUtils.hasUpperCaseCharacter(name);
		for (int ii = startIndex; ii < this.mRoots.length; ii++) {
			MethodData md = this.mRoots[ii];
			String fullName = md.getName();

			if (!hasUpper)
				fullName = fullName.toLowerCase();
			if (fullName.indexOf(name) != -1) {
				this.mPrevMatchIndex = ii;
				return md;
			}
		}
		this.mPrevMatchIndex = -1;
		return null;
	}

	public MethodData findMatchingName(String name) {
		return doMatchName(name, 0);
	}

	public MethodData findNextMatchingName(String name) {
		return doMatchName(name, this.mPrevMatchIndex + 1);
	}

	public MethodData findMatchingTreeItem(TreeItem item) {
		if (item == null)
			return null;
		String text = item.getText();
		if (!Character.isDigit(text.charAt(0)))
			return null;
		int spaceIndex = text.indexOf(' ');
		String numstr = text.substring(0, spaceIndex);
		int rank = Integer.valueOf(numstr).intValue();
		for (MethodData md : this.mRoots) {
			if (md.getRank() == rank)
				return md;
		}
		return null;
	}

	public void setTreeViewer(TreeViewer treeViewer) {
		this.mTreeViewer = treeViewer;
	}

	public String[] getColumnNames() {
		return this.mColumnNames;
	}

	public int[] getColumnWidths() {
		int[] widths = Arrays.copyOf(this.mColumnWidths,
				this.mColumnWidths.length);
		if (!this.mReader.haveCpuTime()) {
			widths[4] = 0;
			widths[3] = 0;
			widths[2] = 0;
			widths[1] = 0;
			widths[10] = 0;
		}
		if (!this.mReader.haveRealTime()) {
			widths[8] = 0;
			widths[7] = 0;
			widths[6] = 0;
			widths[5] = 0;
			widths[11] = 0;
		}
		return widths;
	}

	public int[] getColumnAlignments() {
		return this.mColumnAlignments;
	}

	public Object[] getChildren(Object element) {
		if ((element instanceof MethodData)) {
			MethodData md = (MethodData) element;
			return md.getProfileNodes();
		}
		if ((element instanceof ProfileNode)) {
			ProfileNode pn = (ProfileNode) element;
			return pn.getChildren();
		}
		return new Object[0];
	}

	public Object getParent(Object element) {
		return null;
	}

	public boolean hasChildren(Object element) {
		if ((element instanceof MethodData))
			return true;
		if ((element instanceof ProfileNode))
			return true;
		return false;
	}

	public Object[] getElements(Object element) {
		return this.mRoots;
	}

	public void dispose() {
	}

	public void inputChanged(Viewer arg0, Object arg1, Object arg2) {
	}

	public Object getRoot() {
		return "root";
	}

	public SelectionAdapter getColumnListener() {
		if (this.mListener == null)
			this.mListener = new ColumnListener();
		return this.mListener;
	}

	public LabelProvider getLabelProvider() {
		return new ProfileLabelProvider();
	}

	class ProfileLabelProvider extends LabelProvider implements
			ITableLabelProvider, IColorProvider {
		Color colorRed;
		Color colorParentsBack;
		Color colorChildrenBack;
		TraceUnits traceUnits;

		public ProfileLabelProvider() {
			Display display = Display.getCurrent();
			this.colorRed = display.getSystemColor(3);
			this.colorParentsBack = new Color(display, 230, 230, 255);
			this.colorChildrenBack = new Color(display, 255, 255, 210);
			this.traceUnits = ProfileProvider.this.mReader.getTraceUnits();
		}

		public String getColumnText(Object element, int col) {
			if ((element instanceof MethodData)) {
				MethodData md = (MethodData) element;
				if (col == 0)
					return md.getProfileName();
				if (col == 4) {
					double val = md.getElapsedExclusiveCpuTime();
					val = this.traceUnits.getScaledValue(val);
					return String.format("%.3f",
							new Object[] { Double.valueOf(val) });
				}
				if (col == 3) {
					double val = md.getElapsedExclusiveCpuTime();
					double per = val * 100.0D
							/ ProfileProvider.this.mTotalCpuTime;
					return String.format("%.1f%%",
							new Object[] { Double.valueOf(per) });
				}
				if (col == 2) {
					double val = md.getElapsedInclusiveCpuTime();
					val = this.traceUnits.getScaledValue(val);
					return String.format("%.3f",
							new Object[] { Double.valueOf(val) });
				}
				if (col == 1) {
					double val = md.getElapsedInclusiveCpuTime();
					double per = val * 100.0D
							/ ProfileProvider.this.mTotalCpuTime;
					return String.format("%.1f%%",
							new Object[] { Double.valueOf(per) });
				}
				if (col == 8) {
					double val = md.getElapsedExclusiveRealTime();
					val = this.traceUnits.getScaledValue(val);
					return String.format("%.3f",
							new Object[] { Double.valueOf(val) });
				}
				if (col == 7) {
					double val = md.getElapsedExclusiveRealTime();
					double per = val * 100.0D
							/ ProfileProvider.this.mTotalRealTime;
					return String.format("%.1f%%",
							new Object[] { Double.valueOf(per) });
				}
				if (col == 6) {
					double val = md.getElapsedInclusiveRealTime();
					val = this.traceUnits.getScaledValue(val);
					return String.format("%.3f",
							new Object[] { Double.valueOf(val) });
				}
				if (col == 5) {
					double val = md.getElapsedInclusiveRealTime();
					double per = val * 100.0D
							/ ProfileProvider.this.mTotalRealTime;
					return String.format("%.1f%%",
							new Object[] { Double.valueOf(per) });
				}
				if (col == 9)
					return md.getCalls();
				if (col == 10) {
					int numCalls = md.getTotalCalls();
					double val = md.getElapsedInclusiveCpuTime();
					val /= numCalls;
					val = this.traceUnits.getScaledValue(val);
					return String.format("%.3f",
							new Object[] { Double.valueOf(val) });
				}
				if (col == 11) {
					int numCalls = md.getTotalCalls();
					double val = md.getElapsedInclusiveRealTime();
					val /= numCalls;
					val = this.traceUnits.getScaledValue(val);
					return String.format("%.3f",
							new Object[] { Double.valueOf(val) });
				}
			} else {
				if ((element instanceof ProfileSelf)) {
					ProfileSelf ps = (ProfileSelf) element;
					if (col == 0)
						return ps.getProfileName();
					if (col == 2) {
						double val = ps.getElapsedInclusiveCpuTime();
						val = this.traceUnits.getScaledValue(val);
						return String.format("%.3f",
								new Object[] { Double.valueOf(val) });
					}
					if (col == 1) {
						double val = ps.getElapsedInclusiveCpuTime();
						MethodData context = ps.getContext();
						double total = context.getElapsedInclusiveCpuTime();
						double per = val * 100.0D / total;
						return String.format("%.1f%%",
								new Object[] { Double.valueOf(per) });
					}
					if (col == 6) {
						double val = ps.getElapsedInclusiveRealTime();
						val = this.traceUnits.getScaledValue(val);
						return String.format("%.3f",
								new Object[] { Double.valueOf(val) });
					}
					if (col == 5) {
						double val = ps.getElapsedInclusiveRealTime();
						MethodData context = ps.getContext();
						double total = context.getElapsedInclusiveRealTime();
						double per = val * 100.0D / total;
						return String.format("%.1f%%",
								new Object[] { Double.valueOf(per) });
					}
					return "";
				}
				if ((element instanceof ProfileData)) {
					ProfileData pd = (ProfileData) element;
					if (col == 0)
						return pd.getProfileName();
					if (col == 2) {
						double val = pd.getElapsedInclusiveCpuTime();
						val = this.traceUnits.getScaledValue(val);
						return String.format("%.3f",
								new Object[] { Double.valueOf(val) });
					}
					if (col == 1) {
						double val = pd.getElapsedInclusiveCpuTime();
						MethodData context = pd.getContext();
						double total = context.getElapsedInclusiveCpuTime();
						double per = val * 100.0D / total;
						return String.format("%.1f%%",
								new Object[] { Double.valueOf(per) });
					}
					if (col == 6) {
						double val = pd.getElapsedInclusiveRealTime();
						val = this.traceUnits.getScaledValue(val);
						return String.format("%.3f",
								new Object[] { Double.valueOf(val) });
					}
					if (col == 5) {
						double val = pd.getElapsedInclusiveRealTime();
						MethodData context = pd.getContext();
						double total = context.getElapsedInclusiveRealTime();
						double per = val * 100.0D / total;
						return String.format("%.1f%%",
								new Object[] { Double.valueOf(per) });
					}
					if (col == 9)
						return pd.getNumCalls();
					return "";
				}
				if ((element instanceof ProfileNode)) {
					ProfileNode pn = (ProfileNode) element;
					if (col == 0)
						return pn.getLabel();
					return "";
				}
			}
			return "col" + col;
		}

		public Image getColumnImage(Object element, int col) {
			if (col != 0)
				return null;
			if ((element instanceof MethodData)) {
				MethodData md = (MethodData) element;
				return md.getImage();
			}
			if ((element instanceof ProfileData)) {
				ProfileData pd = (ProfileData) element;
				MethodData md = pd.getMethodData();
				return md.getImage();
			}
			return null;
		}

		public Color getForeground(Object element) {
			return null;
		}

		public Color getBackground(Object element) {
			if ((element instanceof ProfileData)) {
				ProfileData pd = (ProfileData) element;
				if (pd.isParent())
					return this.colorParentsBack;
				return this.colorChildrenBack;
			}
			if ((element instanceof ProfileNode)) {
				ProfileNode pn = (ProfileNode) element;
				if (pn.isParent())
					return this.colorParentsBack;
				return this.colorChildrenBack;
			}
			return null;
		}
	}

	class ColumnListener extends SelectionAdapter {
		MethodData.Sorter sorter = new MethodData.Sorter();

		ColumnListener() {
		}

		public void widgetSelected(SelectionEvent event) {
			TreeColumn column = (TreeColumn) event.widget;
			String name = column.getText();
			Tree tree = column.getParent();
			tree.setRedraw(false);
			TreeColumn[] columns = tree.getColumns();
			for (TreeColumn col : columns) {
				col.setImage(null);
			}
			if (name == ProfileProvider.this.mColumnNames[0]) {
				this.sorter.setColumn(MethodData.Sorter.Column.BY_NAME);
				Arrays.sort(ProfileProvider.this.mRoots, this.sorter);
			} else if (name == ProfileProvider.this.mColumnNames[4]) {
				this.sorter
						.setColumn(MethodData.Sorter.Column.BY_EXCLUSIVE_CPU_TIME);
				Arrays.sort(ProfileProvider.this.mRoots, this.sorter);
			} else if (name == ProfileProvider.this.mColumnNames[3]) {
				this.sorter
						.setColumn(MethodData.Sorter.Column.BY_EXCLUSIVE_CPU_TIME);
				Arrays.sort(ProfileProvider.this.mRoots, this.sorter);
			} else if (name == ProfileProvider.this.mColumnNames[2]) {
				this.sorter
						.setColumn(MethodData.Sorter.Column.BY_INCLUSIVE_CPU_TIME);
				Arrays.sort(ProfileProvider.this.mRoots, this.sorter);
			} else if (name == ProfileProvider.this.mColumnNames[1]) {
				this.sorter
						.setColumn(MethodData.Sorter.Column.BY_INCLUSIVE_CPU_TIME);
				Arrays.sort(ProfileProvider.this.mRoots, this.sorter);
			} else if (name == ProfileProvider.this.mColumnNames[8]) {
				this.sorter
						.setColumn(MethodData.Sorter.Column.BY_EXCLUSIVE_REAL_TIME);
				Arrays.sort(ProfileProvider.this.mRoots, this.sorter);
			} else if (name == ProfileProvider.this.mColumnNames[7]) {
				this.sorter
						.setColumn(MethodData.Sorter.Column.BY_EXCLUSIVE_REAL_TIME);
				Arrays.sort(ProfileProvider.this.mRoots, this.sorter);
			} else if (name == ProfileProvider.this.mColumnNames[6]) {
				this.sorter
						.setColumn(MethodData.Sorter.Column.BY_INCLUSIVE_REAL_TIME);
				Arrays.sort(ProfileProvider.this.mRoots, this.sorter);
			} else if (name == ProfileProvider.this.mColumnNames[5]) {
				this.sorter
						.setColumn(MethodData.Sorter.Column.BY_INCLUSIVE_REAL_TIME);
				Arrays.sort(ProfileProvider.this.mRoots, this.sorter);
			} else if (name == ProfileProvider.this.mColumnNames[9]) {
				this.sorter.setColumn(MethodData.Sorter.Column.BY_CALLS);
				Arrays.sort(ProfileProvider.this.mRoots, this.sorter);
			} else if (name == ProfileProvider.this.mColumnNames[10]) {
				this.sorter
						.setColumn(MethodData.Sorter.Column.BY_CPU_TIME_PER_CALL);
				Arrays.sort(ProfileProvider.this.mRoots, this.sorter);
			} else if (name == ProfileProvider.this.mColumnNames[11]) {
				this.sorter
						.setColumn(MethodData.Sorter.Column.BY_REAL_TIME_PER_CALL);
				Arrays.sort(ProfileProvider.this.mRoots, this.sorter);
			}
			MethodData.Sorter.Direction direction = this.sorter.getDirection();
			if (direction == MethodData.Sorter.Direction.INCREASING) {
				column.setImage(ProfileProvider.this.mSortDown);
			} else
				column.setImage(ProfileProvider.this.mSortUp);
			tree.setRedraw(true);
			ProfileProvider.this.mTreeViewer.refresh();
		}
	}
}

/*
 * Location:
 * /Users/frank/Applications/android-sdk-macosx/tools/lib/traceview.jar
 * !/com/android/traceview/ProfileProvider.class Java compiler version: 6 (50.0)
 * JD-Core Version: 0.7.1
 */