package com.android.traceview;

abstract interface TimeBase {
	public static final TimeBase CPU_TIME = new CpuTimeBase();
	public static final TimeBase REAL_TIME = new RealTimeBase();

	public abstract long getTime(ThreadData paramThreadData);

	public abstract long getElapsedInclusiveTime(MethodData paramMethodData);

	public abstract long getElapsedExclusiveTime(MethodData paramMethodData);

	public abstract long getElapsedInclusiveTime(ProfileData paramProfileData);

	public static final class CpuTimeBase implements TimeBase {
		public long getTime(ThreadData threadData) {
			return threadData.getCpuTime();
		}

		public long getElapsedInclusiveTime(MethodData methodData) {
			return methodData.getElapsedInclusiveCpuTime();
		}

		public long getElapsedExclusiveTime(MethodData methodData) {
			return methodData.getElapsedExclusiveCpuTime();
		}

		public long getElapsedInclusiveTime(ProfileData profileData) {
			return profileData.getElapsedInclusiveCpuTime();
		}
	}

	public static final class RealTimeBase implements TimeBase {
		public long getTime(ThreadData threadData) {
			return threadData.getRealTime();
		}

		public long getElapsedInclusiveTime(MethodData methodData) {
			return methodData.getElapsedInclusiveRealTime();
		}

		public long getElapsedExclusiveTime(MethodData methodData) {
			return methodData.getElapsedExclusiveRealTime();
		}

		public long getElapsedInclusiveTime(ProfileData profileData) {
			return profileData.getElapsedInclusiveRealTime();
		}
	}
}

/*
 * Location:
 * /Users/frank/Applications/android-sdk-macosx/tools/lib/traceview.jar
 * !/com/android/traceview/TimeBase.class Java compiler version: 6 (50.0)
 * JD-Core Version: 0.7.1
 */