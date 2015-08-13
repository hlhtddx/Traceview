package com.android.traceview;

import java.util.ArrayList;
import java.util.HashMap;

import org.eclipse.jface.resource.FontRegistry;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.ScrollBar;

public class TimeLineView extends Composite implements java.util.Observer {
	private HashMap<String, RowData> mRowByName;
	private RowData[] mRows;
	private Segment[] mSegments;
	private HashMap<Integer, String> mThreadLabels;
	private Timescale mTimescale;
	private Surface mSurface;
	private RowLabels mLabels;
	private SashForm mSashForm;
	private int mScrollOffsetY;
	public static final int PixelsPerTick = 50;

	public static abstract interface Block {
		public abstract String getName();

		public abstract MethodData getMethodData();

		public abstract long getStartTime();

		public abstract long getEndTime();

		public abstract Color getColor();

		public abstract double addWeight(int paramInt1, int paramInt2,
				double paramDouble);

		public abstract void clearWeight();

		public abstract long getExclusiveCpuTime();

		public abstract long getInclusiveCpuTime();

		public abstract long getExclusiveRealTime();

		public abstract long getInclusiveRealTime();

		public abstract boolean isContextSwitch();

		public abstract boolean isIgnoredBlock();

		public abstract Block getParentBlock();
	}

	private TickScaler mScaleInfo = new TickScaler(0.0D, 0.0D, 0, 50);

	private static final int LeftMargin = 10;

	private static final int RightMargin = 60;

	private Color mColorBlack;

	private Color mColorGray;

	private Color mColorDarkGray;

	private Color mColorForeground;

	private Color mColorRowBack;

	private Color mColorZoomSelection;
	private FontRegistry mFontRegistry;
	private static final int rowHeight = 20;
	private static final int rowYMargin = 12;
	private static final int rowYMarginHalf = 6;
	private static final int rowYSpace = 32;
	private static final int majorTickLength = 8;
	private static final int minorTickLength = 4;
	private static final int timeLineOffsetY = 58;
	private static final int tickToFontSpacing = 2;
	private static final int topMargin = 90;

	private int mMouseRow = -1;

	private int mNumRows;

	private int mStartRow;
	private int mEndRow;
	private TraceUnits mUnits;
	private String mClockSource;
	private boolean mHaveCpuTime;
	private boolean mHaveRealTime;
	private int mSmallFontWidth;
	private int mSmallFontHeight;
	private SelectionController mSelectionController;
	private MethodData mHighlightMethodData;
	private Call mHighlightCall;
	private static final int MinInclusiveRange = 3;
	private boolean mSetFonts = false;

	public static abstract interface Row {
		public abstract int getId();

		public abstract String getName();
	}

	public static class Record {
		public Record(TimeLineView.Row row, TimeLineView.Block block) {
			this.row = row;
			this.block = block;
		}

		TimeLineView.Row row;
		TimeLineView.Block block;
	}

	public TimeLineView(Composite parent, TraceReader reader,
			SelectionController selectionController) {
		super(parent, 0);
		this.mRowByName = new HashMap<String, RowData>();
		this.mSelectionController = selectionController;
		selectionController.addObserver(this);
		this.mUnits = reader.getTraceUnits();
		this.mClockSource = reader.getClockSource();
		this.mHaveCpuTime = reader.haveCpuTime();
		this.mHaveRealTime = reader.haveRealTime();
		this.mThreadLabels = reader.getThreadLabels();

		Display display = getDisplay();
		this.mColorGray = display.getSystemColor(15);
		this.mColorDarkGray = display.getSystemColor(16);
		this.mColorBlack = display.getSystemColor(2);

		this.mColorForeground = display.getSystemColor(2);
		this.mColorRowBack = new Color(display, 240, 240, 255);
		this.mColorZoomSelection = new Color(display, 230, 230, 230);

		this.mFontRegistry = new FontRegistry(display);
		this.mFontRegistry.put("small", new FontData[] { new FontData("Arial",
				8, 0) });

		this.mFontRegistry.put("courier8", new FontData[] { new FontData(
				"Courier New", 8, 1) });

		this.mFontRegistry.put("medium", new FontData[] { new FontData(
				"Courier New", 10, 0) });

		Image image = new Image(display,
				new org.eclipse.swt.graphics.Rectangle(100, 100, 100, 100));
		GC gc = new GC(image);
		if (this.mSetFonts) {
			gc.setFont(this.mFontRegistry.get("small"));
		}
		this.mSmallFontWidth = gc.getFontMetrics().getAverageCharWidth();
		this.mSmallFontHeight = gc.getFontMetrics().getHeight();

		image.dispose();
		gc.dispose();

		setLayout(new org.eclipse.swt.layout.FillLayout());

		this.mSashForm = new SashForm(this, 256);
		this.mSashForm.setBackground(this.mColorGray);
		this.mSashForm.SASH_WIDTH = 3;

		Composite composite = new Composite(this.mSashForm, 0);
		org.eclipse.swt.layout.GridLayout layout = new org.eclipse.swt.layout.GridLayout(
				1, true);
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		layout.verticalSpacing = 1;
		composite.setLayout(layout);

		BlankCorner corner = new BlankCorner(composite);
		GridData gridData = new GridData(768);
		gridData.heightHint = 90;
		corner.setLayoutData(gridData);

		this.mLabels = new RowLabels(composite);
		gridData = new GridData(1808);
		this.mLabels.setLayoutData(gridData);

		composite = new Composite(this.mSashForm, 0);
		layout = new org.eclipse.swt.layout.GridLayout(1, true);
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		layout.verticalSpacing = 1;
		composite.setLayout(layout);

		this.mTimescale = new Timescale(composite);
		gridData = new GridData(768);
		gridData.heightHint = 90;
		this.mTimescale.setLayoutData(gridData);

		this.mSurface = new Surface(composite);
		gridData = new GridData(1808);
		this.mSurface.setLayoutData(gridData);
		this.mSashForm.setWeights(new int[] { 1, 5 });

		final ScrollBar vBar = this.mSurface.getVerticalBar();
		vBar.addListener(13, new org.eclipse.swt.widgets.Listener() {
			public void handleEvent(Event e) {
				TimeLineView.this.mScrollOffsetY = vBar.getSelection();
				Point dim = TimeLineView.this.mSurface.getSize();
				int newScrollOffsetY = TimeLineView.this
						.computeVisibleRows(dim.y);
				if (newScrollOffsetY != TimeLineView.this.mScrollOffsetY) {
					TimeLineView.this.mScrollOffsetY = newScrollOffsetY;
					vBar.setSelection(newScrollOffsetY);
				}
				TimeLineView.this.mLabels.redraw();
				TimeLineView.this.mSurface.redraw();
			}

		});
		final ScrollBar hBar = this.mSurface.getHorizontalBar();
		hBar.addListener(13, new org.eclipse.swt.widgets.Listener() {
			public void handleEvent(Event e) {
				TimeLineView.this.mSurface.setScaleFromHorizontalScrollBar(hBar
						.getSelection());
				TimeLineView.this.mSurface.redraw();
			}

		});
		this.mSurface.addListener(11, new org.eclipse.swt.widgets.Listener() {
			public void handleEvent(Event e) {
				Point dim = TimeLineView.this.mSurface.getSize();

				if (dim.y >= TimeLineView.this.mNumRows * 32) {
					vBar.setVisible(false);
				} else {
					vBar.setVisible(true);
				}
				int newScrollOffsetY = TimeLineView.this
						.computeVisibleRows(dim.y);
				if (newScrollOffsetY != TimeLineView.this.mScrollOffsetY) {
					TimeLineView.this.mScrollOffsetY = newScrollOffsetY;
					vBar.setSelection(newScrollOffsetY);
				}

				int spaceNeeded = TimeLineView.this.mNumRows * 32;
				vBar.setMaximum(spaceNeeded);
				vBar.setThumb(dim.y);

				TimeLineView.this.mLabels.redraw();
				TimeLineView.this.mSurface.redraw();
			}

		});
		this.mSurface
				.addMouseListener(new org.eclipse.swt.events.MouseAdapter() {
					public void mouseUp(MouseEvent me) {
						TimeLineView.this.mSurface.mouseUp(me);
					}

					public void mouseDown(MouseEvent me) {
						TimeLineView.this.mSurface.mouseDown(me);
					}

					public void mouseDoubleClick(MouseEvent me) {
						TimeLineView.this.mSurface.mouseDoubleClick(me);
					}

				});
		this.mSurface
				.addMouseMoveListener(new org.eclipse.swt.events.MouseMoveListener() {
					public void mouseMove(MouseEvent me) {
						TimeLineView.this.mSurface.mouseMove(me);
					}

				});
		this.mSurface
				.addMouseWheelListener(new org.eclipse.swt.events.MouseWheelListener() {
					public void mouseScrolled(MouseEvent me) {
						TimeLineView.this.mSurface.mouseScrolled(me);
					}

				});
		this.mTimescale
				.addMouseListener(new org.eclipse.swt.events.MouseAdapter() {
					public void mouseUp(MouseEvent me) {
						TimeLineView.this.mTimescale.mouseUp(me);
					}

					public void mouseDown(MouseEvent me) {
						TimeLineView.this.mTimescale.mouseDown(me);
					}

					public void mouseDoubleClick(MouseEvent me) {
						TimeLineView.this.mTimescale.mouseDoubleClick(me);
					}

				});
		this.mTimescale
				.addMouseMoveListener(new org.eclipse.swt.events.MouseMoveListener() {
					public void mouseMove(MouseEvent me) {
						TimeLineView.this.mTimescale.mouseMove(me);
					}

				});
		this.mLabels
				.addMouseMoveListener(new org.eclipse.swt.events.MouseMoveListener() {
					public void mouseMove(MouseEvent me) {
						TimeLineView.this.mLabels.mouseMove(me);
					}

				});
		setData(reader.getThreadTimeRecords());
	}

