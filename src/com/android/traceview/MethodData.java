package com.android.traceview;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;

import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;

public class MethodData {
	private int mId;
	private int mRank = -1;
	private String mClassName;
	private String mMethodName;
	private String mSignature;
	private String mName;
	private String mProfileName;
	private String mPathname;
	private int mLineNumber;
	private long mElapsedExclusiveCpuTime;
	private long mElapsedInclusiveCpuTime;
	private long mTopExclusiveCpuTime;
	private long mElapsedExclusiveRealTime;
	private long mElapsedInclusiveRealTime;
	private long mTopExclusiveRealTime;
	private int[] mNumCalls = new int[2];

	private Color mColor;

	private Color mFadedColor;

	private Image mImage;

	private Image mFadedImage;
	private HashMap<Integer, ProfileData> mParents;
	private HashMap<Integer, ProfileData> mChildren;
	private HashMap<Integer, ProfileData> mRecursiveParents;
	private HashMap<Integer, ProfileData> mRecursiveChildren;
	private ProfileNode[] mProfileNodes;
	private int mX;
	private int mY;
	private double mWeight;

	public MethodData(int id, String className) {
		this.mId = id;
		this.mClassName = className;
		this.mMethodName = null;
		this.mSignature = null;
		this.mPathname = null;
		this.mLineNumber = -1;
		computeName();
		computeProfileName();
	}

	public MethodData(int id, String className, String methodName,
			String signature, String pathname, int lineNumber) {
		this.mId = id;
		this.mClassName = className;
		this.mMethodName = methodName;
		this.mSignature = signature;
		this.mPathname = pathname;
		this.mLineNumber = lineNumber;
		computeName();
		computeProfileName();
	}

	public double addWeight(int x, int y, double weight) {
		if ((this.mX == x) && (this.mY == y)) {
			this.mWeight += weight;
		} else {
			this.mX = x;
			this.mY = y;
			this.mWeight = weight;
		}
		return this.mWeight;
	}

	public void clearWeight() {
		this.mWeight = 0.0D;
	}

	public int getRank() {
		return this.mRank;
	}

	public void setRank(int rank) {
		this.mRank = rank;
		computeProfileName();
	}

	public void addElapsedExclusive(long cpuTime, long realTime) {
		this.mElapsedExclusiveCpuTime += cpuTime;
		this.mElapsedExclusiveRealTime += realTime;
	}

	public void addElapsedInclusive(long cpuTime, long realTime,
			boolean isRecursive, Call parent) {
		if (!isRecursive) {
			this.mElapsedInclusiveCpuTime += cpuTime;
			this.mElapsedInclusiveRealTime += realTime;
			this.mNumCalls[0] += 1;
		} else {
			this.mNumCalls[1] += 1;
		}

		if (parent == null) {
			return;
		}

		MethodData parentMethod = parent.getMethodData();
		if (parent.isRecursive()) {
			parentMethod.mRecursiveChildren = updateInclusive(cpuTime,
					realTime, parentMethod, this, false,
					parentMethod.mRecursiveChildren);
		} else {
			parentMethod.mChildren = updateInclusive(cpuTime, realTime,
					parentMethod, this, false, parentMethod.mChildren);
		}

		if (isRecursive) {
			this.mRecursiveParents = updateInclusive(cpuTime, realTime, this,
					parentMethod, true, this.mRecursiveParents);
		} else {
			this.mParents = updateInclusive(cpuTime, realTime, this,
					parentMethod, true, this.mParents);
		}
	}

	private HashMap<Integer, ProfileData> updateInclusive(long cpuTime,
			long realTime, MethodData contextMethod, MethodData elementMethod,
			boolean elementIsParent, HashMap<Integer, ProfileData> map) {
		if (map == null) {
			map = new HashMap<Integer, ProfileData>(4);
		} else {
			ProfileData profileData = (ProfileData) map.get(Integer
					.valueOf(elementMethod.mId));
			if (profileData != null) {
				profileData.addElapsedInclusive(cpuTime, realTime);
				return map;
			}
		}

		ProfileData elementData = new ProfileData(contextMethod, elementMethod,
				elementIsParent);

		elementData.setElapsedInclusive(cpuTime, realTime);
		elementData.setNumCalls(1);
		map.put(Integer.valueOf(elementMethod.mId), elementData);
		return map;
	}

	public void analyzeData(TimeBase timeBase) {
		ProfileData[] sortedParents = sortProfileData(this.mParents, timeBase);
		ProfileData[] sortedChildren = sortProfileData(this.mChildren, timeBase);
		ProfileData[] sortedRecursiveParents = sortProfileData(
				this.mRecursiveParents, timeBase);
		ProfileData[] sortedRecursiveChildren = sortProfileData(
				this.mRecursiveChildren, timeBase);

		sortedChildren = addSelf(sortedChildren);

		ArrayList<ProfileNode> nodes = new ArrayList<ProfileNode>();

		if (this.mParents != null) {
			ProfileNode profileNode = new ProfileNode("Parents", this,
					sortedParents, true, false);

			nodes.add(profileNode);
		}
		if (this.mChildren != null) {
			ProfileNode profileNode = new ProfileNode("Children", this,
					sortedChildren, false, false);

			nodes.add(profileNode);
		}
		if (this.mRecursiveParents != null) {
			ProfileNode profileNode = new ProfileNode(
					"Parents while recursive", this, sortedRecursiveParents,
					true, true);

			nodes.add(profileNode);
		}
		if (this.mRecursiveChildren != null) {
			ProfileNode profileNode = new ProfileNode(
					"Children while recursive", this, sortedRecursiveChildren,
					false, true);

			nodes.add(profileNode);
		}
		this.mProfileNodes = ((ProfileNode[]) nodes
				.toArray(new ProfileNode[nodes.size()]));
	}

