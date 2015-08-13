package com.android.traceview;

import java.util.ArrayList;
import java.util.Observable;

public class SelectionController extends Observable {
	private ArrayList<Selection> mSelections;

	public void change(ArrayList<Selection> selections, Object arg) {
		this.mSelections = selections;
		setChanged();
		notifyObservers(arg);
	}

	public ArrayList<Selection> getSelections() {
		return this.mSelections;
	}
}

/*
 * Location:
 * /Users/frank/Applications/android-sdk-macosx/tools/lib/traceview.jar
 * !/com/android/traceview/SelectionController.class Java compiler version: 6
 * (50.0) JD-Core Version: 0.7.1
 */