	public void update(java.util.Observable objservable, Object arg) {
		if (arg == "TimeLineView") {
			return;
		}
		boolean foundHighlight = false;

		ArrayList<Selection> selections = this.mSelectionController
				.getSelections();
		for (Selection selection : selections) {
			Selection.Action action = selection.getAction();
			if (action == Selection.Action.Highlight) {
				String name = selection.getName();

				if (name == "MethodData") {
					foundHighlight = true;
					this.mHighlightMethodData = ((MethodData) selection
							.getValue());

					this.mHighlightCall = null;
					startHighlighting();
				} else if (name == "Call") {
					foundHighlight = true;
					this.mHighlightCall = ((Call) selection.getValue());

					this.mHighlightMethodData = null;
					startHighlighting();
				}
			}
		}
		if (!foundHighlight)
			this.mSurface.clearHighlights();
	}

	public void setData(ArrayList<Record> records) {
		if (records == null) {
			records = new ArrayList<Record>();
		}

		java.util.Collections.sort(records,
				new java.util.Comparator<TimeLineView.Record>() {
					public int compare(TimeLineView.Record r1,
							TimeLineView.Record r2) {
						long start1 = r1.block.getStartTime();
						long start2 = r2.block.getStartTime();
						if (start1 > start2)
							return 1;
						if (start1 < start2) {
							return -1;
						}

						long end1 = r1.block.getEndTime();
						long end2 = r2.block.getEndTime();
						if (end1 > end2)
							return -1;
						if (end1 < end2) {
							return 1;
						}
						return 0;
					}

				});
		ArrayList<Segment> segmentList = new ArrayList<Segment>();

		double minVal = 0.0D;
		if (records.size() > 0) {
			minVal = ((Record) records.get(0)).block.getStartTime();
		}

		double maxVal = 0.0D;
		for (Record rec : records) {
			Row row = rec.row;
			Block block = rec.block;
			if (!block.isIgnoredBlock()) {

				String rowName = row.getName();
				RowData rd = (RowData) this.mRowByName.get(rowName);
				if (rd == null) {
					rd = new RowData(row);
					this.mRowByName.put(rowName, rd);
				}
				long blockStartTime = block.getStartTime();
				long blockEndTime = block.getEndTime();
				if (blockEndTime > rd.mEndTime) {
					long start = Math.max(blockStartTime, rd.mEndTime);
					rd.mElapsed = blockEndTime - start;
					rd.mEndTime = blockEndTime;
				}
				if (blockEndTime > maxVal) {
					maxVal = blockEndTime;
				}

				Block top = rd.top();
				if (top == null) {
					rd.push(block);
				} else {
					long topStartTime = top.getStartTime();
					long topEndTime = top.getEndTime();
					if (topEndTime >= blockStartTime) {
						if (topStartTime < blockStartTime) {
							Segment segment = new Segment(rd, top,
									topStartTime, blockStartTime);

							segmentList.add(segment);
						}

						if (topEndTime == blockStartTime)
							rd.pop();
						rd.push(block);
					} else {
						popFrames(rd, top, blockStartTime, segmentList);
						rd.push(block);
					}
				}
			}
		}
		for (RowData rd : this.mRowByName.values()) {
			Block top = rd.top();
			popFrames(rd, top, 2147483647L, segmentList);
		}

		this.mSurface.setRange(minVal, maxVal);
		this.mSurface.setLimitRange(minVal, maxVal);

		java.util.Collection<RowData> rv = this.mRowByName.values();
		this.mRows = ((RowData[]) rv.toArray(new RowData[rv.size()]));
		java.util.Arrays.sort(this.mRows,
				new java.util.Comparator<TimeLineView.RowData>() {
					public int compare(TimeLineView.RowData rd1,
							TimeLineView.RowData rd2) {
						return (int) (rd2.mElapsed - rd1.mElapsed);
					}
				});

		for (int ii = 0; ii < this.mRows.length; ii++) {
			this.mRows[ii].mRank = ii;
		}

		this.mNumRows = 0;
		for (int ii = 0; ii < this.mRows.length; ii++) {
			if (this.mRows[ii].mElapsed == 0L)
				break;
			this.mNumRows += 1;
		}

		this.mSegments = ((Segment[]) segmentList
				.toArray(new Segment[segmentList.size()]));
		java.util.Arrays.sort(this.mSegments,
				new java.util.Comparator<TimeLineView.Segment>() {
					public int compare(TimeLineView.Segment bd1,
							TimeLineView.Segment bd2) {
						TimeLineView.RowData rd1 = bd1.mRowData;
						TimeLineView.RowData rd2 = bd2.mRowData;
						int diff = rd1.mRank - rd2.mRank;
						if (diff == 0) {
							long timeDiff = bd1.mStartTime - bd2.mStartTime;
							if (timeDiff == 0L)
								timeDiff = bd1.mEndTime - bd2.mEndTime;
							return (int) timeDiff;
						}
						return diff;
					}
				});
	}

	private static void popFrames(RowData rd, Block top, long startTime,
			ArrayList<Segment> segmentList) {
		long topEndTime = top.getEndTime();
		long lastEndTime = top.getStartTime();
		while (topEndTime <= startTime) {
			if (topEndTime > lastEndTime) {
				Segment segment = new Segment(rd, top, lastEndTime, topEndTime);
				segmentList.add(segment);
				lastEndTime = topEndTime;
			}
			rd.pop();
			top = rd.top();
			if (top == null)
				return;
			topEndTime = top.getEndTime();
		}

		if (lastEndTime < startTime) {
			Segment bd = new Segment(rd, top, lastEndTime, startTime);
			segmentList.add(bd);
		}
	}

	private class RowLabels extends Canvas {
		private static final int labelMarginX = 2;

		public RowLabels(Composite parent) {
			super(parent, 262144);
			addPaintListener(new PaintListener() {
				public void paintControl(PaintEvent pe) {
					TimeLineView.RowLabels.this.draw(pe.display, pe.gc);
				}
			});
		}

		private void mouseMove(MouseEvent me) {
			int rownum = (me.y + TimeLineView.this.mScrollOffsetY) / 32;
			if (TimeLineView.this.mMouseRow != rownum) {
				TimeLineView.this.mMouseRow = rownum;
				redraw();
				TimeLineView.this.mSurface.redraw();
			}
		}

		private void draw(Display display, GC gc) {
			if (TimeLineView.this.mSegments.length == 0) {

				return;
			}
			Point dim = getSize();

			Image image = new Image(display, getBounds());

			GC gcImage = new GC(image);
			if (TimeLineView.this.mSetFonts) {
				gcImage.setFont(TimeLineView.this.mFontRegistry.get("medium"));
			}
			if (TimeLineView.this.mNumRows > 2) {
				gcImage.setBackground(TimeLineView.this.mColorRowBack);
				for (int ii = 1; ii < TimeLineView.this.mNumRows; ii += 2) {
					TimeLineView.RowData rd = TimeLineView.this.mRows[ii];
					int y1 = rd.mRank * 32 - TimeLineView.this.mScrollOffsetY;
					gcImage.fillRectangle(0, y1, dim.x, 32);
				}
			}

			int offsetY = 6 - TimeLineView.this.mScrollOffsetY;
			for (int ii = TimeLineView.this.mStartRow; ii <= TimeLineView.this.mEndRow; ii++) {
				TimeLineView.RowData rd = TimeLineView.this.mRows[ii];
				int y1 = rd.mRank * 32 + offsetY;
				Point extent = gcImage.stringExtent(rd.mName);
				int x1 = dim.x - extent.x - 2;
				gcImage.drawString(rd.mName, x1, y1, true);
			}

			if ((TimeLineView.this.mMouseRow >= TimeLineView.this.mStartRow)
					&& (TimeLineView.this.mMouseRow <= TimeLineView.this.mEndRow)) {
				gcImage.setForeground(TimeLineView.this.mColorGray);
				int y1 = TimeLineView.this.mMouseRow * 32
						- TimeLineView.this.mScrollOffsetY;
				gcImage.drawRectangle(0, y1, dim.x, 32);
			}

			gc.drawImage(image, 0, 0);

			image.dispose();
			gcImage.dispose();
		}
	}