	private ProfileData[] sortProfileData(HashMap<Integer, ProfileData> map,
			final TimeBase timeBase) {
		if (map == null) {
			return null;
		}

		Collection<ProfileData> values = map.values();
		ProfileData[] sorted = (ProfileData[]) values
				.toArray(new ProfileData[values.size()]);

		Arrays.sort(sorted, new Comparator<ProfileData>() {
			public int compare(ProfileData pd1, ProfileData pd2) {
				if (timeBase.getElapsedInclusiveTime(pd2) > timeBase
						.getElapsedInclusiveTime(pd1))
					return 1;
				if (timeBase.getElapsedInclusiveTime(pd2) < timeBase
						.getElapsedInclusiveTime(pd1))
					return -1;
				return 0;
			}
		});
		return sorted;
	}

	private ProfileData[] addSelf(ProfileData[] children) {
		ProfileData[] pdata;
		if (children == null) {
			pdata = new ProfileData[1];
		} else {
			pdata = new ProfileData[children.length + 1];
			System.arraycopy(children, 0, pdata, 1, children.length);
		}
		pdata[0] = new ProfileSelf(this);
		return pdata;
	}

	public void addTopExclusive(long cpuTime, long realTime) {
		this.mTopExclusiveCpuTime += cpuTime;
		this.mTopExclusiveRealTime += realTime;
	}

	public long getTopExclusiveCpuTime() {
		return this.mTopExclusiveCpuTime;
	}

	public long getTopExclusiveRealTime() {
		return this.mTopExclusiveRealTime;
	}

	public int getId() {
		return this.mId;
	}

	private void computeName() {
		if (this.mMethodName == null) {
			this.mName = this.mClassName;
			return;
		}

		StringBuilder sb = new StringBuilder();
		sb.append(this.mClassName);
		sb.append(".");
		sb.append(this.mMethodName);
		sb.append(" ");
		sb.append(this.mSignature);
		this.mName = sb.toString();
	}

	public String getName() {
		return this.mName;
	}

	public String getClassName() {
		return this.mClassName;
	}

	public String getMethodName() {
		return this.mMethodName;
	}

	public String getProfileName() {
		return this.mProfileName;
	}

	public String getSignature() {
		return this.mSignature;
	}

	public void computeProfileName() {
		if (this.mRank == -1) {
			this.mProfileName = this.mName;
			return;
		}

		StringBuilder sb = new StringBuilder();
		sb.append(this.mRank);
		sb.append(" ");
		sb.append(getName());
		this.mProfileName = sb.toString();
	}

	public String getCalls() {
		return String.format(
				"%d+%d",
				new Object[] { Integer.valueOf(this.mNumCalls[0]),
						Integer.valueOf(this.mNumCalls[1]) });
	}

	public int getTotalCalls() {
		return this.mNumCalls[0] + this.mNumCalls[1];
	}

	public Color getColor() {
		return this.mColor;
	}

	public void setColor(Color color) {
		this.mColor = color;
	}

	public void setImage(Image image) {
		this.mImage = image;
	}

	public Image getImage() {
		return this.mImage;
	}

	public String toString() {
		return getName();
	}

	public long getElapsedExclusiveCpuTime() {
		return this.mElapsedExclusiveCpuTime;
	}

	public long getElapsedExclusiveRealTime() {
		return this.mElapsedExclusiveRealTime;
	}

	public long getElapsedInclusiveCpuTime() {
		return this.mElapsedInclusiveCpuTime;
	}

	public long getElapsedInclusiveRealTime() {
		return this.mElapsedInclusiveRealTime;
	}

	public void setFadedColor(Color fadedColor) {
		this.mFadedColor = fadedColor;
	}

	public Color getFadedColor() {
		return this.mFadedColor;
	}

	public void setFadedImage(Image fadedImage) {
		this.mFadedImage = fadedImage;
	}

	public Image getFadedImage() {
		return this.mFadedImage;
	}

	public void setPathname(String pathname) {
		this.mPathname = pathname;
	}

	public String getPathname() {
		return this.mPathname;
	}

	public void setLineNumber(int lineNumber) {
		this.mLineNumber = lineNumber;
	}

	public int getLineNumber() {
		return this.mLineNumber;
	}

	public ProfileNode[] getProfileNodes() {
		return this.mProfileNodes;
	}

	public static class Sorter implements Comparator<MethodData> {
		private Column mColumn;
		private Direction mDirection;

