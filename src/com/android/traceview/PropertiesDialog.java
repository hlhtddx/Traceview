package com.android.traceview;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;

public class PropertiesDialog extends Dialog {
	private HashMap<String, String> mProperties;

	public PropertiesDialog(Shell parent) {
		super(parent);

		setShellStyle(2160);
	}

	public void setProperties(HashMap<String, String> properties) {
		this.mProperties = properties;
	}

	protected void createButtonsForButtonBar(Composite parent) {
		createButton(parent, 0, IDialogConstants.OK_LABEL, true);
	}

	protected Control createDialogArea(Composite parent) {
		Composite container = (Composite) super.createDialogArea(parent);
		GridLayout gridLayout = new GridLayout(1, false);
		gridLayout.marginWidth = 0;
		gridLayout.marginHeight = 0;
		gridLayout.horizontalSpacing = 0;
		gridLayout.verticalSpacing = 0;
		container.setLayout(gridLayout);

		TableViewer tableViewer = new TableViewer(container, 35328);

		tableViewer.getTable().setLinesVisible(true);
		tableViewer.getTable().setHeaderVisible(true);

		TableViewerColumn propertyColumn = new TableViewerColumn(tableViewer, 0);
		propertyColumn.getColumn().setText("Property");
		propertyColumn.setLabelProvider(new ColumnLabelProvider() {
			public String getText(Object element) {
				Map.Entry<String, String> entry = (Map.Entry<String, String>) element;
				return (String) entry.getKey();
			}
		});
		propertyColumn.getColumn().setWidth(400);

		TableViewerColumn valueColumn = new TableViewerColumn(tableViewer, 0);
		valueColumn.getColumn().setText("Value");
		valueColumn.setLabelProvider(new ColumnLabelProvider() {
			public String getText(Object element) {
				Map.Entry<String, String> entry = (Map.Entry<String, String>) element;
				return (String) entry.getValue();
			}
		});
		valueColumn.getColumn().setWidth(200);

		tableViewer.setContentProvider(new ArrayContentProvider());
		tableViewer.setInput(this.mProperties.entrySet().toArray());

		GridData gridData = new GridData();
		gridData.verticalAlignment = 4;
		gridData.horizontalAlignment = 4;
		gridData.grabExcessHorizontalSpace = true;
		gridData.grabExcessVerticalSpace = true;
		tableViewer.getControl().setLayoutData(gridData);

		return container;
	}
}

/*
 * Location:
 * /Users/frank/Applications/android-sdk-macosx/tools/lib/traceview.jar
 * !/com/android/traceview/PropertiesDialog.class Java compiler version: 6
 * (50.0) JD-Core Version: 0.7.1
 */