	private class BlankCorner extends Canvas {
		public BlankCorner(Composite parent) {
			super(parent, 0);
			addPaintListener(new PaintListener() {
				public void paintControl(PaintEvent pe) {
					TimeLineView.BlankCorner.this.draw(pe.display, pe.gc);
				}
			});
		}

		private void draw(Display display, GC gc) {
			Image image = new Image(display, getBounds());
			gc.drawImage(image, 0, 0);

			image.dispose();
		}
	}

	private class Timescale extends Canvas {
		private Point mMouse = new Point(10, 0);
		private Cursor mZoomCursor;
		private String mMethodName = null;
		private Color mMethodColor = null;

		private String mDetails;

		private int mMethodStartY;
		private int mDetailsStartY;
		private int mMarkStartX;
		private int mMarkEndX;
		private static final int METHOD_BLOCK_MARGIN = 10;

		public Timescale(Composite parent) {
			super(parent, 0);
			Display display = getDisplay();
			this.mZoomCursor = new Cursor(display, 9);
			setCursor(this.mZoomCursor);
			this.mMethodStartY = (TimeLineView.this.mSmallFontHeight + 1);
			this.mDetailsStartY = (this.mMethodStartY
					+ TimeLineView.this.mSmallFontHeight + 1);
			addPaintListener(new PaintListener() {
				public void paintControl(PaintEvent pe) {
					TimeLineView.Timescale.this.draw(pe.display, pe.gc);
				}
			});
		}

		public void setVbarPosition(int x) {
			this.mMouse.x = x;
		}

		public void setMarkStart(int x) {
			this.mMarkStartX = x;
		}

		public void setMarkEnd(int x) {
			this.mMarkEndX = x;
		}

		public void setMethodName(String name) {
			this.mMethodName = name;
		}

		public void setMethodColor(Color color) {
			this.mMethodColor = color;
		}

		public void setDetails(String details) {
			this.mDetails = details;
		}

		private void mouseMove(MouseEvent me) {
			me.y = -1;
			TimeLineView.this.mSurface.mouseMove(me);
		}

		private void mouseDown(MouseEvent me) {
			TimeLineView.this.mSurface.startScaling(me.x);
			TimeLineView.this.mSurface.redraw();
		}

		private void mouseUp(MouseEvent me) {
			TimeLineView.this.mSurface.stopScaling(me.x);
		}

		private void mouseDoubleClick(MouseEvent me) {
			TimeLineView.this.mSurface.resetScale();
			TimeLineView.this.mSurface.redraw();
		}

		private void draw(Display display, GC gc) {
			Point dim = getSize();

			Image image = new Image(display, getBounds());

			GC gcImage = new GC(image);
			if (TimeLineView.this.mSetFonts) {
				gcImage.setFont(TimeLineView.this.mFontRegistry.get("medium"));
			}
			if (TimeLineView.this.mSurface.drawingSelection()) {
				drawSelection(display, gcImage);
			}

			drawTicks(display, gcImage);

			gcImage.setForeground(TimeLineView.this.mColorDarkGray);
			gcImage.drawLine(this.mMouse.x, 58, this.mMouse.x, dim.y);

			drawTickLegend(display, gcImage);

			drawMethod(display, gcImage);

			drawDetails(display, gcImage);

			gc.drawImage(image, 0, 0);

			image.dispose();
			gcImage.dispose();
		}

		private void drawSelection(Display display, GC gc) {
			Point dim = getSize();
			gc.setForeground(TimeLineView.this.mColorGray);
			gc.drawLine(this.mMarkStartX, 58, this.mMarkStartX, dim.y);
			gc.setBackground(TimeLineView.this.mColorZoomSelection);
			int width;
			int x;
			if (this.mMarkStartX < this.mMarkEndX) {
				x = this.mMarkStartX;
				width = this.mMarkEndX - this.mMarkStartX;
			} else {
				x = this.mMarkEndX;
				width = this.mMarkStartX - this.mMarkEndX;
			}
			if (width > 1) {
				gc.fillRectangle(x, 58, width, dim.y);
			}
		}

		private void drawTickLegend(Display display, GC gc) {
			int mouseX = this.mMouse.x - 10;
			double mouseXval = TimeLineView.this.mScaleInfo
					.pixelToValue(mouseX);
			String info = TimeLineView.this.mUnits.labelledString(mouseXval);
			gc.setForeground(TimeLineView.this.mColorForeground);
			gc.drawString(info, 12, 1, true);

			double maxVal = TimeLineView.this.mScaleInfo.getMaxVal();
			info = TimeLineView.this.mUnits.labelledString(maxVal);
			if (TimeLineView.this.mClockSource != null) {
				info = String.format(" max %s (%s)", new Object[] { info,
						TimeLineView.this.mClockSource });
			} else {
				info = String.format(" max %s ", new Object[] { info });
			}
			Point extent = gc.stringExtent(info);
			Point dim = getSize();
			int x1 = dim.x - 60 - extent.x;
			gc.drawString(info, x1, 1, true);
		}

		private void drawMethod(Display display, GC gc) {
			if (this.mMethodName == null) {
				return;
			}

			int x1 = 10;
			int y1 = this.mMethodStartY;
			gc.setBackground(this.mMethodColor);
			int width = 2 * TimeLineView.this.mSmallFontWidth;
			gc.fillRectangle(x1, y1, width, TimeLineView.this.mSmallFontHeight);
			x1 += width + 10;
			gc.drawString(this.mMethodName, x1, y1, true);
		}

		private void drawDetails(Display display, GC gc) {
			if (this.mDetails == null) {
				return;
			}

			int x1 = 10 + 2 * TimeLineView.this.mSmallFontWidth + 10;
			int y1 = this.mDetailsStartY;
			gc.drawString(this.mDetails, x1, y1, true);
		}

		private void drawTicks(Display display, GC gc) {
			Point dim = getSize();
			int y2 = 66;
			int y3 = 62;
			int y4 = y2 + 2;
			gc.setForeground(TimeLineView.this.mColorForeground);
			gc.drawLine(10, 58, dim.x - 60, 58);

			double minVal = TimeLineView.this.mScaleInfo.getMinVal();
			double maxVal = TimeLineView.this.mScaleInfo.getMaxVal();
			double minMajorTick = TimeLineView.this.mScaleInfo
					.getMinMajorTick();
			double tickIncrement = TimeLineView.this.mScaleInfo
					.getTickIncrement();
			double minorTickIncrement = tickIncrement / 5.0D;
			double pixelsPerRange = TimeLineView.this.mScaleInfo
					.getPixelsPerRange();

			if (minVal < minMajorTick) {
				gc.setForeground(TimeLineView.this.mColorGray);
				double xMinor = minMajorTick;
				for (int ii = 1; ii <= 4; ii++) {
					xMinor -= minorTickIncrement;
					if (xMinor < minVal)
						break;
					int x1 = 10 + (int) (0.5D + (xMinor - minVal)
							* pixelsPerRange);

					gc.drawLine(x1, 58, x1, y3);
				}
			}

			if (tickIncrement <= 10.0D) {

				return;
			}
			for (double x = minMajorTick; x <= maxVal; x += tickIncrement) {
				int x1 = 10 + (int) (0.5D + (x - minVal) * pixelsPerRange);

				gc.setForeground(TimeLineView.this.mColorForeground);
				gc.drawLine(x1, 58, x1, y2);
				if (x > maxVal) {
					break;
				}

				String tickString = TimeLineView.this.mUnits.valueOf(x);
				gc.drawString(tickString, x1, y4, true);

				gc.setForeground(TimeLineView.this.mColorGray);
				double xMinor = x;
				for (int ii = 1; ii <= 4; ii++) {
					xMinor += minorTickIncrement;
					if (xMinor > maxVal)
						break;
					x1 = 10 + (int) (0.5D + (xMinor - minVal) * pixelsPerRange);

					gc.drawLine(x1, 58, x1, y3);
				}
			}
		}
	}

	private static enum GraphicsState {
		Normal, Marking, Scaling, Animating, Scrolling;

		private GraphicsState() {
		}
	}