		public int compare(MethodData md1, MethodData md2) {
			if (this.mColumn == Column.BY_NAME) {
				int result = md1.getName().compareTo(md2.getName());
				return this.mDirection == Direction.INCREASING ? result
						: -result;
			}
			if (this.mColumn == Column.BY_INCLUSIVE_CPU_TIME) {
				if (md2.getElapsedInclusiveCpuTime() > md1
						.getElapsedInclusiveCpuTime())
					return this.mDirection == Direction.INCREASING ? -1 : 1;
				if (md2.getElapsedInclusiveCpuTime() < md1
						.getElapsedInclusiveCpuTime())
					return this.mDirection == Direction.INCREASING ? 1 : -1;
				return md1.getName().compareTo(md2.getName());
			}
			if (this.mColumn == Column.BY_EXCLUSIVE_CPU_TIME) {
				if (md2.getElapsedExclusiveCpuTime() > md1
						.getElapsedExclusiveCpuTime())
					return this.mDirection == Direction.INCREASING ? -1 : 1;
				if (md2.getElapsedExclusiveCpuTime() < md1
						.getElapsedExclusiveCpuTime())
					return this.mDirection == Direction.INCREASING ? 1 : -1;
				return md1.getName().compareTo(md2.getName());
			}
			if (this.mColumn == Column.BY_INCLUSIVE_REAL_TIME) {
				if (md2.getElapsedInclusiveRealTime() > md1
						.getElapsedInclusiveRealTime())
					return this.mDirection == Direction.INCREASING ? -1 : 1;
				if (md2.getElapsedInclusiveRealTime() < md1
						.getElapsedInclusiveRealTime())
					return this.mDirection == Direction.INCREASING ? 1 : -1;
				return md1.getName().compareTo(md2.getName());
			}
			if (this.mColumn == Column.BY_EXCLUSIVE_REAL_TIME) {
				if (md2.getElapsedExclusiveRealTime() > md1
						.getElapsedExclusiveRealTime())
					return this.mDirection == Direction.INCREASING ? -1 : 1;
				if (md2.getElapsedExclusiveRealTime() < md1
						.getElapsedExclusiveRealTime())
					return this.mDirection == Direction.INCREASING ? 1 : -1;
				return md1.getName().compareTo(md2.getName());
			}
			if (this.mColumn == Column.BY_CALLS) {
				int result = md1.getTotalCalls() - md2.getTotalCalls();
				if (result == 0)
					return md1.getName().compareTo(md2.getName());
				return this.mDirection == Direction.INCREASING ? result
						: -result;
			}
			if (this.mColumn == Column.BY_CPU_TIME_PER_CALL) {
				double time1 = md1.getElapsedInclusiveCpuTime();
				time1 /= md1.getTotalCalls();
				double time2 = md2.getElapsedInclusiveCpuTime();
				time2 /= md2.getTotalCalls();
				double diff = time1 - time2;
				int result = 0;
				if (diff < 0.0D) {
					result = -1;
				} else if (diff > 0.0D)
					result = 1;
				if (result == 0)
					return md1.getName().compareTo(md2.getName());
				return this.mDirection == Direction.INCREASING ? result
						: -result;
			}
			if (this.mColumn == Column.BY_REAL_TIME_PER_CALL) {
				double time1 = md1.getElapsedInclusiveRealTime();
				time1 /= md1.getTotalCalls();
				double time2 = md2.getElapsedInclusiveRealTime();
				time2 /= md2.getTotalCalls();
				double diff = time1 - time2;
				int result = 0;
				if (diff < 0.0D) {
					result = -1;
				} else if (diff > 0.0D)
					result = 1;
				if (result == 0)
					return md1.getName().compareTo(md2.getName());
				return this.mDirection == Direction.INCREASING ? result
						: -result;
			}
			return 0;
		}

		public void setColumn(Column column) {
			if (this.mColumn == column) {
				if (this.mDirection == Direction.INCREASING) {
					this.mDirection = Direction.DECREASING;
				} else {
					this.mDirection = Direction.INCREASING;
				}
			} else if (column == Column.BY_NAME) {
				this.mDirection = Direction.INCREASING;
			} else {
				this.mDirection = Direction.DECREASING;
			}
			this.mColumn = column;
		}

		public Column getColumn() {
			return this.mColumn;
		}

		public void setDirection(Direction direction) {
			this.mDirection = direction;
		}

		public Direction getDirection() {
			return this.mDirection;
		}

		public static enum Column {
			BY_NAME, BY_EXCLUSIVE_CPU_TIME, BY_EXCLUSIVE_REAL_TIME, BY_INCLUSIVE_CPU_TIME, BY_INCLUSIVE_REAL_TIME, BY_CALLS, BY_REAL_TIME_PER_CALL, BY_CPU_TIME_PER_CALL;

			private Column() {
			}
		}

		public static enum Direction {
			INCREASING, DECREASING;

			private Direction() {
			}
		}
	}
}

/*
 * Location:
 * /Users/frank/Applications/android-sdk-macosx/tools/lib/traceview.jar
 * !/com/android/traceview/MethodData.class Java compiler version: 6 (50.0)
 * JD-Core Version: 0.7.1
 */