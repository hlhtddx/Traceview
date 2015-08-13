package com.android.traceview;

import java.text.DecimalFormat;

public class TraceUnits {
	private TimeScale mTimeScale = TimeScale.MicroSeconds;
	private double mScale = 1.0D;
	DecimalFormat mFormatter = new DecimalFormat();

	public double getScaledValue(long value) {
		return value * this.mScale;
	}

	public double getScaledValue(double value) {
		return value * this.mScale;
	}

	public String valueOf(long value) {
		return valueOf(value);
	}

	public String valueOf(double value) {
		double scaled = value * this.mScale;
		String pattern;
		if ((int) scaled == scaled) {
			pattern = "###,###";
		} else
			pattern = "###,###.###";
		this.mFormatter.applyPattern(pattern);
		return this.mFormatter.format(scaled);
	}

	public String labelledString(double value) {
		String units = label();
		String num = valueOf(value);
		return String.format("%s: %s", new Object[] { units, num });
	}

	public String labelledString(long value) {
		return labelledString((double)value);
	}

	public String label() {
		if (this.mScale == 1.0D)
			return "usec";
		if (this.mScale == 0.001D)
			return "msec";
		if (this.mScale == 1.0E-6D)
			return "sec";
		return null;
	}

	public void setTimeScale(TimeScale val) {
		this.mTimeScale = val;
		switch (val) {
		case Seconds:
			this.mScale = 1.0E-6D;
			break;
		case MilliSeconds:
			this.mScale = 0.001D;
			break;
		case MicroSeconds:
			this.mScale = 1.0D;
		}
	}

	public TimeScale getTimeScale() {
		return this.mTimeScale;
	}

	public static enum TimeScale {
		Seconds, MilliSeconds, MicroSeconds;

		private TimeScale() {
		}
	}
}

/*
 * Location:
 * /Users/frank/Applications/android-sdk-macosx/tools/lib/traceview.jar
 * !/com/android/traceview/TraceUnits.class Java compiler version: 6 (50.0)
 * JD-Core Version: 0.7.1
 */