	private class Surface extends Canvas {
		private static final int TotalXMargin = 70;

		public Surface(Composite parent) {
			super(parent, 262912);
			Display display = getDisplay();
			this.mNormalCursor = new Cursor(display, 2);
			this.mIncreasingCursor = new Cursor(display, 12);
			this.mDecreasingCursor = new Cursor(display, 13);

			initZoomFractionsWithExp();

			addPaintListener(new PaintListener() {
				public void paintControl(PaintEvent pe) {
					TimeLineView.Surface.this.draw(pe.display, pe.gc);
				}

			});
			this.mZoomAnimator = new Runnable() {
				public void run() {
					TimeLineView.Surface.this.animateZoom();
				}

			};
			this.mHighlightAnimator = new Runnable() {
				public void run() {
					TimeLineView.Surface.this.animateHighlight();
				}
			};
		}

		private void initZoomFractionsWithExp() {
			this.mZoomFractions = new double[8];
			int next = 0;
			for (int ii = 0; ii < 4; next++) {
				this.mZoomFractions[next] = ((1 << ii) / 16.0D);
				ii++;
			}

			for (int ii = 2; ii < 6; next++) {
				this.mZoomFractions[next] = (((1 << ii) - 1) / (1 << ii));
				ii++;
			}
		}

		private void initZoomFractionsWithSinWave() {
			this.mZoomFractions = new double[8];
			for (int ii = 0; ii < 8; ii++) {
				double offset = 3.141592653589793D * ii / 8.0D;
				this.mZoomFractions[ii] = ((Math
						.sin(4.71238898038469D + offset) + 1.0D) / 2.0D);
			}
		}

		public void setRange(double minVal, double maxVal) {
			this.mMinDataVal = minVal;
			this.mMaxDataVal = maxVal;
			TimeLineView.this.mScaleInfo.setMinVal(minVal);
			TimeLineView.this.mScaleInfo.setMaxVal(maxVal);
		}

		public void setLimitRange(double minVal, double maxVal) {
			this.mLimitMinVal = minVal;
			this.mLimitMaxVal = maxVal;
		}

		public void resetScale() {
			TimeLineView.this.mScaleInfo.setMinVal(this.mLimitMinVal);
			TimeLineView.this.mScaleInfo.setMaxVal(this.mLimitMaxVal);
		}

		public void setScaleFromHorizontalScrollBar(int selection) {
			double minVal = TimeLineView.this.mScaleInfo.getMinVal();
			double maxVal = TimeLineView.this.mScaleInfo.getMaxVal();
			double visibleRange = maxVal - minVal;

			minVal = this.mLimitMinVal + selection;
			maxVal = minVal + visibleRange;
			if (maxVal > this.mLimitMaxVal) {
				maxVal = this.mLimitMaxVal;
				minVal = maxVal - visibleRange;
			}
			TimeLineView.this.mScaleInfo.setMinVal(minVal);
			TimeLineView.this.mScaleInfo.setMaxVal(maxVal);

			this.mGraphicsState = TimeLineView.GraphicsState.Scrolling;
		}

		private void updateHorizontalScrollBar() {
			double minVal = TimeLineView.this.mScaleInfo.getMinVal();
			double maxVal = TimeLineView.this.mScaleInfo.getMaxVal();
			double visibleRange = maxVal - minVal;
			double fullRange = this.mLimitMaxVal - this.mLimitMinVal;

			ScrollBar hBar = getHorizontalBar();
			if (fullRange > visibleRange) {
				hBar.setVisible(true);
				hBar.setMinimum(0);
				hBar.setMaximum((int) Math.ceil(fullRange));
				hBar.setThumb((int) Math.ceil(visibleRange));
				hBar.setSelection((int) Math.floor(minVal - this.mLimitMinVal));
			} else {
				hBar.setVisible(false);
			}
		}

		private void draw(Display display, GC gc) {
			if (TimeLineView.this.mSegments.length == 0) {

				return;
			}

			Image image = new Image(display, getBounds());

			GC gcImage = new GC(image);
			if (TimeLineView.this.mSetFonts) {
				gcImage.setFont(TimeLineView.this.mFontRegistry.get("small"));
			}

			if (this.mGraphicsState == TimeLineView.GraphicsState.Scaling) {
				double diff = this.mMouse.x - this.mMouseMarkStartX;
				if (diff > 0.0D) {
					double newMinVal = this.mScaleMinVal - diff
							/ this.mScalePixelsPerRange;
					if (newMinVal < this.mLimitMinVal)
						newMinVal = this.mLimitMinVal;
					TimeLineView.this.mScaleInfo.setMinVal(newMinVal);

				} else if (diff < 0.0D) {
					double newMaxVal = this.mScaleMaxVal - diff
							/ this.mScalePixelsPerRange;
					if (newMaxVal > this.mLimitMaxVal)
						newMaxVal = this.mLimitMaxVal;
					TimeLineView.this.mScaleInfo.setMaxVal(newMaxVal);
				}
			}

			Point dim = getSize();
			if ((TimeLineView.this.mStartRow != this.mCachedStartRow)
					|| (TimeLineView.this.mEndRow != this.mCachedEndRow)
					|| (TimeLineView.this.mScaleInfo.getMinVal() != this.mCachedMinVal)
					|| (TimeLineView.this.mScaleInfo.getMaxVal() != this.mCachedMaxVal)) {

				this.mCachedStartRow = TimeLineView.this.mStartRow;
				this.mCachedEndRow = TimeLineView.this.mEndRow;
				int xdim = dim.x - 70;
				TimeLineView.this.mScaleInfo.setNumPixels(xdim);
				boolean forceEndPoints = (this.mGraphicsState == TimeLineView.GraphicsState.Scaling)
						|| (this.mGraphicsState == TimeLineView.GraphicsState.Animating)
						|| (this.mGraphicsState == TimeLineView.GraphicsState.Scrolling);

				TimeLineView.this.mScaleInfo.computeTicks(forceEndPoints);
				this.mCachedMinVal = TimeLineView.this.mScaleInfo.getMinVal();
				this.mCachedMaxVal = TimeLineView.this.mScaleInfo.getMaxVal();
				if (this.mLimitMinVal > TimeLineView.this.mScaleInfo
						.getMinVal())
					this.mLimitMinVal = TimeLineView.this.mScaleInfo
							.getMinVal();
				if (this.mLimitMaxVal < TimeLineView.this.mScaleInfo
						.getMaxVal()) {
					this.mLimitMaxVal = TimeLineView.this.mScaleInfo
							.getMaxVal();
				}

				computeStrips();

				updateHorizontalScrollBar();
			}

			if (TimeLineView.this.mNumRows > 2) {
				gcImage.setBackground(TimeLineView.this.mColorRowBack);
				for (int ii = 1; ii < TimeLineView.this.mNumRows; ii += 2) {
					TimeLineView.RowData rd = TimeLineView.this.mRows[ii];
					int y1 = rd.mRank * 32 - TimeLineView.this.mScrollOffsetY;
					gcImage.fillRectangle(0, y1, dim.x, 32);
				}
			}

			if (drawingSelection()) {
				drawSelection(display, gcImage);
			}

			String blockName = null;
			Color blockColor = null;
			String blockDetails = null;

			if (this.mDebug) {
				double pixelsPerRange = TimeLineView.this.mScaleInfo
						.getPixelsPerRange();
				System.out
						.printf("dim.x %d pixels %d minVal %f, maxVal %f ppr %f rpp %f\n",
								new Object[] {
										Integer.valueOf(dim.x),
										Integer.valueOf(dim.x - 70),
										Double.valueOf(TimeLineView.this.mScaleInfo
												.getMinVal()),
										Double.valueOf(TimeLineView.this.mScaleInfo
												.getMaxVal()),
										Double.valueOf(pixelsPerRange),
										Double.valueOf(1.0D / pixelsPerRange) });
			}

			TimeLineView.Block selectBlock = null;
			for (TimeLineView.Strip strip : this.mStripList) {
				if (strip.mColor != null) {

					gcImage.setBackground(strip.mColor);
					gcImage.fillRectangle(strip.mX, strip.mY
							- TimeLineView.this.mScrollOffsetY, strip.mWidth,
							strip.mHeight);

					if (TimeLineView.this.mMouseRow == strip.mRowData.mRank) {
						if ((this.mMouse.x >= strip.mX)
								&& (this.mMouse.x < strip.mX + strip.mWidth)) {
							TimeLineView.Block block = strip.mSegment.mBlock;
							blockName = block.getName();
							blockColor = strip.mColor;
							if (TimeLineView.this.mHaveCpuTime) {
								if (TimeLineView.this.mHaveRealTime) {
									blockDetails = String
											.format("excl cpu %s, incl cpu %s, excl real %s, incl real %s",
													new Object[] {
															TimeLineView.this.mUnits
																	.labelledString(block
																			.getExclusiveCpuTime()),
															TimeLineView.this.mUnits
																	.labelledString(block
																			.getInclusiveCpuTime()),
															TimeLineView.this.mUnits
																	.labelledString(block
																			.getExclusiveRealTime()),
															TimeLineView.this.mUnits
																	.labelledString(block
																			.getInclusiveRealTime()) });

								} else {

									blockDetails = String
											.format("excl cpu %s, incl cpu %s",
													new Object[] {
															TimeLineView.this.mUnits
																	.labelledString(block
																			.getExclusiveCpuTime()),
															TimeLineView.this.mUnits
																	.labelledString(block
																			.getInclusiveCpuTime()) });
								}

							} else {
								blockDetails = String
										.format("excl real %s, incl real %s",
												new Object[] {
														TimeLineView.this.mUnits
																.labelledString(block
																		.getExclusiveRealTime()),
														TimeLineView.this.mUnits
																.labelledString(block
																		.getInclusiveRealTime()) });
							}
						}

						if ((this.mMouseSelect.x >= strip.mX)
								&& (this.mMouseSelect.x < strip.mX
										+ strip.mWidth)) {
							selectBlock = strip.mSegment.mBlock;
						}
					}
				}
			}
			this.mMouseSelect.x = 0;
			this.mMouseSelect.y = 0;

			if (selectBlock != null) {
				ArrayList<Selection> selections = new ArrayList<Selection>();

				TimeLineView.RowData rd = TimeLineView.this.mRows[TimeLineView.this.mMouseRow];
				selections.add(Selection.highlight("Thread", rd.mName));
				selections.add(Selection.highlight("Call", selectBlock));

				int mouseX = this.mMouse.x - 10;
				double mouseXval = TimeLineView.this.mScaleInfo
						.pixelToValue(mouseX);
				selections.add(Selection.highlight("Time",
						Double.valueOf(mouseXval)));

				TimeLineView.this.mSelectionController.change(selections,
						"TimeLineView");
				TimeLineView.this.mHighlightMethodData = null;
				TimeLineView.this.mHighlightCall = ((Call) selectBlock);
				TimeLineView.this.startHighlighting();
			}

			if ((TimeLineView.this.mMouseRow >= 0)
					&& (TimeLineView.this.mMouseRow < TimeLineView.this.mNumRows)
					&& (this.mHighlightStep == 0)) {
				gcImage.setForeground(TimeLineView.this.mColorGray);
				int y1 = TimeLineView.this.mMouseRow * 32
						- TimeLineView.this.mScrollOffsetY;
				gcImage.drawLine(0, y1, dim.x, y1);
				gcImage.drawLine(0, y1 + 32, dim.x, y1 + 32);
			}

			drawHighlights(gcImage, dim);

			gcImage.setForeground(TimeLineView.this.mColorDarkGray);
			int lineEnd = Math.min(dim.y, TimeLineView.this.mNumRows * 32);
			gcImage.drawLine(this.mMouse.x, 0, this.mMouse.x, lineEnd);

			if (blockName != null) {
				TimeLineView.this.mTimescale.setMethodName(blockName);
				TimeLineView.this.mTimescale.setMethodColor(blockColor);
				TimeLineView.this.mTimescale.setDetails(blockDetails);
				this.mShowHighlightName = false;
			} else if (this.mShowHighlightName) {
				MethodData md = TimeLineView.this.mHighlightMethodData;
				if ((md == null) && (TimeLineView.this.mHighlightCall != null))
					md = TimeLineView.this.mHighlightCall.getMethodData();
				if (md == null)
					System.out.printf("null highlight?\n", new Object[0]);
				if (md != null) {
					TimeLineView.this.mTimescale.setMethodName(md
							.getProfileName());
					TimeLineView.this.mTimescale.setMethodColor(md.getColor());
					TimeLineView.this.mTimescale.setDetails(null);
				}
			} else {
				TimeLineView.this.mTimescale.setMethodName(null);
				TimeLineView.this.mTimescale.setMethodColor(null);
				TimeLineView.this.mTimescale.setDetails(null);
			}
			TimeLineView.this.mTimescale.redraw();

			gc.drawImage(image, 0, 0);

			image.dispose();
			gcImage.dispose();
		}

