/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.core.refactoring.rename;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import java.util.Map;
import java.util.Set;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.core.refactoring.AbstractRefactoringASTAnalyzer;
import org.eclipse.jdt.internal.core.refactoring.Assert;
import org.eclipse.jdt.internal.core.refactoring.Checks;
import org.eclipse.jdt.internal.core.refactoring.RefactoringCoreMessages;
import org.eclipse.jdt.internal.core.refactoring.base.IChange;
import org.eclipse.jdt.internal.core.refactoring.base.Refactoring;
import org.eclipse.jdt.internal.core.refactoring.base.RefactoringStatus;
import org.eclipse.jdt.internal.core.refactoring.tagging.IMultiRenameRefactoring;
import org.eclipse.jdt.internal.core.refactoring.tagging.IReferenceUpdatingRefactoring;
import org.eclipse.jdt.internal.core.refactoring.text.ITextBufferChange;
import org.eclipse.jdt.internal.core.refactoring.text.ITextBufferChangeCreator;

public class RenameParametersRefactoring extends Refactoring implements IMultiRenameRefactoring, IReferenceUpdatingRefactoring{
	private Map fRenamings;
	private ITextBufferChangeCreator fTextBufferChangeCreator;
	private boolean fUpdateReferences;
	private IMethod fMethod;
	
	public RenameParametersRefactoring(ITextBufferChangeCreator changeCreator, IMethod method){
		Assert.isNotNull(method);
		Assert.isNotNull(changeCreator);
		fMethod= method;
		setOldParameterNames();
		fTextBufferChangeCreator= changeCreator;
		fUpdateReferences= true;
	}
	
	/* non java-doc
	 * @see Refactoring#checkPreconditions(IProgressMonitor)
	 */
	public RefactoringStatus checkPreconditions(IProgressMonitor pm) throws JavaModelException{
		RefactoringStatus result= checkPreactivation();
		if (result.hasFatalError())
			return result;
		result.merge(super.checkPreconditions(pm));
		return result;
	}
	
	/* non java-doc 
	 * @see IRefactoring#getName
	 */
	public String getName(){
		return RefactoringCoreMessages.getString("RenameParametersRefactoring.rename_parameters"); //$NON-NLS-1$
	}
	
	public IMethod getMethod(){
		return fMethod;
	}
	
	/*
	 * @see IReferenceUpdatingRefactoring#canEnableUpdateReferences()
	 */
	public boolean canEnableUpdateReferences() {
		return true;
	}
	
	/*
	 *@see  IReferenceUpdatingRefactoring#setUpdateReferences
	 */
	public void setUpdateReferences(boolean update){
		fUpdateReferences= update;
	}

	/*
	 * @see  IReferenceUpdatingRefactoring#getUpdateReferences
	 */	
	public boolean getUpdateReferences(){
		return fUpdateReferences;
	}
	
	//-- preconditions
	
	/* non java-doc 
	 * @see IPreactivatedRefactoring#getPreactivation
	 */
	public RefactoringStatus checkPreactivation() throws JavaModelException{
		RefactoringStatus result= new RefactoringStatus();
		result.merge(checkAvailability(fMethod));
		if (fRenamings == null || fRenamings.isEmpty())
			result.addFatalError(RefactoringCoreMessages.getString("RenameParametersRefactoring.no_parameters"));  //$NON-NLS-1$
		return result;
	}
	
	/* non java-doc 
	 * @see Refactoring#getActivation
	 */
	public RefactoringStatus checkActivation(IProgressMonitor pm) throws JavaModelException{
		return Checks.checkIfCuBroken(fMethod);
	}

	public RefactoringStatus checkNewNames(){
		if (fRenamings == null || fRenamings.isEmpty())
			return new RefactoringStatus();
		RefactoringStatus result= new RefactoringStatus();
		if (!anythingRenamed())
			result.addFatalError(RefactoringCoreMessages.getString("RenameParametersRefactoring.no_change")); //$NON-NLS-1$
		if (result.isOK())
			result.merge(checkForDuplicateNames());
		if (result.isOK())	
			result.merge(checkAllNames());
		return result;
	}
	
	/* non java-doc 
	 * @see Refactoring#getInput
	 */
	public RefactoringStatus checkInput(IProgressMonitor pm) throws JavaModelException{
		try{
			RefactoringStatus result= new RefactoringStatus();
			pm.beginTask("", 10); //$NON-NLS-1$
			result.merge(Checks.checkIfCuBroken(fMethod));
			if (result.hasFatalError())
				return result;
			if (Arrays.asList(getUnsavedFiles()).contains(Refactoring.getResource(fMethod)))
				result.addFatalError(RefactoringCoreMessages.getString("RenameParametersRefactoring.not_saved"));		 //$NON-NLS-1$
			if (result.hasFatalError())
				return result;	
			pm.subTask(RefactoringCoreMessages.getString("RenameParametersRefactoring.checking")); //$NON-NLS-1$
			result.merge(checkNewNames());
			pm.worked(3);
			/*
			 * only one resource is affected - no need to check its availability
			 * (done in MethodRefactoring::checkActivation)
			 */
			if (fUpdateReferences && mustAnalyzeAst()) 
				result.merge(analyzeAst()); 
			pm.worked(7);
			return result;
		} finally{
			pm.done();
		}	
	}

