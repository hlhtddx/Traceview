package com.android.traceview;

import java.util.ArrayList;
import java.util.HashMap;

class ThreadData implements TimeLineView.Row {
	private int mId;
	private String mName;
	private boolean mIsEmpty;
	private Call mRootCall;
	private ArrayList<Call> mStack = new ArrayList<Call>();

	private HashMap<MethodData, Integer> mStackMethods = new HashMap<MethodData, Integer>();

	boolean mHaveGlobalTime;

	long mGlobalStartTime;
	long mGlobalEndTime;
	boolean mHaveThreadTime;
	long mThreadStartTime;
	long mThreadEndTime;
	long mThreadCurrentTime;

	ThreadData(int id, String name, MethodData topLevel) {
		this.mId = id;
		this.mName = String.format("[%d] %s",
				new Object[] { Integer.valueOf(id), name });
		this.mIsEmpty = true;
		this.mRootCall = new Call(this, topLevel, null);
		this.mRootCall.setName(this.mName);
		this.mStack.add(this.mRootCall);
	}

	public String getName() {
		return this.mName;
	}

	public Call getRootCall() {
		return this.mRootCall;
	}

	public boolean isEmpty() {
		return this.mIsEmpty;
	}

	Call enter(MethodData method, ArrayList<TraceAction> trace) {
		if (this.mIsEmpty) {
			this.mIsEmpty = false;
			if (trace != null) {
				trace.add(new TraceAction(TraceAction.ACTION_ENTER, this.mRootCall));
			}
		}

		Call caller = top();
		Call call = new Call(this, method, caller);
		this.mStack.add(call);

		if (trace != null) {
			trace.add(new TraceAction(TraceAction.ACTION_ENTER, call));
		}

		Integer num = (Integer) this.mStackMethods.get(method);
		if (num == null) {
			num = Integer.valueOf(0);
		} else if (num.intValue() > 0) {
			call.setRecursive(true);
		}
		this.mStackMethods.put(method, Integer.valueOf(num.intValue() + 1));

		return call;
	}

	Call exit(MethodData method, ArrayList<TraceAction> trace) {
		Call call = top();
		if (call.mCaller == null) {
			return null;
		}

		if (call.getMethodData() != method) {
			String error = "Method exit (" + method.getName()
					+ ") does not match current method ("
					+ call.getMethodData().getName() + ")";

			throw new RuntimeException(error);
		}

		this.mStack.remove(this.mStack.size() - 1);

		if (trace != null) {
			trace.add(new TraceAction(TraceAction.ACTION_EXIT, call));
		}

		Integer num = (Integer) this.mStackMethods.get(method);
		if (num != null) {
			if (num.intValue() == 1) {
				this.mStackMethods.remove(method);
			} else {
				this.mStackMethods.put(method,
						Integer.valueOf(num.intValue() - 1));
			}
		}

		return call;
	}

	Call top() {
		return (Call) this.mStack.get(this.mStack.size() - 1);
	}

	void endTrace(ArrayList<TraceAction> trace) {
		for (int i = this.mStack.size() - 1; i >= 1; i--) {
			Call call = (Call) this.mStack.get(i);
			call.mGlobalEndTime = this.mGlobalEndTime;
			call.mThreadEndTime = this.mThreadEndTime;
			if (trace != null) {
				trace.add(new TraceAction(TraceAction.ACTION_INCOMPLETE, call));
			}
		}
		this.mStack.clear();
		this.mStackMethods.clear();
	}

	void updateRootCallTimeBounds() {
		if (!this.mIsEmpty) {
			this.mRootCall.mGlobalStartTime = this.mGlobalStartTime;
			this.mRootCall.mGlobalEndTime = this.mGlobalEndTime;
			this.mRootCall.mThreadStartTime = this.mThreadStartTime;
			this.mRootCall.mThreadEndTime = this.mThreadEndTime;
		}
	}

	public String toString() {
		return this.mName;
	}

	public int getId() {
		return this.mId;
	}

	public long getCpuTime() {
		return this.mRootCall.mInclusiveCpuTime;
	}

	public long getRealTime() {
		return this.mRootCall.mInclusiveRealTime;
	}
}

/*
 * Location:
 * /Users/frank/Applications/android-sdk-macosx/tools/lib/traceview.jar
 * !/com/android/traceview/ThreadData.class Java compiler version: 6 (50.0)
 * JD-Core Version: 0.7.1
 */