		private void drawHighlights(GC gc, Point dim) {
			int height = this.mHighlightHeight;
			if (height <= 0)
				return;
			for (TimeLineView.Range range : this.mHighlightExclusive) {
				gc.setBackground(range.mColor);
				int xStart = range.mXdim.x;
				int width = range.mXdim.y;
				gc.fillRectangle(xStart, range.mY - height
						- TimeLineView.this.mScrollOffsetY, width, height);
			}

			height--;
			if (height <= 0) {
				height = 1;
			}

			gc.setForeground(TimeLineView.this.mColorDarkGray);
			gc.setBackground(TimeLineView.this.mColorDarkGray);
			for (TimeLineView.Range range : this.mHighlightInclusive) {
				int x1 = range.mXdim.x;
				int x2 = range.mXdim.y;
				boolean drawLeftEnd = false;
				boolean drawRightEnd = false;
				if (x1 >= 10) {
					drawLeftEnd = true;
				} else
					x1 = 10;
				if (x2 >= 10) {
					drawRightEnd = true;
				} else
					x2 = dim.x - 60;
				int y1 = range.mY + 20 + 2 - TimeLineView.this.mScrollOffsetY;

				if (x2 - x1 < 3) {
					int width = x2 - x1;
					if (width < 2)
						width = 2;
					gc.fillRectangle(x1, y1, width, height);
				} else {
					if (drawLeftEnd) {
						if (drawRightEnd) {
							int[] points = { x1, y1, x1, y1 + height, x2,
									y1 + height, x2, y1 };

							gc.drawPolyline(points);
						} else {
							int[] points = { x1, y1, x1, y1 + height, x2,
									y1 + height };

							gc.drawPolyline(points);
						}
					} else if (drawRightEnd) {
						int[] points = { x1, y1 + height, x2, y1 + height, x2,
								y1 };

						gc.drawPolyline(points);
					} else {
						int[] points = { x1, y1 + height, x2, y1 + height };
						gc.drawPolyline(points);
					}

					if (!drawLeftEnd) {
						int[] points = { x1 + 7, y1 + height - 4, x1,
								y1 + height, x1 + 7, y1 + height + 4 };

						gc.fillPolygon(points);
					}
					if (!drawRightEnd) {
						int[] points = { x2 - 7, y1 + height - 4, x2,
								y1 + height, x2 - 7, y1 + height + 4 };

						gc.fillPolygon(points);
					}
				}
			}
		}

		private boolean drawingSelection() {
			return (this.mGraphicsState == TimeLineView.GraphicsState.Marking)
					|| (this.mGraphicsState == TimeLineView.GraphicsState.Animating);
		}

		private void drawSelection(Display display, GC gc) {
			Point dim = getSize();
			gc.setForeground(TimeLineView.this.mColorGray);
			gc.drawLine(this.mMouseMarkStartX, 0, this.mMouseMarkStartX, dim.y);
			gc.setBackground(TimeLineView.this.mColorZoomSelection);

			int mouseX = this.mGraphicsState == TimeLineView.GraphicsState.Animating ? this.mMouseMarkEndX
					: this.mMouse.x;
			int width;
			int x;
			if (this.mMouseMarkStartX < mouseX) {
				x = this.mMouseMarkStartX;
				width = mouseX - this.mMouseMarkStartX;
			} else {
				x = mouseX;
				width = this.mMouseMarkStartX - mouseX;
			}
			gc.fillRectangle(x, 0, width, dim.y);
		}

