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
package org.eclipse.jdt.ui.tests.refactoring;

import java.util.ArrayList;
import java.util.List;

import junit.framework.Assert;

import org.eclipse.core.resources.IResource;

import org.eclipse.ltk.core.refactoring.participants.CopyArguments;
import org.eclipse.ltk.core.refactoring.participants.MoveArguments;
import org.eclipse.ltk.core.refactoring.participants.RenameArguments;

import org.eclipse.jdt.core.IJavaElement;

import org.eclipse.jdt.internal.corext.util.JavaElementResourceMapping;


public class ParticipantTesting {
	
	public static void reset() {
		TestCreateParticipantShared.reset();
		TestDeleteParticipantShared.reset();
		TestMoveParticipantShared.reset();
		TestRenameParticipantShared.reset();
		TestCopyParticipantShared.reset();
		
		TestCreateParticipantSingle.reset();
		TestDeleteParticipantSingle.reset();
		TestMoveParticipantSingle.reset();
		TestRenameParticipantSingle.reset();
		TestCopyParticipantSingle.reset();
	}
	
	public static String[] createHandles(Object object) {
		return createHandles(new Object[] { object });
	}
	
	public static String[] createHandles(Object obj1, Object obj2) {
		return createHandles(new Object[] { obj1, obj2 });
	}
	
	public static String[] createHandles(Object obj1, Object obj2, Object obj3) {
		return createHandles(new Object[] { obj1, obj2, obj3 });
	}
	
	public static String[] createHandles(Object obj1, Object obj2, Object obj3, Object obj4) {
		return createHandles(new Object[] { obj1, obj2, obj3, obj4 });
	}
		
	public static String[] createHandles(Object[] elements) {
		List result= new ArrayList();
		for (int i= 0; i < elements.length; i++) {
			Object element= elements[i];
			if (element instanceof IJavaElement) {
				result.add(((IJavaElement)element).getHandleIdentifier());
			} else if (element instanceof IResource) {
				result.add(((IResource)element).getFullPath().toString());
			} else if (element instanceof JavaElementResourceMapping) {
				result.add(((JavaElementResourceMapping)element).
					getJavaElement().getHandleIdentifier() + "_mapping");
			}
		}
		return (String[])result.toArray(new String[result.size()]);
	}
	
	public static void testRename(String[] handles, RenameArguments[] args) {
		Assert.assertEquals(handles.length, args.length);
		if (handles.length == 0) {
			TestRenameParticipantShared.testNumberOfElements(0);
			TestRenameParticipantSingle.testNumberOfInstances(0);
		} else {
			testElementsShared(handles, TestRenameParticipantShared.fgInstance.fHandles);
			TestRenameParticipantShared.testArguments(args);
			
			TestRenameParticipantSingle.testNumberOfInstances(handles.length);
			TestRenameParticipantSingle.testElements(handles);
			TestRenameParticipantSingle.testArguments(args);
		}
	}
	
	public static void testMove(String[] handles, MoveArguments[] args) {
		Assert.assertEquals(handles.length, args.length);
		if (handles.length == 0) {
			TestMoveParticipantShared.testNumberOfElements(0);
			TestMoveParticipantSingle.testNumberOfInstances(0);
		} else {
			testElementsShared(handles, TestMoveParticipantShared.fgInstance.fHandles);
			TestMoveParticipantShared.testArguments(args);
			
			TestMoveParticipantSingle.testNumberOfInstances(handles.length);
			TestMoveParticipantSingle.testElements(handles);
			TestMoveParticipantSingle.testArguments(args);
		}
	}
	
	public static void testDelete(String[] handles) {
		if (handles.length == 0) {
			TestDeleteParticipantShared.testNumberOfElements(0);
			TestDeleteParticipantSingle.testNumberOfInstances(0);
		} else {
			testElementsShared(handles, TestDeleteParticipantShared.fgInstance.fHandles);
			
			TestDeleteParticipantSingle.testNumberOfInstances(handles.length);
			TestDeleteParticipantSingle.testElements(handles);
		}
	}	
	
	public static void testCreate(String[] handles) {
		if (handles.length == 0)  {
			TestCreateParticipantShared.testNumberOfElements(0);
			TestCreateParticipantSingle.testNumberOfInstances(0);
		} else {
			testElementsShared(handles, TestCreateParticipantShared.fgInstance.fHandles);
			
			TestCreateParticipantSingle.testNumberOfInstances(handles.length);
			TestCreateParticipantSingle.testElements(handles);
		}
	}
	
	public static void testCopy(String[] handles, CopyArguments[] arguments) {
		if (handles.length == 0)  {
			TestCopyParticipantShared.testNumberOfElements(0);
			TestCopyParticipantSingle.testNumberOfInstances(0);
		} else {
			testElementsShared(handles, TestCopyParticipantShared.fgInstance.fHandles);
			TestCopyParticipantShared.testArguments(arguments);
			
			TestCopyParticipantSingle.testNumberOfInstances(handles.length);
			TestCopyParticipantSingle.testElements(handles);
			TestCopyParticipantSingle.testArguments(arguments);
		}
	}
	
	public static void testSimilarElements(List similarList, List similarNewNameList, List similarNewHandleList) {
		Assert.assertEquals(similarList.size(), similarNewNameList.size());
		if (similarList.size() == 0) {
			TestRenameParticipantShared.testNumberOfSimilarElements(0);
		} else {
			TestRenameParticipantShared.testSimilarElements(similarList, similarNewNameList, similarNewHandleList);
		}
		
	}	
	
	private static void testElementsShared(String[] handles, List list) {
		for (int i= 0; i < handles.length; i++) {
			String handle= handles[i];
			Assert.assertTrue("Handle not found: " + handle, list.contains(handle));
		}
		testNumberOfElements(handles.length, list);
	}
	
	private static void testNumberOfElements(int expected, List list) {
		if (expected == 0 && list == null)
			return;
		Assert.assertEquals(expected, list.size());
	}	
}