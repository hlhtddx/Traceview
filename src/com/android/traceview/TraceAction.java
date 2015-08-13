package com.android.traceview;

final class TraceAction {
	public static final int ACTION_ENTER = 0;

	public static final int ACTION_EXIT = 1;

	public static final int ACTION_INCOMPLETE = 2;

	public final int mAction;

	public final Call mCall;

	public TraceAction(int action, Call call) {
		this.mAction = action;
		this.mCall = call;
	}
}

/*
 * Location:
 * /Users/frank/Applications/android-sdk-macosx/tools/lib/traceview.jar
 * !/com/android/traceview/TraceAction.class Java compiler version: 6 (50.0)
 * JD-Core Version: 0.7.1
 */