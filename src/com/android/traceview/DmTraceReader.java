package com.android.traceview;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.BufferUnderflowException;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DmTraceReader extends TraceReader {
	private static final int TRACE_MAGIC = 1464814675;
	private static final int METHOD_TRACE_ENTER = 0;
	private static final int METHOD_TRACE_EXIT = 1;
	private static final int METHOD_TRACE_UNROLL = 2;
	private static final long MIN_CONTEXT_SWITCH_TIME_USEC = 100L;
	private int mVersionNumber;
	private boolean mRegression;
	private ProfileProvider mProfileProvider;
	private String mTraceFileName;
	private MethodData mTopLevel;
	private ArrayList<Call> mCallList;
	private HashMap<String, String> mPropertiesMap;
	private HashMap<Integer, MethodData> mMethodMap;
	private HashMap<Integer, ThreadData> mThreadMap;
	private ThreadData[] mSortedThreads;
	private MethodData[] mSortedMethods;
	private long mTotalCpuTime;
	private long mTotalRealTime;
	private MethodData mContextSwitch;
	private int mRecordSize;
	private ClockSource mClockSource;

	private static enum ClockSource {
		THREAD_CPU, WALL, DUAL;

		private ClockSource() {
		}
	}

	private static final Pattern mIdNamePattern = Pattern
			.compile("(\\d+)\t(.*)");
	static final int PARSE_VERSION = 0;

	public DmTraceReader(String traceFileName, boolean regression)
			throws IOException {
		this.mTraceFileName = traceFileName;
		this.mRegression = regression;
		this.mPropertiesMap = new HashMap<String, String>();
		this.mMethodMap = new HashMap<Integer, MethodData>();
		this.mThreadMap = new HashMap<Integer, ThreadData>();
		this.mCallList = new ArrayList<Call>();

		this.mTopLevel = new MethodData(0, "(toplevel)");
		this.mContextSwitch = new MethodData(-1, "(context switch)");
		this.mMethodMap.put(Integer.valueOf(0), this.mTopLevel);
		this.mMethodMap.put(Integer.valueOf(-1), this.mContextSwitch);
		generateTrees();
	}

	void generateTrees() throws IOException {
		long offset = parseKeys();
		parseData(offset);
		analyzeData();
	}

	public ProfileProvider getProfileProvider() {
		if (this.mProfileProvider == null)
			this.mProfileProvider = new ProfileProvider(this);
		return this.mProfileProvider;
	}

	private MappedByteBuffer mapFile(String filename, long offset)
			throws IOException {
		MappedByteBuffer buffer = null;
		FileInputStream dataFile = new FileInputStream(filename);
		try {
			File file = new File(filename);
			FileChannel fc = dataFile.getChannel();
			buffer = fc.map(FileChannel.MapMode.READ_ONLY, offset,
					file.length() - offset);
			buffer.order(ByteOrder.LITTLE_ENDIAN);

			return buffer;
		} finally {
			dataFile.close();
		}
	}

	private void readDataFileHeader(MappedByteBuffer buffer) {
		int magic = buffer.getInt();
		if (magic != 1464814675) {
			System.err.printf(
					"Error: magic number mismatch; got 0x%x, expected 0x%x\n",
					new Object[] { Integer.valueOf(magic),
							Integer.valueOf(1464814675) });

			throw new RuntimeException();
		}

		int version = buffer.getShort();
		if (version != this.mVersionNumber) {
			System.err
					.printf("Error: version number mismatch; got %d in data header but %d in options\n",
							new Object[] { Integer.valueOf(version),
									Integer.valueOf(this.mVersionNumber) });

			throw new RuntimeException();
		}
		if ((version < 1) || (version > 3)) {
			System.err
					.printf("Error: unsupported trace version number %d.  Please use a newer version of TraceView to read this file.",
							new Object[] { Integer.valueOf(version) });

			throw new RuntimeException();
		}

		int offsetToData = buffer.getShort() - 16;

		buffer.getLong();

		if (version == 1) {
			this.mRecordSize = 9;
		} else if (version == 2) {
			this.mRecordSize = 10;
		} else {
			this.mRecordSize = buffer.getShort();
			offsetToData -= 2;
		}

		while (offsetToData-- > 0) {
			buffer.get();
		}
	}

	private void parseData(long offset) throws IOException {
		MappedByteBuffer buffer = mapFile(this.mTraceFileName, offset);
		readDataFileHeader(buffer);

		ArrayList<TraceAction> trace = null;
		if (this.mClockSource == ClockSource.THREAD_CPU) {
			trace = new ArrayList<TraceAction>();
		}

		boolean haveThreadClock = this.mClockSource != ClockSource.WALL;
		boolean haveGlobalClock = this.mClockSource != ClockSource.THREAD_CPU;

		ThreadData prevThreadData = null;
		for (;;) {
			int threadId;
			int methodId;
			long threadTime;
			long globalTime;
			try {
				int recordSize = this.mRecordSize;

				if (this.mVersionNumber == 1) {
					threadId = buffer.get();
					recordSize--;
				} else {
					threadId = buffer.getShort();
					recordSize -= 2;
				}

				methodId = buffer.getInt();
				recordSize -= 4;

				switch (this.mClockSource) {
				case WALL:
					threadTime = 0L;
					globalTime = buffer.getInt();
					recordSize -= 4;
					break;
				case DUAL:
					threadTime = buffer.getInt();
					globalTime = buffer.getInt();
					recordSize -= 8;
					break;
				case THREAD_CPU:
				default:
					threadTime = buffer.getInt();
					globalTime = 0L;
					recordSize -= 4;
				}

				if (recordSize-- > 0) {
					buffer.get();
					continue;
				}
			} catch (BufferUnderflowException ex) {
				break;
			}
			int methodAction = methodId & 0x3;
			methodId &= 0xFFFFFFFC;
			MethodData methodData = (MethodData) this.mMethodMap.get(Integer
					.valueOf(methodId));
			if (methodData == null) {
				String name = String.format("(0x%1$x)",
						new Object[] { Integer.valueOf(methodId) });
				methodData = new MethodData(methodId, name);
				this.mMethodMap.put(Integer.valueOf(methodId), methodData);
			}

			ThreadData threadData = (ThreadData) this.mThreadMap.get(Integer
					.valueOf(threadId));
			if (threadData == null) {
				String name = String.format("[%1$d]",
						new Object[] { Integer.valueOf(threadId) });
				threadData = new ThreadData(threadId, name, this.mTopLevel);
				this.mThreadMap.put(Integer.valueOf(threadId), threadData);
			}

			long elapsedGlobalTime = 0L;
			if (haveGlobalClock) {
				if (!threadData.mHaveGlobalTime) {
					threadData.mGlobalStartTime = globalTime;
					threadData.mHaveGlobalTime = true;
				} else {
					elapsedGlobalTime = globalTime - threadData.mGlobalEndTime;
				}
				threadData.mGlobalEndTime = globalTime;
			}

			//Check context switching as following conditions:
			// 1. When no global clock, we detect thread switching by Thread Id changing
			// 2. Otherwise, if sleeping time (elapsedGlobalTime - elapsedThreadTime) is longer than 100us, we consider it as a thread switching
			// 3. If no thread time, context switching is skipped
			if (haveThreadClock) {
				long elapsedThreadTime = 0L;
				if (!threadData.mHaveThreadTime) {
					threadData.mThreadStartTime = threadTime;
					threadData.mThreadCurrentTime = threadTime;
					threadData.mHaveThreadTime = true;
				} else {
					elapsedThreadTime = threadTime - threadData.mThreadEndTime;
				}
				threadData.mThreadEndTime = threadTime;

				if (!haveGlobalClock) {
					//No global clock, check if thread id changed
					if ((prevThreadData != null)
							&& (prevThreadData != threadData)) {
						Call switchCall = prevThreadData.enter(
								this.mContextSwitch, trace);
						switchCall.mThreadStartTime = prevThreadData.mThreadEndTime;
						this.mCallList.add(switchCall);

						Call top = threadData.top();
						if (top.getMethodData() == this.mContextSwitch) {
							threadData.exit(this.mContextSwitch, trace);
							long beforeSwitch = elapsedThreadTime / 2L;
							top.mThreadStartTime += beforeSwitch;
							top.mThreadEndTime = top.mThreadStartTime;
						}
					}
					prevThreadData = threadData;

				} else {
					//With global clock, check sleeping time
					long sleepTime = elapsedGlobalTime - elapsedThreadTime;
					if (sleepTime > 100L) {
						Call switchCall = threadData.enter(this.mContextSwitch,
								trace);
						long beforeSwitch = elapsedThreadTime / 2L;
						long afterSwitch = elapsedThreadTime - beforeSwitch;
						switchCall.mGlobalStartTime = (globalTime
								- elapsedGlobalTime + beforeSwitch);
						switchCall.mGlobalEndTime = (globalTime - afterSwitch);
						switchCall.mThreadStartTime = (threadTime - afterSwitch);
						switchCall.mThreadEndTime = switchCall.mThreadStartTime;
						threadData.exit(this.mContextSwitch, trace);
						this.mCallList.add(switchCall);
					}
				}

				Call top = threadData.top();
				top.addCpuTime(elapsedThreadTime);
			}
			Call call;
			switch (methodAction) {
			case 0:
				call = threadData.enter(methodData, trace);
				if (haveGlobalClock) {
					call.mGlobalStartTime = globalTime;
				}
				if (haveThreadClock) {
					call.mThreadStartTime = threadTime;
				}
				this.mCallList.add(call);
				break;

			case 1:
			case 2:
				call = threadData.exit(methodData, trace);
				if (call != null) {
					if (haveGlobalClock) {
						call.mGlobalEndTime = globalTime;
					}
					if (haveThreadClock) {
						call.mThreadEndTime = threadTime;
					}
				}

				break;
			default:
				throw new RuntimeException("Unrecognized method action: "
						+ methodAction);
			}

		}

		for (ThreadData threadData : this.mThreadMap.values()) {
			threadData.endTrace(trace);
		}

		long globalTime;
		if (!haveGlobalClock) {
			globalTime = 0L;
			prevThreadData = null;
			for (TraceAction traceAction : trace) {
				Call call = traceAction.mCall;
				ThreadData threadData = call.getThreadData();

				if (traceAction.mAction == 0) {
					long threadTime = call.mThreadStartTime;
					globalTime += call.mThreadStartTime
							- threadData.mThreadCurrentTime;
					call.mGlobalStartTime = globalTime;
					if (!threadData.mHaveGlobalTime) {
						threadData.mHaveGlobalTime = true;
						threadData.mGlobalStartTime = globalTime;
					}
					threadData.mThreadCurrentTime = threadTime;
				} else if (traceAction.mAction == 1) {
					long threadTime = call.mThreadEndTime;
					globalTime += call.mThreadEndTime
							- threadData.mThreadCurrentTime;
					call.mGlobalEndTime = globalTime;
					threadData.mGlobalEndTime = globalTime;
					threadData.mThreadCurrentTime = threadTime;
				}
				prevThreadData = threadData;
			}
		}

		for (int i = this.mCallList.size() - 1; i >= 0; i--) {
			Call call = (Call) this.mCallList.get(i);

			long realTime = call.mGlobalEndTime - call.mGlobalStartTime;
			call.mExclusiveRealTime = Math.max(realTime
					- call.mInclusiveRealTime, 0L);
			call.mInclusiveRealTime = realTime;

			call.finish();
		}
		this.mTotalCpuTime = 0L;
		this.mTotalRealTime = 0L;
		for (ThreadData threadData : this.mThreadMap.values()) {
			Call rootCall = threadData.getRootCall();
			threadData.updateRootCallTimeBounds();
			rootCall.finish();
			this.mTotalCpuTime += rootCall.mInclusiveCpuTime;
			this.mTotalRealTime += rootCall.mInclusiveRealTime;
		}

		if (this.mRegression) {
			System.out.format("totalCpuTime %dus\n",
					new Object[] { Long.valueOf(this.mTotalCpuTime) });
			System.out.format("totalRealTime %dus\n",
					new Object[] { Long.valueOf(this.mTotalRealTime) });

			dumpThreadTimes();
			dumpCallTimes();
		}
	}

	long parseKeys() throws IOException {
		long offset = 0L;
		BufferedReader in = null;
		try {
			in = new BufferedReader(new InputStreamReader(new FileInputStream(
					this.mTraceFileName), "US-ASCII"));

			int mode = 0;
			String line = null;
			for (;;) {
				line = in.readLine();
				if (line == null) {
					throw new IOException(
							"Key section does not have an *end marker");
				}

				offset += line.length() + 1;
				if (line.startsWith("*")) {
					if (line.equals("*version")) {
						mode = 0;

					} else if (line.equals("*threads")) {
						mode = 1;

					} else if (line.equals("*methods")) {
						mode = 2;
					} else {
						if (line.equals("*end"))
							break;
					}
				} else {
					switch (mode) {
					case 0:
						this.mVersionNumber = Integer.decode(line).intValue();
						mode = 4;
						break;
					case 1:
						parseThread(line);
						break;
					case 2:
						parseMethod(line);
						break;
					case 4:
						parseOption(line);
					}
				}
			}
		} catch (FileNotFoundException ex) {
			System.err.println(ex.getMessage());
		} finally {
			if (in != null) {
				in.close();
			}
		}

		if (this.mClockSource == null) {
			this.mClockSource = ClockSource.THREAD_CPU;
		}

		return offset;
	}

	void parseOption(String line) {
		String[] tokens = line.split("=");
		if (tokens.length == 2) {
			String key = tokens[0];
			String value = tokens[1];
			this.mPropertiesMap.put(key, value);

			if (key.equals("clock")) {
				if (value.equals("thread-cpu")) {
					this.mClockSource = ClockSource.THREAD_CPU;
				} else if (value.equals("wall")) {
					this.mClockSource = ClockSource.WALL;
				} else if (value.equals("dual")) {
					this.mClockSource = ClockSource.DUAL;
				}
			}
		}
	}

	void parseThread(String line) {
		String idStr = null;
		String name = null;
		Matcher matcher = mIdNamePattern.matcher(line);
		if (matcher.find()) {
			idStr = matcher.group(1);
			name = matcher.group(2);
		}
		if (idStr == null)
			return;
		if (name == null) {
			name = "(unknown)";
		}
		int id = Integer.decode(idStr).intValue();
		this.mThreadMap.put(Integer.valueOf(id), new ThreadData(id, name,
				this.mTopLevel));
	}

	void parseMethod(String line) {
		String[] tokens = line.split("\t");
		int id = Long.decode(tokens[0]).intValue();
		String className = tokens[1];
		String methodName = null;
		String signature = null;
		String pathname = null;
		int lineNumber = -1;
		if (tokens.length == 6) {
			methodName = tokens[2];
			signature = tokens[3];
			pathname = tokens[4];
			lineNumber = Integer.decode(tokens[5]).intValue();
			pathname = constructPathname(className, pathname);
		} else if (tokens.length > 2) {
			if (tokens[3].startsWith("(")) {
				methodName = tokens[2];
				signature = tokens[3];
			} else {
				pathname = tokens[2];
				lineNumber = Integer.decode(tokens[3]).intValue();
			}
		}

		this.mMethodMap.put(Integer.valueOf(id), new MethodData(id, className,
				methodName, signature, pathname, lineNumber));
	}

	private String constructPathname(String className, String pathname) {
		int index = className.lastIndexOf('/');
		if ((index > 0) && (index < className.length() - 1)
				&& (pathname.endsWith(".java"))) {
			pathname = className.substring(0, index + 1) + pathname;
		}
		return pathname;
	}

	private void analyzeData() {
		final TimeBase timeBase = getPreferredTimeBase();

		Collection<ThreadData> tv = this.mThreadMap.values();
		this.mSortedThreads = ((ThreadData[]) tv.toArray(new ThreadData[tv
				.size()]));
		Arrays.sort(this.mSortedThreads, new Comparator<ThreadData>() {
			public int compare(ThreadData td1, ThreadData td2) {
				if (timeBase.getTime(td2) > timeBase.getTime(td1))
					return 1;
				if (timeBase.getTime(td2) < timeBase.getTime(td1))
					return -1;
				return td2.getName().compareTo(td1.getName());
			}

		});
		Collection<MethodData> mv = this.mMethodMap.values();

		MethodData[] methods = (MethodData[]) mv.toArray(new MethodData[mv
				.size()]);
		Arrays.sort(methods, new Comparator<MethodData>() {
			public int compare(MethodData md1, MethodData md2) {
				if (timeBase.getElapsedInclusiveTime(md2) > timeBase
						.getElapsedInclusiveTime(md1))
					return 1;
				if (timeBase.getElapsedInclusiveTime(md2) < timeBase
						.getElapsedInclusiveTime(md1))
					return -1;
				return md1.getName().compareTo(md2.getName());
			}

		});
		int nonZero = 0;
		for (MethodData md : methods) {
			if (timeBase.getElapsedInclusiveTime(md) == 0L)
				break;
			nonZero++;
		}

		this.mSortedMethods = new MethodData[nonZero];
		int ii = 0;
		for (MethodData md : methods) {
			if (timeBase.getElapsedInclusiveTime(md) == 0L)
				break;
			md.setRank(ii);
			this.mSortedMethods[(ii++)] = md;
		}

		for (MethodData md : this.mSortedMethods) {
			md.analyzeData(timeBase);
		}

		for (Call call : this.mCallList) {
			call.updateName();
		}

		if (this.mRegression) {
			dumpMethodStats();
		}
	}

	static final int PARSE_THREADS = 1;

	static final int PARSE_METHODS = 2;

	static final int PARSE_OPTIONS = 4;

	public ArrayList<TimeLineView.Record> getThreadTimeRecords() {
		ArrayList<TimeLineView.Record> timeRecs = new ArrayList<TimeLineView.Record>();

		for (ThreadData threadData : this.mSortedThreads) {
			if ((!threadData.isEmpty()) && (threadData.getId() != 0)) {
				TimeLineView.Record record = new TimeLineView.Record(
						threadData, threadData.getRootCall());
				timeRecs.add(record);
			}
		}

		for (Call call : this.mCallList) {
			TimeLineView.Record record = new TimeLineView.Record(
					call.getThreadData(), call);
			timeRecs.add(record);
		}

		if (this.mRegression) {
			dumpTimeRecs(timeRecs);
			System.exit(0);
		}
		return timeRecs;
	}

	private void dumpThreadTimes() {
		System.out.print("\nThread Times\n");
		System.out.print("id  t-start    t-end  g-start    g-end     name\n");
		for (ThreadData threadData : this.mThreadMap.values()) {
			System.out.format(
					"%2d %8d %8d %8d %8d  %s\n",
					new Object[] { Integer.valueOf(threadData.getId()),
							Long.valueOf(threadData.mThreadStartTime),
							Long.valueOf(threadData.mThreadEndTime),
							Long.valueOf(threadData.mGlobalStartTime),
							Long.valueOf(threadData.mGlobalEndTime),
							threadData.getName() });
		}
	}

	private void dumpCallTimes() {
		System.out.print("\nCall Times\n");
		System.out
				.print("id  t-start    t-end  g-start    g-end    excl.    incl.  method\n");
		for (Call call : this.mCallList) {
			System.out.format(
					"%2d %8d %8d %8d %8d %8d %8d  %s\n",
					new Object[] { Integer.valueOf(call.getThreadId()),
							Long.valueOf(call.mThreadStartTime),
							Long.valueOf(call.mThreadEndTime),
							Long.valueOf(call.mGlobalStartTime),
							Long.valueOf(call.mGlobalEndTime),
							Long.valueOf(call.mExclusiveCpuTime),
							Long.valueOf(call.mInclusiveCpuTime),
							call.getMethodData().getName() });
		}
	}

	private void dumpMethodStats() {
		System.out.print("\nMethod Stats\n");
		System.out
				.print("Excl Cpu  Incl Cpu  Excl Real Incl Real    Calls  Method\n");
		for (MethodData md : this.mSortedMethods) {
			System.out.format(
					"%9d %9d %9d %9d %9s  %s\n",
					new Object[] {
							Long.valueOf(md.getElapsedExclusiveCpuTime()),
							Long.valueOf(md.getElapsedInclusiveCpuTime()),
							Long.valueOf(md.getElapsedExclusiveRealTime()),
							Long.valueOf(md.getElapsedInclusiveRealTime()),
							md.getCalls(), md.getProfileName() });
		}
	}

	private void dumpTimeRecs(ArrayList<TimeLineView.Record> timeRecs) {
		System.out.print("\nTime Records\n");
		System.out.print("id  t-start    t-end  g-start    g-end  method\n");
		for (TimeLineView.Record record : timeRecs) {
			Call call = (Call) record.block;
			System.out.format(
					"%2d %8d %8d %8d %8d  %s\n",
					new Object[] { Integer.valueOf(call.getThreadId()),
							Long.valueOf(call.mThreadStartTime),
							Long.valueOf(call.mThreadEndTime),
							Long.valueOf(call.mGlobalStartTime),
							Long.valueOf(call.mGlobalEndTime),
							call.getMethodData().getName() });
		}
	}

	public HashMap<Integer, String> getThreadLabels() {
		HashMap<Integer, String> labels = new HashMap<Integer, String>();
		for (ThreadData t : this.mThreadMap.values()) {
			labels.put(Integer.valueOf(t.getId()), t.getName());
		}
		return labels;
	}

	public MethodData[] getMethods() {
		return this.mSortedMethods;
	}

	public ThreadData[] getThreads() {
		return this.mSortedThreads;
	}

	public long getTotalCpuTime() {
		return this.mTotalCpuTime;
	}

	public long getTotalRealTime() {
		return this.mTotalRealTime;
	}

	public boolean haveCpuTime() {
		return this.mClockSource != ClockSource.WALL;
	}

	public boolean haveRealTime() {
		return this.mClockSource != ClockSource.THREAD_CPU;
	}

	public HashMap<String, String> getProperties() {
		return this.mPropertiesMap;
	}

	public TimeBase getPreferredTimeBase() {
		if (this.mClockSource == ClockSource.WALL) {
			return TimeBase.REAL_TIME;
		}
		return TimeBase.CPU_TIME;
	}

	public String getClockSource() {
		switch (this.mClockSource) {
		case THREAD_CPU:
			return "cpu time";
		case WALL:
			return "real time";
		case DUAL:
			return "real time, dual clock";
		}
		return null;
	}
}

/*
 * Location:
 * /Users/frank/Applications/android-sdk-macosx/tools/lib/traceview.jar
 * !/com/android/traceview/DmTraceReader.class Java compiler version: 6 (50.0)
 * JD-Core Version: 0.7.1
 */