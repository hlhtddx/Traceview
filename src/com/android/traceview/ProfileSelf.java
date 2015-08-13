package com.android.traceview;

public class ProfileSelf extends ProfileData {
	public ProfileSelf(MethodData methodData) {
		this.mElement = methodData;
		this.mContext = methodData;
	}

	public String getProfileName() {
		return "self";
	}

	public long getElapsedInclusiveCpuTime() {
		return this.mElement.getTopExclusiveCpuTime();
	}

	public long getElapsedInclusiveRealTime() {
		return this.mElement.getTopExclusiveRealTime();
	}
}

/*
 * Location:
 * /Users/frank/Applications/android-sdk-macosx/tools/lib/traceview.jar
 * !/com/android/traceview/ProfileSelf.class Java compiler version: 6 (50.0)
 * JD-Core Version: 0.7.1
 */