package com.android.traceview;

import java.util.ArrayList;
import java.util.HashMap;

public abstract class TraceReader {
	private TraceUnits mTraceUnits;

	public TraceUnits getTraceUnits() {
		if (this.mTraceUnits == null)
			this.mTraceUnits = new TraceUnits();
		return this.mTraceUnits;
	}

	public ArrayList<TimeLineView.Record> getThreadTimeRecords() {
		return null;
	}

	public HashMap<Integer, String> getThreadLabels() {
		return null;
	}

	public MethodData[] getMethods() {
		return null;
	}

	public ThreadData[] getThreads() {
		return null;
	}

	public long getTotalCpuTime() {
		return 0L;
	}

	public long getTotalRealTime() {
		return 0L;
	}

	public boolean haveCpuTime() {
		return false;
	}

	public boolean haveRealTime() {
		return false;
	}

	public HashMap<String, String> getProperties() {
		return null;
	}

	public ProfileProvider getProfileProvider() {
		return null;
	}

	public TimeBase getPreferredTimeBase() {
		return TimeBase.CPU_TIME;
	}

	public String getClockSource() {
		return null;
	}
}

/*
 * Location:
 * /Users/frank/Applications/android-sdk-macosx/tools/lib/traceview.jar
 * !/com/android/traceview/TraceReader.class Java compiler version: 6 (50.0)
 * JD-Core Version: 0.7.1
 */