		private void computeStrips() {
			double minVal = TimeLineView.this.mScaleInfo.getMinVal();
			double maxVal = TimeLineView.this.mScaleInfo.getMaxVal();

			TimeLineView.Pixel[] pixels = new TimeLineView.Pixel[TimeLineView.this.mNumRows];
			for (int ii = 0; ii < TimeLineView.this.mNumRows; ii++) {
				pixels[ii] = new TimeLineView.Pixel();
			}

			for (int ii = 0; ii < TimeLineView.this.mSegments.length; ii++) {
				TimeLineView.this.mSegments[ii].mBlock.clearWeight();
			}

			this.mStripList.clear();
			this.mHighlightExclusive.clear();
			this.mHighlightInclusive.clear();
			MethodData callMethod = null;
			long callStart = 0L;
			long callEnd = -1L;
			TimeLineView.RowData callRowData = null;
			int prevMethodStart = -1;
			int prevMethodEnd = -1;
			int prevCallStart = -1;
			int prevCallEnd = -1;
			if (TimeLineView.this.mHighlightCall != null) {
				int callPixelStart = -1;
				int callPixelEnd = -1;
				callStart = TimeLineView.this.mHighlightCall.getStartTime();
				callEnd = TimeLineView.this.mHighlightCall.getEndTime();
				callMethod = TimeLineView.this.mHighlightCall.getMethodData();
				if (callStart >= minVal)
					callPixelStart = TimeLineView.this.mScaleInfo
							.valueToPixel(callStart);
				if (callEnd <= maxVal) {
					callPixelEnd = TimeLineView.this.mScaleInfo
							.valueToPixel(callEnd);
				}

				int threadId = TimeLineView.this.mHighlightCall.getThreadId();
				String threadName = (String) TimeLineView.this.mThreadLabels
						.get(Integer.valueOf(threadId));
				callRowData = (TimeLineView.RowData) TimeLineView.this.mRowByName
						.get(threadName);
				int y1 = callRowData.mRank * 32 + 6;
				Color color = callMethod.getColor();
				this.mHighlightInclusive.add(new TimeLineView.Range(
						callPixelStart + 10, callPixelEnd + 10, y1, color));
			}

			for (TimeLineView.Segment segment : TimeLineView.this.mSegments)
				if (segment.mEndTime > minVal) {
					if (segment.mStartTime < maxVal) {

						TimeLineView.Block block = segment.mBlock;

						Color color = block.getColor();
						if (color != null) {

							double recordStart = Math.max(segment.mStartTime,
									minVal);
							double recordEnd = Math.min(segment.mEndTime,
									maxVal);
							if (recordStart != recordEnd) {
								int pixelStart = TimeLineView.this.mScaleInfo
										.valueToPixel(recordStart);
								int pixelEnd = TimeLineView.this.mScaleInfo
										.valueToPixel(recordEnd);
								int width = pixelEnd - pixelStart;
								boolean isContextSwitch = segment.mIsContextSwitch;

								TimeLineView.RowData rd = segment.mRowData;
								MethodData md = block.getMethodData();

								int y1 = rd.mRank * 32 + 6;

								if (rd.mRank > TimeLineView.this.mEndRow) {
									break;
								}

								if (TimeLineView.this.mHighlightMethodData != null) {
									if (TimeLineView.this.mHighlightMethodData == md) {
										if ((prevMethodStart != pixelStart)
												|| (prevMethodEnd != pixelEnd)) {
											prevMethodStart = pixelStart;
											prevMethodEnd = pixelEnd;
											int rangeWidth = width;
											if (rangeWidth == 0)
												rangeWidth = 1;
											this.mHighlightExclusive
													.add(new TimeLineView.Range(
															pixelStart + 10,
															rangeWidth, y1,
															color));

											callStart = block.getStartTime();
											int callPixelStart = -1;
											if (callStart >= minVal)
												callPixelStart = TimeLineView.this.mScaleInfo
														.valueToPixel(callStart);
											int callPixelEnd = -1;
											callEnd = block.getEndTime();
											if (callEnd <= maxVal)
												callPixelEnd = TimeLineView.this.mScaleInfo
														.valueToPixel(callEnd);
											if ((prevCallStart != callPixelStart)
													|| (prevCallEnd != callPixelEnd)) {
												prevCallStart = callPixelStart;
												prevCallEnd = callPixelEnd;
												this.mHighlightInclusive
														.add(new TimeLineView.Range(
																callPixelStart + 10,
																callPixelEnd + 10,
																y1, color));
											}

										}
									} else if (this.mFadeColors) {
										color = md.getFadedColor();
									}
								} else if (TimeLineView.this.mHighlightCall != null) {
									if ((segment.mStartTime >= callStart)
											&& (segment.mEndTime <= callEnd)
											&& (callMethod == md)
											&& (callRowData == rd)) {

										if ((prevMethodStart != pixelStart)
												|| (prevMethodEnd != pixelEnd)) {
											prevMethodStart = pixelStart;
											prevMethodEnd = pixelEnd;
											int rangeWidth = width;
											if (rangeWidth == 0)
												rangeWidth = 1;
											this.mHighlightExclusive
													.add(new TimeLineView.Range(
															pixelStart + 10,
															rangeWidth, y1,
															color));
										}
									} else if (this.mFadeColors) {
										color = md.getFadedColor();
									}
								}

								TimeLineView.Pixel pix = pixels[rd.mRank];
								if (pix.mStart != pixelStart) {
									if (pix.mSegment != null) {
										emitPixelStrip(rd, y1, pix);
									}

									if (width == 0) {

										double weight = computeWeight(
												recordStart, recordEnd,
												isContextSwitch, pixelStart);

										weight = block.addWeight(pixelStart,
												rd.mRank, weight);
										if (weight > pix.mMaxWeight) {
											pix.setFields(pixelStart, weight,
													segment, color, rd);
										}
									} else {
										int x1 = pixelStart + 10;
										TimeLineView.Strip strip = new TimeLineView.Strip(
												x1,
												isContextSwitch ? y1 + 20 - 1
														: y1, width,
												isContextSwitch ? 1 : 20, rd,
												segment, color);

										this.mStripList.add(strip);
									}
								} else {
									double weight = computeWeight(recordStart,
											recordEnd, isContextSwitch,
											pixelStart);

									weight = block.addWeight(pixelStart,
											rd.mRank, weight);
									if (weight > pix.mMaxWeight) {
										pix.setFields(pixelStart, weight,
												segment, color, rd);
									}
									if (width == 1) {
										emitPixelStrip(rd, y1, pix);

										pixelStart++;
										weight = computeWeight(recordStart,
												recordEnd, isContextSwitch,
												pixelStart);

										weight = block.addWeight(pixelStart,
												rd.mRank, weight);
										pix.setFields(pixelStart, weight,
												segment, color, rd);
									} else if (width > 1) {
										emitPixelStrip(rd, y1, pix);

										pixelStart++;
										width--;
										int x1 = pixelStart + 10;
										TimeLineView.Strip strip = new TimeLineView.Strip(
												x1,
												isContextSwitch ? y1 + 20 - 1
														: y1, width,
												isContextSwitch ? 1 : 20, rd,
												segment, color);

										this.mStripList.add(strip);
									}
								}
							}
						}
					}
				}
			for (int ii = 0; ii < TimeLineView.this.mNumRows; ii++) {
				TimeLineView.Pixel pix = pixels[ii];
				if (pix.mSegment != null) {
					TimeLineView.RowData rd = pix.mRowData;
					int y1 = rd.mRank * 32 + 6;

					emitPixelStrip(rd, y1, pix);
				}
			}
		}

		private static final int yMargin = 1;

		private static final int MinZoomPixelMargin = 10;

		private double computeWeight(double start, double end,
				boolean isContextSwitch, int pixel) {
			if (isContextSwitch) {
				return 0.0D;
			}
			double pixelStartFraction = TimeLineView.this.mScaleInfo
					.valueToPixelFraction(start);
			double pixelEndFraction = TimeLineView.this.mScaleInfo
					.valueToPixelFraction(end);
			double leftEndPoint = Math.max(pixelStartFraction, pixel - 0.5D);
			double rightEndPoint = Math.min(pixelEndFraction, pixel + 0.5D);
			double weight = rightEndPoint - leftEndPoint;
			return weight;
		}

		private void emitPixelStrip(TimeLineView.RowData rd, int y,
				TimeLineView.Pixel pixel) {
			if (pixel.mSegment == null) {
				return;
			}
			int x = pixel.mStart + 10;

			int height = (int) (pixel.mMaxWeight * 20.0D * 0.75D);
			if (height < this.mMinStripHeight)
				height = this.mMinStripHeight;
			int remainder = 20 - height;
			if (remainder > 0) {
				TimeLineView.Strip strip = new TimeLineView.Strip(x, y, 1,
						remainder, rd, pixel.mSegment,
						this.mFadeColors ? TimeLineView.this.mColorGray
								: TimeLineView.this.mColorBlack);

				this.mStripList.add(strip);
			}

			TimeLineView.Strip strip = new TimeLineView.Strip(x, y + remainder,
					1, height, rd, pixel.mSegment, pixel.mColor);

			this.mStripList.add(strip);

			pixel.mSegment = null;
			pixel.mMaxWeight = 0.0D;
		}

