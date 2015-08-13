package com.android.traceview;

public class ProfileData {
	protected MethodData mElement;

	protected MethodData mContext;

	protected boolean mElementIsParent;

	protected long mElapsedInclusiveCpuTime;

	protected long mElapsedInclusiveRealTime;

	protected int mNumCalls;

	public ProfileData() {
	}

	public ProfileData(MethodData context, MethodData element,
			boolean elementIsParent) {
		this.mContext = context;
		this.mElement = element;
		this.mElementIsParent = elementIsParent;
	}

	public String getProfileName() {
		return this.mElement.getProfileName();
	}

	public MethodData getMethodData() {
		return this.mElement;
	}

	public void addElapsedInclusive(long cpuTime, long realTime) {
		this.mElapsedInclusiveCpuTime += cpuTime;
		this.mElapsedInclusiveRealTime += realTime;
		this.mNumCalls += 1;
	}

	public void setElapsedInclusive(long cpuTime, long realTime) {
		this.mElapsedInclusiveCpuTime = cpuTime;
		this.mElapsedInclusiveRealTime = realTime;
	}

	public long getElapsedInclusiveCpuTime() {
		return this.mElapsedInclusiveCpuTime;
	}

	public long getElapsedInclusiveRealTime() {
		return this.mElapsedInclusiveRealTime;
	}

	public void setNumCalls(int numCalls) {
		this.mNumCalls = numCalls;
	}

	public String getNumCalls() {
		int totalCalls;
		if (this.mElementIsParent) {
			totalCalls = this.mContext.getTotalCalls();
		} else
			totalCalls = this.mElement.getTotalCalls();
		return String.format(
				"%d/%d",
				new Object[] { Integer.valueOf(this.mNumCalls),
						Integer.valueOf(totalCalls) });
	}

	public boolean isParent() {
		return this.mElementIsParent;
	}

	public MethodData getContext() {
		return this.mContext;
	}
}

/*
 * Location:
 * /Users/frank/Applications/android-sdk-macosx/tools/lib/traceview.jar
 * !/com/android/traceview/ProfileData.class Java compiler version: 6 (50.0)
 * JD-Core Version: 0.7.1
 */