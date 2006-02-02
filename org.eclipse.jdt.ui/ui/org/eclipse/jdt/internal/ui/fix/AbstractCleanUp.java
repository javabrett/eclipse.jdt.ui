/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.fix;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

import org.eclipse.jface.dialogs.IDialogSettings;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;

public abstract class AbstractCleanUp implements ICleanUp {

	protected static IDialogSettings getSection(IDialogSettings settings, String sectionName) {
		IDialogSettings section= settings.getSection(sectionName);
		if (section == null)
			section= settings.addNewSection(sectionName);
		return section;
	}

	private static final String SETTINGS_FLAG_NAME= "flag"; //$NON-NLS-1$
	private static final String TAB= "    "; //$NON-NLS-1$
	
	private int fFlags;
	
	protected AbstractCleanUp(IDialogSettings settings, int defaultFlag) {

		if (settings.get(SETTINGS_FLAG_NAME) == null)
			settings.put(SETTINGS_FLAG_NAME, defaultFlag);
		
		fFlags= settings.getInt(SETTINGS_FLAG_NAME);
	}

	protected AbstractCleanUp(int flag) {
		fFlags= flag;
	}

	public void saveSettings(IDialogSettings settings) {
		settings.put(SETTINGS_FLAG_NAME, fFlags);
	}

	protected void setFlag(int flag, boolean b) {
		if (!isFlag(flag) && b) {
			fFlags |= flag;
		} else if (isFlag(flag) && !b) {
			fFlags &= ~flag;
		}
	}

	protected boolean isFlag(int flag) {
		return (fFlags & flag) != 0;
	}

	protected Button addCheckBox(Composite parent, final int flag, String label) {
		return addCheckBox(parent, flag, label, new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				setFlag(flag, ((Button)e.getSource()).getSelection());
			}
		});
	}

	protected Button addCheckBox(Composite parent, final int flag, String label, SelectionAdapter adapter) {
		Button button= new Button(parent, SWT.CHECK);
		button.setText(label);
		button.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
		button.setSelection(isFlag(flag));
		button.addSelectionListener(adapter);
		return button;
	}
	
	protected Button addRadioButton(Composite parent, final int flag, String label) {
		return addRadioButton(parent, flag, label, new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				setFlag(flag, ((Button)e.getSource()).getSelection());
			}
		});
	}

	protected Button addRadioButton(Composite parent, final int flag, String label, SelectionAdapter adapter) {
		Button button= new Button(parent, SWT.RADIO);
		button.setText(label);
		button.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
		button.setSelection(isFlag(flag));
		button.addSelectionListener(adapter);
		return button;
	}
	
	protected Button[] createSubGroup(Composite parent, Button controlButton, final int style, final int[] flags, final String[] labels, final int[] uiFlags, boolean isVertical) {
		Composite sub= new Composite(parent, SWT.NONE);
		sub.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		if (isVertical) {
			sub.setLayout(new GridLayout(2, false));
		} else {
			sub.setLayout(new GridLayout(flags.length + 1, false));
			addTab(sub);
		}

		final Button[] buttons= new Button[flags.length];
		for (int i= 0; i < buttons.length; i++) {
			if (isVertical)
				addTab(sub);
			if (style == SWT.CHECK) {
				buttons[i]= addCheckBox(sub, flags[i], labels[i]);
			} else {
				buttons[i]= addRadioButton(sub, flags[i], labels[i]);
			}
			final int index= i;
			buttons[i].addSelectionListener(new SelectionAdapter() {
				public void widgetSelected(SelectionEvent e) {
					boolean isSelected= ((Button)e.getSource()).getSelection();
					setFlag(uiFlags[index], isSelected);
				}
				
			});
		}
		
		if (hasFlag(flags)) {
			controlButton.setSelection(true);
			for (int i= 0; i < flags.length; i++) {
				if (isFlag(flags[i])) {
					setFlag(uiFlags[i], true);
				} else {
					setFlag(uiFlags[i], false);
				}
			}
		} else {
			controlButton.setSelection(false);
			boolean hasCheck= false;
			for (int i= 0; i < buttons.length; i++) {
				Button b= buttons[i];
				b.setEnabled(false);
				boolean flag= isFlag(uiFlags[i]);
				if (flag) {
					if (style == SWT.CHECK) {
						b.setSelection(true);
					} else if (!hasCheck) {
						b.setSelection(true);
					}
					hasCheck= true;
				} else {
					b.setSelection(false);
				}
			}
			if (style == SWT.RADIO && !hasCheck) {
				buttons[0].setSelection(true);
				setFlag(uiFlags[0], true);
			}
		}

		controlButton.addSelectionListener(new SelectionAdapter() {

			public void widgetSelected(SelectionEvent e) {
				boolean isSelected= ((Button)e.getSource()).getSelection();
				for (int i= 0; i < buttons.length; i++) {
					buttons[i].setEnabled(isSelected);
				}
				if (!isSelected) {
					for (int i= 0; i < flags.length; i++) {
						setFlag(flags[i], false);
					}
				} else {
					for (int i= 0; i < flags.length; i++) {
						setFlag(flags[i], buttons[i].getSelection());
					}
				}
			}
			
		});
		return buttons;
	}
	
	private boolean hasFlag(int[] flags) {
		for (int i= 0; i < flags.length; i++) {
			if (isFlag(flags[i]))
				return true;
		}
		return false;
	}
	
	protected void addTab(Composite composite) {
		Label tab= new Label(composite, SWT.NONE);
		tab.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false));
		tab.setText(TAB);
	}
	
	/**
	 * {@inheritDoc}
	 */
	public void beginCleanUp(IJavaProject project, ICompilationUnit[] compilationUnits, IProgressMonitor monitor) throws CoreException {
		if (monitor != null)
			monitor.done();
		//Default do nothing
	}

	/**
	 * {@inheritDoc}
	 */
	public void endCleanUp() throws CoreException {
		//Default do nothing
	}
	
}
