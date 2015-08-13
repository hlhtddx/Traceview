package com.android.traceview;

import org.eclipse.swt.graphics.Color;

class Call implements TimeLineView.Block {
	private final ThreadData mThreadData;
	private final MethodData mMethodData;
	final Call mCaller;
	private String mName;
	private boolean mIsRecursive;
	long mGlobalStartTime;
	long mGlobalEndTime;
	long mThreadStartTime;
	long mThreadEndTime;
	long mInclusiveRealTime;
	long mExclusiveRealTime;
	long mInclusiveCpuTime;
	long mExclusiveCpuTime;

	Call(ThreadData threadData, MethodData methodData, Call caller) {
		this.mThreadData = threadData;
		this.mMethodData = methodData;
		this.mName = methodData.getProfileName();
		this.mCaller = caller;
	}

	public void updateName() {
		this.mName = this.mMethodData.getProfileName();
	}

	public double addWeight(int x, int y, double weight) {
		return this.mMethodData.addWeight(x, y, weight);
	}

	public void clearWeight() {
		this.mMethodData.clearWeight();
	}

	public long getStartTime() {
		return this.mGlobalStartTime;
	}

	public long getEndTime() {
		return this.mGlobalEndTime;
	}

	public long getExclusiveCpuTime() {
		return this.mExclusiveCpuTime;
	}

	public long getInclusiveCpuTime() {
		return this.mInclusiveCpuTime;
	}

	public long getExclusiveRealTime() {
		return this.mExclusiveRealTime;
	}

	public long getInclusiveRealTime() {
		return this.mInclusiveRealTime;
	}

	public Color getColor() {
		return this.mMethodData.getColor();
	}

	public String getName() {
		return this.mName;
	}

	public void setName(String name) {
		this.mName = name;
	}

	public ThreadData getThreadData() {
		return this.mThreadData;
	}

	public int getThreadId() {
		return this.mThreadData.getId();
	}

	public MethodData getMethodData() {
		return this.mMethodData;
	}

	public boolean isContextSwitch() {
		return this.mMethodData.getId() == -1;
	}

	public boolean isIgnoredBlock() {
		return (this.mCaller == null)
				|| ((isContextSwitch()) && (this.mCaller.mCaller == null));
	}

	public TimeLineView.Block getParentBlock() {
		return this.mCaller;
	}

	public boolean isRecursive() {
		return this.mIsRecursive;
	}

	void setRecursive(boolean isRecursive) {
		this.mIsRecursive = isRecursive;
	}

	void addCpuTime(long elapsedCpuTime) {
		this.mExclusiveCpuTime += elapsedCpuTime;
		this.mInclusiveCpuTime += elapsedCpuTime;
	}

	void finish() {
		if (this.mCaller != null) {
			this.mCaller.mInclusiveCpuTime += this.mInclusiveCpuTime;
			this.mCaller.mInclusiveRealTime += this.mInclusiveRealTime;
		}

		this.mMethodData.addElapsedExclusive(this.mExclusiveCpuTime,
				this.mExclusiveRealTime);
		if (!this.mIsRecursive) {
			this.mMethodData.addTopExclusive(this.mExclusiveCpuTime,
					this.mExclusiveRealTime);
		}
		this.mMethodData.addElapsedInclusive(this.mInclusiveCpuTime,
				this.mInclusiveRealTime, this.mIsRecursive, this.mCaller);
	}

	public static final class TraceAction {
		public static final int ACTION_ENTER = 0;
		public static final int ACTION_EXIT = 1;
		public final int mAction;
		public final Call mCall;

		public TraceAction(int action, Call call) {
			this.mAction = action;
			this.mCall = call;
		}
	}
}

/*
 * Location:
 * /Users/frank/Applications/android-sdk-macosx/tools/lib/traceview.jar
 * !/com/android/traceview/Call.class Java compiler version: 6 (50.0) JD-Core
 * Version: 0.7.1
 */