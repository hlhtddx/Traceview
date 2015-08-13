package com.android.traceview;

class TickScaler {
	private double mMinVal;

	private double mMaxVal;

	private double mRangeVal;

	private int mNumPixels;

	private int mPixelsPerTick;

	private double mPixelsPerRange;

	private double mTickIncrement;

	private double mMinMajorTick;

	TickScaler(double minVal, double maxVal, int numPixels, int pixelsPerTick) {
		this.mMinVal = minVal;
		this.mMaxVal = maxVal;
		this.mNumPixels = numPixels;
		this.mPixelsPerTick = pixelsPerTick;
	}

	public void setMinVal(double minVal) {
		this.mMinVal = minVal;
	}

	public double getMinVal() {
		return this.mMinVal;
	}

	public void setMaxVal(double maxVal) {
		this.mMaxVal = maxVal;
	}

	public double getMaxVal() {
		return this.mMaxVal;
	}

	public void setNumPixels(int numPixels) {
		this.mNumPixels = numPixels;
	}

	public int getNumPixels() {
		return this.mNumPixels;
	}

	public void setPixelsPerTick(int pixelsPerTick) {
		this.mPixelsPerTick = pixelsPerTick;
	}

	public int getPixelsPerTick() {
		return this.mPixelsPerTick;
	}

	public void setPixelsPerRange(double pixelsPerRange) {
		this.mPixelsPerRange = pixelsPerRange;
	}

	public double getPixelsPerRange() {
		return this.mPixelsPerRange;
	}

	public void setTickIncrement(double tickIncrement) {
		this.mTickIncrement = tickIncrement;
	}

	public double getTickIncrement() {
		return this.mTickIncrement;
	}

	public void setMinMajorTick(double minMajorTick) {
		this.mMinMajorTick = minMajorTick;
	}

	public double getMinMajorTick() {
		return this.mMinMajorTick;
	}

	public int valueToPixel(double value) {
		return (int) Math.ceil(this.mPixelsPerRange * (value - this.mMinVal)
				- 0.5D);
	}

	public double valueToPixelFraction(double value) {
		return this.mPixelsPerRange * (value - this.mMinVal);
	}

	public double pixelToValue(int pixel) {
		return this.mMinVal + pixel / this.mPixelsPerRange;
	}

	public void computeTicks(boolean useGivenEndPoints) {
		int numTicks = this.mNumPixels / this.mPixelsPerTick;
		this.mRangeVal = (this.mMaxVal - this.mMinVal);
		this.mTickIncrement = (this.mRangeVal / numTicks);
		double dlogTickIncrement = Math.log10(this.mTickIncrement);
		int logTickIncrement = (int) Math.floor(dlogTickIncrement);
		double scale = Math.pow(10.0D, logTickIncrement);
		double scaledTickIncr = this.mTickIncrement / scale;
		if (scaledTickIncr > 5.0D) {
			scaledTickIncr = 10.0D;
		} else if (scaledTickIncr > 2.0D) {
			scaledTickIncr = 5.0D;
		} else if (scaledTickIncr > 1.0D) {
			scaledTickIncr = 2.0D;
		} else
			scaledTickIncr = 1.0D;
		this.mTickIncrement = (scaledTickIncr * scale);

		if (!useGivenEndPoints) {
			double minorTickIncrement = this.mTickIncrement / 5.0D;
			double dval = this.mMaxVal / minorTickIncrement;
			int ival = (int) dval;
			if (ival != dval) {
				this.mMaxVal = ((ival + 1) * minorTickIncrement);
			}

			ival = (int) (this.mMinVal / this.mTickIncrement);
			this.mMinVal = (ival * this.mTickIncrement);
			this.mMinMajorTick = this.mMinVal;
		} else {
			int ival = (int) (this.mMinVal / this.mTickIncrement);
			this.mMinMajorTick = (ival * this.mTickIncrement);
			if (this.mMinMajorTick < this.mMinVal) {
				this.mMinMajorTick += this.mTickIncrement;
			}
		}
		this.mRangeVal = (this.mMaxVal - this.mMinVal);
		this.mPixelsPerRange = (this.mNumPixels / this.mRangeVal);
	}
}

/*
 * Location:
 * /Users/frank/Applications/android-sdk-macosx/tools/lib/traceview.jar
 * !/com/android/traceview/TickScaler.class Java compiler version: 6 (50.0)
 * JD-Core Version: 0.7.1
 */