	/*
	 * @see IMultiRenameRefactoring#setNewNames(Map)
	 */
	public void setNewNames(Map renamings) {
		Assert.isNotNull(renamings);
		fRenamings= renamings;
	}

	/*
	 * @see IMultiRenameRefactoring#getNewNames()
	 */
	public Map getNewNames() {
		return fRenamings;
	}
	
	private void setOldParameterNames(){
		if (fMethod.isBinary()) 
			return;
		try{
			String[] oldNames= fMethod.getParameterNames();
			fRenamings= new HashMap();
			for (int i= 0; i <oldNames.length; i++) {
				fRenamings.put(oldNames[i], oldNames[i]);
			}
		} catch (JavaModelException e){
			//ok to ignore - if this method does not exist, then the refactoring will not
			//be activated anyway
		}	
	}
	
	private boolean anythingRenamed(){
		for (Iterator iterator = fRenamings.keySet().iterator(); iterator.hasNext();) {
			String oldName = (String) iterator.next();
			if (! getNewName(oldName).equals(oldName))
				return true;	
		}
		return false;
	}
	
	private String getNewName(String oldName){
		if (fRenamings.containsKey(oldName))
			return (String)fRenamings.get(oldName);
		else
			return oldName;	
	}
	
	private RefactoringStatus checkForDuplicateNames(){
		RefactoringStatus result= new RefactoringStatus();
		Set found= new HashSet();
		Set doubled= new HashSet();
		for (Iterator iterator = fRenamings.keySet().iterator(); iterator.hasNext();) {
			String oldName= (String) iterator.next();
			String newName= getNewName(oldName);
			if (found.contains(newName) && !doubled.contains(newName)){
				result.addError(RefactoringCoreMessages.getFormattedString("RenameParametersRefactoring.duplicate_name", newName));//$NON-NLS-1$	
				doubled.add(newName);
			} else {
				found.add(newName);
			}	
		}
		return result;
	}
	
	private RefactoringStatus checkAllNames(){
		RefactoringStatus result= new RefactoringStatus();
		for (Iterator iterator = fRenamings.keySet().iterator(); iterator.hasNext();) {
			String oldName = (String) iterator.next();
			String newName= getNewName(oldName);
			result.merge(Checks.checkFieldName(newName));	
			if (! Checks.startsWithLowerCase(newName))
				result.addWarning(RefactoringCoreMessages.getString("RenameParametersRefactoring.should_start_lowercase")); //$NON-NLS-1$
		}
		return result;			
	}
	
	private boolean mustAnalyzeAst() throws JavaModelException{
		int flags= fMethod.getFlags();
		if (Flags.isAbstract(flags))
			return false;
		else if (Flags.isNative(flags))
			return false;
		else if (fMethod.getDeclaringType().isInterface())
			return false;
		else 
			return true;
	}
	
	private RefactoringStatus analyzeAst() throws JavaModelException{		
		AbstractRefactoringASTAnalyzer analyzer= new RenameParameterASTAnalyzer(fMethod, fRenamings);
		return analyzer.analyze(fMethod.getCompilationUnit());
	}
	
	//-------- changes ----
	
	public IChange createChange(IProgressMonitor pm) throws JavaModelException{
		try{
			String[] renamed= getRenamedParameterNames();
			pm.beginTask(RefactoringCoreMessages.getString("RenameParametersRefactoring.creating_change"), renamed.length); //$NON-NLS-1$
			ITextBufferChange builder= fTextBufferChangeCreator.create(RefactoringCoreMessages.getString("RenameParametersRefactoring.rename_method_parameters"), fMethod.getCompilationUnit()); //$NON-NLS-1$
			for (int i = 0; i < renamed.length; i++) {
				addParameterRenaming(renamed[i], builder);
				pm.worked(1);
			}
			return builder;
		} finally{
			pm.done();
		}	
	}
	
	private String[] getRenamedParameterNames(){
		Set result= new HashSet();
		for (Iterator iterator = fRenamings.keySet().iterator(); iterator.hasNext();) {
			String oldName = (String) iterator.next();
			String newName= getNewName(oldName);
			if (! oldName.equals(newName))
				result.add(oldName);
		}
		return (String[]) result.toArray(new String[result.size()]);
	}
	
	private void addParameterRenaming(String oldParameterName, ITextBufferChange builder) throws JavaModelException{
		int[] offsets= findParameterOccurrenceOffsets(oldParameterName);
		Assert.isTrue(offsets.length > 0); //at least the method declaration
		for (int i= 0; i < offsets.length; i++){
			addParameterRenameChange(oldParameterName, offsets[i], builder);
		};
	}
	
	private int[] findParameterOccurrenceOffsets(String oldParameterName) throws JavaModelException{
		return ParameterOffsetFinder.findOffsets(fMethod, oldParameterName, fUpdateReferences);
	}

	private void addParameterRenameChange(String oldParameterName, int occurrenceOffset, ITextBufferChange builder){
		String name=  RefactoringCoreMessages.getString("RenameParametersRefactoring.update_reference");//$NON-NLS-1$
		builder.addReplace(name, occurrenceOffset, oldParameterName.length(), getNewName(oldParameterName)); 
	}
}