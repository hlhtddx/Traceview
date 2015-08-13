package com.android.traceview;

import com.android.sdkstats.SdkStatsService;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Properties;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.window.ApplicationWindow;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

public class MainWindow extends ApplicationWindow {
	private static final String PING_NAME = "Traceview";
	private TraceReader mReader;
	private String mTraceName;
	public static HashMap<String, String> sStringCache = new HashMap<String, String>();

	public MainWindow(String traceName, TraceReader reader) {
		super(null);
		this.mReader = reader;
		this.mTraceName = traceName;

		addMenuBar();
	}

	public void run() {
		setBlockOnOpen(true);
		open();
	}

	protected void configureShell(Shell shell) {
		super.configureShell(shell);
		shell.setText("Traceview: " + this.mTraceName);

		InputStream in = getClass().getClassLoader().getResourceAsStream(
				"icons/traceview-128.png");

		if (in != null) {
			shell.setImage(new Image(shell.getDisplay(), in));
		}

		shell.setBounds(100, 10, 1282, 900);
	}

	protected Control createContents(Composite parent) {
		ColorController.assignMethodColors(parent.getDisplay(),
				this.mReader.getMethods());
		SelectionController selectionController = new SelectionController();

		GridLayout gridLayout = new GridLayout(1, false);
		gridLayout.marginWidth = 0;
		gridLayout.marginHeight = 0;
		gridLayout.horizontalSpacing = 0;
		gridLayout.verticalSpacing = 0;
		parent.setLayout(gridLayout);

		Display display = parent.getDisplay();
		Color darkGray = display.getSystemColor(16);

		SashForm sashForm1 = new SashForm(parent, 512);
		sashForm1.setBackground(darkGray);
		sashForm1.SASH_WIDTH = 3;
		GridData data = new GridData(1808);
		sashForm1.setLayoutData(data);

		new TimeLineView(sashForm1, this.mReader, selectionController);

		new ProfileView(sashForm1, this.mReader, selectionController);
		return sashForm1;
	}

	protected MenuManager createMenuManager() {
		MenuManager manager = super.createMenuManager();

		MenuManager viewMenu = new MenuManager("View");
		manager.add(viewMenu);

		Action showPropertiesAction = new Action("Show Properties...") {
			public void run() {
				MainWindow.this.showProperties();
			}
		};
		viewMenu.add(showPropertiesAction);

		return manager;
	}

	private void showProperties() {
		PropertiesDialog dialog = new PropertiesDialog(getShell());
		dialog.setProperties(this.mReader.getProperties());
		dialog.open();
	}

	private static String makeTempTraceFile(String base) throws IOException {
		File temp = File.createTempFile(base, ".trace");
		temp.deleteOnExit();

		FileOutputStream dstStream = null;
		FileInputStream keyStream = null;
		FileInputStream dataStream = null;
		try {
			dstStream = new FileOutputStream(temp);
			FileChannel dstChannel = dstStream.getChannel();

			keyStream = new FileInputStream(base + ".key");
			FileChannel srcChannel = keyStream.getChannel();
			long size = dstChannel.transferFrom(srcChannel, 0L,
					srcChannel.size());
			srcChannel.close();

			dataStream = new FileInputStream(base + ".data");
			srcChannel = dataStream.getChannel();
			dstChannel.transferFrom(srcChannel, size, srcChannel.size());
		} finally {
			if (dstStream != null) {
				dstStream.close();
			}
			if (keyStream != null) {
				keyStream.close();
			}
			if (dataStream != null) {
				dataStream.close();
			}
		}

		return temp.getPath();
	}

	private static String getRevision() {
		Properties p = new Properties();
		try {
			String toolsdir = System
					.getProperty("com.android.traceview.toolsdir");
			File sourceProp;
			String revision;
			if ((toolsdir == null) || (toolsdir.length() == 0)) {
				sourceProp = new File("source.properties");
			} else {
				sourceProp = new File(toolsdir, "source.properties");
			}

			FileInputStream fis = null;
			try {
				fis = new FileInputStream(sourceProp);
				p.load(fis);

				if (fis != null) {
					try {
						fis.close();
					} catch (IOException ignore) {
					}
				}

				revision = p.getProperty("Pkg.Revision");
			} finally {
				if (fis != null) {
					try {
						fis.close();
					} catch (IOException ignore) {
					}
				}
			}

			if ((revision != null) && (revision.length() > 0)) {
				return revision;
			}
		} catch (FileNotFoundException e) {
		} catch (IOException e) {
		}

		return null;
	}

	public static void main(String[] args) {
		TraceReader reader = null;
		boolean regression = false;

		String revision = getRevision();
		if (revision != null) {
			new SdkStatsService().ping("Traceview", revision);
		}

		int argc = 0;
		int len = args.length;
		while (argc < len) {
			String arg = args[argc];
			if (arg.charAt(0) != '-') {
				break;
			}
			if (!arg.equals("-r"))
				break;
			regression = true;

			argc++;
		}
		if (argc != len - 1) {
			System.out.printf("Usage: java %s [-r] trace%n",
					new Object[] { MainWindow.class.getName() });
			System.out.printf("  -r   regression only%n", new Object[0]);
			return;
		}

		String traceName = args[(len - 1)];
		File file = new File(traceName);
		if ((file.exists()) && (file.isDirectory())) {
			System.out.printf("Qemu trace files not supported yet.\n",
					new Object[0]);
			System.exit(1);
		} else {
			if (!file.exists()) {
				if (new File(traceName + ".trace").exists()) {
					traceName = traceName + ".trace";
				} else if ((new File(traceName + ".data").exists())
						&& (new File(traceName + ".key").exists())) {
					try {
						traceName = makeTempTraceFile(traceName);
					} catch (IOException e) {
						System.err.printf(
								"cannot convert old trace file '%s'\n",
								new Object[] { traceName });
						System.exit(1);
					}
				} else {
					System.err.printf("trace file '%s' not found\n",
							new Object[] { traceName });
					System.exit(1);
				}
			}
			try {
				reader = new DmTraceReader(traceName, regression);
			} catch (IOException e) {
				System.err.printf("Failed to read the trace file",
						new Object[0]);
				e.printStackTrace();
				System.exit(1);
				return;
			}
		}

		reader.getTraceUnits().setTimeScale(TraceUnits.TimeScale.MilliSeconds);

		Display.setAppName("Traceview");
		new MainWindow(traceName, reader).run();
	}
}

/*
 * Location:
 * /Users/frank/Applications/android-sdk-macosx/tools/lib/traceview.jar
 * !/com/android/traceview/MainWindow.class Java compiler version: 6 (50.0)
 * JD-Core Version: 0.7.1
 */