		private void mouseMove(MouseEvent me) {
			Point dim = TimeLineView.this.mSurface.getSize();
			int x = me.x;
			if (x < 10)
				x = 10;
			if (x > dim.x - 60)
				x = dim.x - 60;
			this.mMouse.x = x;
			this.mMouse.y = me.y;
			TimeLineView.this.mTimescale.setVbarPosition(x);
			if (this.mGraphicsState == TimeLineView.GraphicsState.Marking) {
				TimeLineView.this.mTimescale.setMarkEnd(x);
			}

			if (this.mGraphicsState == TimeLineView.GraphicsState.Normal) {
				TimeLineView.this.mSurface.setCursor(this.mNormalCursor);
			} else if (this.mGraphicsState == TimeLineView.GraphicsState.Marking) {
				if (this.mMouse.x >= this.mMouseMarkStartX) {
					TimeLineView.this.mSurface
							.setCursor(this.mIncreasingCursor);
				} else
					TimeLineView.this.mSurface
							.setCursor(this.mDecreasingCursor);
			}
			int rownum = (this.mMouse.y + TimeLineView.this.mScrollOffsetY) / 32;
			if ((me.y < 0) || (me.y >= dim.y)) {
				rownum = -1;
			}
			if (TimeLineView.this.mMouseRow != rownum) {
				TimeLineView.this.mMouseRow = rownum;
				TimeLineView.this.mLabels.redraw();
			}
			redraw();
		}

		private void mouseDown(MouseEvent me) {
			Point dim = TimeLineView.this.mSurface.getSize();
			int x = me.x;
			if (x < 10)
				x = 10;
			if (x > dim.x - 60)
				x = dim.x - 60;
			this.mMouseMarkStartX = x;
			this.mGraphicsState = TimeLineView.GraphicsState.Marking;
			TimeLineView.this.mSurface.setCursor(this.mIncreasingCursor);
			TimeLineView.this.mTimescale.setMarkStart(this.mMouseMarkStartX);
			TimeLineView.this.mTimescale.setMarkEnd(this.mMouseMarkStartX);
			redraw();
		}

		private void mouseUp(MouseEvent me) {
			TimeLineView.this.mSurface.setCursor(this.mNormalCursor);
			if (this.mGraphicsState != TimeLineView.GraphicsState.Marking) {
				this.mGraphicsState = TimeLineView.GraphicsState.Normal;
				return;
			}
			this.mGraphicsState = TimeLineView.GraphicsState.Animating;
			Point dim = TimeLineView.this.mSurface.getSize();

			if ((me.y <= 0) || (me.y >= dim.y)) {
				this.mGraphicsState = TimeLineView.GraphicsState.Normal;
				redraw();
				return;
			}

			int x = me.x;
			if (x < 10)
				x = 10;
			if (x > dim.x - 60)
				x = dim.x - 60;
			this.mMouseMarkEndX = x;

			int dist = this.mMouseMarkEndX - this.mMouseMarkStartX;
			if (dist < 0)
				dist = -dist;
			if (dist <= 2) {
				this.mGraphicsState = TimeLineView.GraphicsState.Normal;

				this.mMouseSelect.x = this.mMouseMarkStartX;
				this.mMouseSelect.y = me.y;
				redraw();
				return;
			}

			if (this.mMouseMarkEndX < this.mMouseMarkStartX) {
				int temp = this.mMouseMarkEndX;
				this.mMouseMarkEndX = this.mMouseMarkStartX;
				this.mMouseMarkStartX = temp;
			}

			if ((this.mMouseMarkStartX <= 20)
					&& (this.mMouseMarkEndX >= dim.x - 60 - 10)) {
				this.mGraphicsState = TimeLineView.GraphicsState.Normal;
				redraw();
				return;
			}

			double minVal = TimeLineView.this.mScaleInfo.getMinVal();
			double maxVal = TimeLineView.this.mScaleInfo.getMaxVal();
			double ppr = TimeLineView.this.mScaleInfo.getPixelsPerRange();
			this.mZoomMin = (minVal + (this.mMouseMarkStartX - 10) / ppr);
			this.mZoomMax = (minVal + (this.mMouseMarkEndX - 10) / ppr);

			if (this.mZoomMin < this.mMinDataVal)
				this.mZoomMin = this.mMinDataVal;
			if (this.mZoomMax > this.mMaxDataVal) {
				this.mZoomMax = this.mMaxDataVal;
			}

			int xdim = dim.x - 70;
			TickScaler scaler = new TickScaler(this.mZoomMin, this.mZoomMax,
					xdim, 50);

			scaler.computeTicks(false);
			this.mZoomMin = scaler.getMinVal();
			this.mZoomMax = scaler.getMaxVal();

			this.mMouseMarkStartX = ((int) ((this.mZoomMin - minVal) * ppr + 10.0D));
			this.mMouseMarkEndX = ((int) ((this.mZoomMax - minVal) * ppr + 10.0D));
			TimeLineView.this.mTimescale.setMarkStart(this.mMouseMarkStartX);
			TimeLineView.this.mTimescale.setMarkEnd(this.mMouseMarkEndX);

			this.mMouseEndDistance = (dim.x - 60 - this.mMouseMarkEndX);
			this.mMouseStartDistance = (this.mMouseMarkStartX - 10);
			this.mZoomMouseStart = this.mMouseMarkStartX;
			this.mZoomMouseEnd = this.mMouseMarkEndX;
			this.mZoomStep = 0;

			this.mMin2ZoomMin = (this.mZoomMin - minVal);
			this.mZoomMax2Max = (maxVal - this.mZoomMax);
			this.mZoomFixed = (this.mZoomMin + (this.mZoomMax - this.mZoomMin)
					* this.mMin2ZoomMin
					/ (this.mMin2ZoomMin + this.mZoomMax2Max));

			this.mZoomFixedPixel = ((this.mZoomFixed - minVal) * ppr + 10.0D);
			this.mFixedPixelStartDistance = (this.mZoomFixedPixel - 10.0D);
			this.mFixedPixelEndDistance = (dim.x - 60 - this.mZoomFixedPixel);

			this.mZoomMin2Fixed = (this.mZoomFixed - this.mZoomMin);
			this.mFixed2ZoomMax = (this.mZoomMax - this.mZoomFixed);

			getDisplay().timerExec(10, this.mZoomAnimator);
			redraw();
			update();
		}

		private void mouseScrolled(MouseEvent me) {
			this.mGraphicsState = TimeLineView.GraphicsState.Scrolling;
			double tMin = TimeLineView.this.mScaleInfo.getMinVal();
			double tMax = TimeLineView.this.mScaleInfo.getMaxVal();
			double zoomFactor = 2.0D;
			double tMinRef = this.mLimitMinVal;
			double tMaxRef = this.mLimitMaxVal;

			double tMaxNew;
			double tMinNew;
			double t;

			if (me.count > 0) {
				Point dim = TimeLineView.this.mSurface.getSize();
				int x = me.x;
				if (x < 10)
					x = 10;
				if (x > dim.x - 60)
					x = dim.x - 60;
				double ppr = TimeLineView.this.mScaleInfo.getPixelsPerRange();
				t = tMin + (x - 10) / ppr;
				tMinNew = Math.max(tMinRef, t - (t - tMin) / zoomFactor);
				tMaxNew = Math.min(tMaxRef, t + (tMax - t) / zoomFactor);
			} else {
				double factor = (tMax - tMin) / (tMaxRef - tMinRef);
				if (factor < 1.0D) {
					t = (factor * tMinRef - tMin) / (factor - 1.0D);
					tMinNew = Math.max(tMinRef, t - zoomFactor * (t - tMin));
					tMaxNew = Math.min(tMaxRef, t + zoomFactor * (tMax - t));
				} else {
					return;
				}
			}
			TimeLineView.this.mScaleInfo.setMinVal(tMinNew);
			TimeLineView.this.mScaleInfo.setMaxVal(tMaxNew);
			TimeLineView.this.mSurface.redraw();
		}

		private void mouseDoubleClick(MouseEvent me) {
		}

		public void startScaling(int mouseX) {
			Point dim = TimeLineView.this.mSurface.getSize();
			int x = mouseX;
			if (x < 10)
				x = 10;
			if (x > dim.x - 60)
				x = dim.x - 60;
			this.mMouseMarkStartX = x;
			this.mGraphicsState = TimeLineView.GraphicsState.Scaling;
			this.mScalePixelsPerRange = TimeLineView.this.mScaleInfo
					.getPixelsPerRange();
			this.mScaleMinVal = TimeLineView.this.mScaleInfo.getMinVal();
			this.mScaleMaxVal = TimeLineView.this.mScaleInfo.getMaxVal();
		}

		public void stopScaling(int mouseX) {
			this.mGraphicsState = TimeLineView.GraphicsState.Normal;
		}

		private void animateHighlight() {
			this.mHighlightStep += 1;
			if (this.mHighlightStep >= this.HIGHLIGHT_STEPS) {
				this.mFadeColors = false;
				this.mHighlightStep = 0;

				this.mCachedEndRow = -1;
			} else {
				this.mFadeColors = true;
				this.mShowHighlightName = true;
				this.mHighlightHeight = this.highlightHeights[this.mHighlightStep];
				getDisplay().timerExec(50, this.mHighlightAnimator);
			}
			redraw();
		}

