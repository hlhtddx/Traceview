package com.android.traceview;

public class Selection {
	private Action mAction;

	private String mName;

	private Object mValue;

	public Selection(Action action, String name, Object value) {
		this.mAction = action;
		this.mName = name;
		this.mValue = value;
	}

	public static Selection highlight(String name, Object value) {
		return new Selection(Action.Highlight, name, value);
	}

	public static Selection include(String name, Object value) {
		return new Selection(Action.Include, name, value);
	}

	public static Selection exclude(String name, Object value) {
		return new Selection(Action.Exclude, name, value);
	}

	public void setName(String name) {
		this.mName = name;
	}

	public String getName() {
		return this.mName;
	}

	public void setValue(Object value) {
		this.mValue = value;
	}

	public Object getValue() {
		return this.mValue;
	}

	public void setAction(Action action) {
		this.mAction = action;
	}

	public Action getAction() {
		return this.mAction;
	}

	public static enum Action {
		Highlight, Include, Exclude, Aggregate;

		private Action() {
		}
	}
}

/*
 * Location:
 * /Users/frank/Applications/android-sdk-macosx/tools/lib/traceview.jar
 * !/com/android/traceview/Selection.class Java compiler version: 6 (50.0)
 * JD-Core Version: 0.7.1
 */