package com.android.traceview;

public class ProfileNode {
	private String mLabel;

	private MethodData mMethodData;

	private ProfileData[] mChildren;

	private boolean mIsParent;

	private boolean mIsRecursive;

	public ProfileNode(String label, MethodData methodData,
			ProfileData[] children, boolean isParent, boolean isRecursive) {
		this.mLabel = label;
		this.mMethodData = methodData;
		this.mChildren = children;
		this.mIsParent = isParent;
		this.mIsRecursive = isRecursive;
	}

	public String getLabel() {
		return this.mLabel;
	}

	public ProfileData[] getChildren() {
		return this.mChildren;
	}

	public boolean isParent() {
		return this.mIsParent;
	}

	public boolean isRecursive() {
		return this.mIsRecursive;
	}
}

/*
 * Location:
 * /Users/frank/Applications/android-sdk-macosx/tools/lib/traceview.jar
 * !/com/android/traceview/ProfileNode.class Java compiler version: 6 (50.0)
 * JD-Core Version: 0.7.1
 */