		private void clearHighlights() {
			this.mShowHighlightName = false;
			this.mHighlightHeight = 0;
			TimeLineView.this.mHighlightMethodData = null;
			TimeLineView.this.mHighlightCall = null;
			this.mFadeColors = false;
			this.mHighlightStep = 0;

			this.mCachedEndRow = -1;
			redraw();
		}

		private void animateZoom() {
			this.mZoomStep += 1;
			if (this.mZoomStep > 8) {
				this.mGraphicsState = TimeLineView.GraphicsState.Normal;

				this.mCachedMinVal = (TimeLineView.this.mScaleInfo.getMinVal() + 1.0D);
			} else if (this.mZoomStep == 8) {
				TimeLineView.this.mScaleInfo.setMinVal(this.mZoomMin);
				TimeLineView.this.mScaleInfo.setMaxVal(this.mZoomMax);
				this.mMouseMarkStartX = 10;
				Point dim = getSize();
				this.mMouseMarkEndX = (dim.x - 60);
				TimeLineView.this.mTimescale
						.setMarkStart(this.mMouseMarkStartX);
				TimeLineView.this.mTimescale.setMarkEnd(this.mMouseMarkEndX);
				getDisplay().timerExec(10, this.mZoomAnimator);
			} else {
				double fraction = this.mZoomFractions[this.mZoomStep];
				this.mMouseMarkStartX = ((int) (this.mZoomMouseStart - fraction
						* this.mMouseStartDistance));
				this.mMouseMarkEndX = ((int) (this.mZoomMouseEnd + fraction
						* this.mMouseEndDistance));
				TimeLineView.this.mTimescale
						.setMarkStart(this.mMouseMarkStartX);
				TimeLineView.this.mTimescale.setMarkEnd(this.mMouseMarkEndX);

				double ppr;
				if (this.mZoomMin2Fixed >= this.mFixed2ZoomMax) {
					ppr = (this.mZoomFixedPixel - this.mMouseMarkStartX)
							/ this.mZoomMin2Fixed;
				} else
					ppr = (this.mMouseMarkEndX - this.mZoomFixedPixel)
							/ this.mFixed2ZoomMax;
				double newMin = this.mZoomFixed - this.mFixedPixelStartDistance
						/ ppr;
				double newMax = this.mZoomFixed + this.mFixedPixelEndDistance
						/ ppr;
				TimeLineView.this.mScaleInfo.setMinVal(newMin);
				TimeLineView.this.mScaleInfo.setMaxVal(newMax);

				getDisplay().timerExec(10, this.mZoomAnimator);
			}
			redraw();
		}

		private TimeLineView.GraphicsState mGraphicsState = TimeLineView.GraphicsState.Normal;
		private Point mMouse = new Point(10, 0);
		private int mMouseMarkStartX;
		private int mMouseMarkEndX;
		private boolean mDebug = false;
		private ArrayList<TimeLineView.Strip> mStripList = new ArrayList<Strip>();
		private ArrayList<TimeLineView.Range> mHighlightExclusive = new ArrayList<Range>();
		private ArrayList<TimeLineView.Range> mHighlightInclusive = new ArrayList<Range>();
		private int mMinStripHeight = 2;
		private double mCachedMinVal;
		private double mCachedMaxVal;
		private int mCachedStartRow;
		private int mCachedEndRow;
		private double mScalePixelsPerRange;
		private double mScaleMinVal;
		private double mScaleMaxVal;
		private double mLimitMinVal;
		private double mLimitMaxVal;
		private double mMinDataVal;
		private double mMaxDataVal;
		private Cursor mNormalCursor;
		private Cursor mIncreasingCursor;
		private Cursor mDecreasingCursor;
		private static final int ZOOM_TIMER_INTERVAL = 10;
		private static final int HIGHLIGHT_TIMER_INTERVAL = 50;
		private static final int ZOOM_STEPS = 8;
		private int mHighlightHeight = 4;
		private final int[] highlightHeights = { 0, 2, 4, 5, 6, 5, 4, 2, 4, 5,
				6 };

		private final int HIGHLIGHT_STEPS = this.highlightHeights.length;
		private boolean mFadeColors;
		private boolean mShowHighlightName;
		private double[] mZoomFractions;
		private int mZoomStep;
		private int mZoomMouseStart;
		private int mZoomMouseEnd;
		private int mMouseStartDistance;
		private int mMouseEndDistance;
		private Point mMouseSelect = new Point(0, 0);

		private double mZoomFixed;
		private double mZoomFixedPixel;
		private double mFixedPixelStartDistance;
		private double mFixedPixelEndDistance;
		private double mZoomMin2Fixed;
		private double mMin2ZoomMin;
		private double mFixed2ZoomMax;
		private double mZoomMax2Max;
		private double mZoomMin;
		private double mZoomMax;
		private Runnable mZoomAnimator;
		private Runnable mHighlightAnimator;
		private int mHighlightStep;
	}

	private int computeVisibleRows(int ydim) {
		int offsetY = this.mScrollOffsetY;
		int spaceNeeded = this.mNumRows * 32;
		if (offsetY + ydim > spaceNeeded) {
			offsetY = spaceNeeded - ydim;
			if (offsetY < 0) {
				offsetY = 0;
			}
		}
		this.mStartRow = (offsetY / 32);
		this.mEndRow = ((offsetY + ydim) / 32);
		if (this.mEndRow >= this.mNumRows) {
			this.mEndRow = (this.mNumRows - 1);
		}

		return offsetY;
	}

	private void startHighlighting() {
		this.mSurface.mHighlightStep = 0;
		this.mSurface.mFadeColors = true;

		this.mSurface.mCachedEndRow = -1;
		getDisplay().timerExec(0, this.mSurface.mHighlightAnimator);
	}

	private static class RowData {
		private String mName;
		private int mRank;

		RowData(TimeLineView.Row row) {
			this.mName = row.getName();
			this.mStack = new ArrayList<TimeLineView.Block>();
		}

		private long mElapsed;

		public void push(TimeLineView.Block block) {
			this.mStack.add(block);
		}

		private long mEndTime;
		private ArrayList<TimeLineView.Block> mStack;

		public TimeLineView.Block top() {
			if (this.mStack.size() == 0)
				return null;
			return (TimeLineView.Block) this.mStack.get(this.mStack.size() - 1);
		}

		public void pop() {
			if (this.mStack.size() == 0)
				return;
			this.mStack.remove(this.mStack.size() - 1);
		}
	}

	private static class Segment {
		private TimeLineView.RowData mRowData;
		private TimeLineView.Block mBlock;
		private long mStartTime;
		private long mEndTime;
		private boolean mIsContextSwitch;

		Segment(TimeLineView.RowData rowData, TimeLineView.Block block,
				long startTime, long endTime) {
			this.mRowData = rowData;
			if (block.isContextSwitch()) {
				this.mBlock = block.getParentBlock();
				this.mIsContextSwitch = true;
			} else {
				this.mBlock = block;
			}
			this.mStartTime = startTime;
			this.mEndTime = endTime;
		}
	}

	private static class Strip {
		int mX;
		int mY;
		int mWidth;
		int mHeight;
		TimeLineView.RowData mRowData;
		TimeLineView.Segment mSegment;
		Color mColor;

		Strip(int x, int y, int width, int height,
				TimeLineView.RowData rowData, TimeLineView.Segment segment,
				Color color) {
			this.mX = x;
			this.mY = y;
			this.mWidth = width;
			this.mHeight = height;
			this.mRowData = rowData;
			this.mSegment = segment;
			this.mColor = color;
		}
	}

	private static class Pixel {
		public void setFields(int start, double weight,
				TimeLineView.Segment segment, Color color,
				TimeLineView.RowData rowData) {
			this.mStart = start;
			this.mMaxWeight = weight;
			this.mSegment = segment;
			this.mColor = color;
			this.mRowData = rowData;
		}

		int mStart = -2;
		double mMaxWeight;
		TimeLineView.Segment mSegment;
		Color mColor;
		TimeLineView.RowData mRowData;
	}

	private static class Range {
		Range(int xStart, int width, int y, Color color) {
			this.mXdim.x = xStart;
			this.mXdim.y = width;
			this.mY = y;
			this.mColor = color;
		}

		Point mXdim = new Point(0, 0);
		int mY;
		Color mColor;
	}
}

/*
 * Location:
 * /Users/frank/Applications/android-sdk-macosx/tools/lib/traceview.jar
 * !/com/android/traceview/TimeLineView.class Java compiler version: 6 (50.0)
 * JD-Core Version: 0.7.1
 */