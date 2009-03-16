/*******************************************************************************
 * Copyright (c) 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.javaeditor;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.operation.IRunnableContext;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.StructuredSelection;

import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextOperationTarget;
import org.eclipse.jface.text.hyperlink.IHyperlink;

import org.eclipse.ui.PlatformUI;

import org.eclipse.ui.texteditor.ITextEditor;

import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;

import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.JavaElementLabels;
import org.eclipse.jdt.ui.SharedASTProvider;
import org.eclipse.jdt.ui.actions.SelectionDispatchAction;

import org.eclipse.jdt.internal.ui.JavaPlugin;


/**
 * Java element implementation hyperlink.
 * 
 * @since 3.5
 */
public class JavaElementImplementationHyperlink implements IHyperlink {
	
	private final IRegion fRegion;
	private final SelectionDispatchAction fOpenAction;
	private final IJavaElement fElement;
	private final boolean fQualify;

	/**
	 * The current text editor.
	 */
	private ITextEditor fEditor;

	/**
	 * Creates a new Java element implementation hyperlink for methods.
	 * 
	 * @param region the region of the link
	 * @param openAction the action to use to open the java elements
	 * @param element the java element to open
	 * @param qualify <code>true</code> if the hyperlink text should show a qualified name for
	 *            element.
	 * @param editor the active java editor
	 */
	public JavaElementImplementationHyperlink(IRegion region, SelectionDispatchAction openAction, IJavaElement element, boolean qualify, ITextEditor editor) {
		Assert.isNotNull(openAction);
		Assert.isNotNull(region);
		Assert.isNotNull(element);

		fRegion= region;
		fOpenAction= openAction;
		fElement= element;
		fQualify= qualify;
		fEditor= editor;
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.javaeditor.IHyperlink#getHyperlinkRegion()
	 */
	public IRegion getHyperlinkRegion() {
		return fRegion;
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.javaeditor.IHyperlink#getHyperlinkText()
	 */
	public String getHyperlinkText() {
		if (fQualify) {
			String elementLabel= JavaElementLabels.getElementLabel(fElement, JavaElementLabels.ALL_FULLY_QUALIFIED);
			return Messages.format(JavaEditorMessages.JavaElementImplementationHyperlink_hyperlinkText_qualified, new Object[] { elementLabel });
		} else {
			return JavaEditorMessages.JavaElementImplementationHyperlink_hyperlinkText;
		}
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.javaeditor.IHyperlink#getTypeLabel()
	 */
	public String getTypeLabel() {
		return null;
	}

	/**
	 * Opens the given implementation hyperlink for methods.
	 * <p>
	 * If there's only one implementor that hyperlink is opened in the editor, otherwise the
	 * Quick Hierarchy is opened.
	 * </p>
	 */
	public void open() {
		ITypeRoot editorInput= EditorUtility.getEditorInputJavaElement(fEditor, false);

		CompilationUnit ast= SharedASTProvider.getAST(editorInput, SharedASTProvider.WAIT_ACTIVE_ONLY, null);
		if (ast == null) {
			openQuickHierarchy();
			return;
		}

		ASTNode node= NodeFinder.perform(ast, fRegion.getOffset(), fRegion.getLength());
		IJavaElement elem= null;
		if (node instanceof SimpleName) {
			ASTNode parent= node.getParent();
			if (parent != null) {
				if (parent instanceof MethodInvocation) {
					Expression expression= ((MethodInvocation)parent).getExpression();
					if (expression == null) {
						ITypeBinding parentTypeBinding= Bindings.getBindingOfParentType(node);
						if (parentTypeBinding != null)
							elem= parentTypeBinding.getJavaElement();
					} else {
						ITypeBinding binding= expression.resolveTypeBinding();
						if (binding != null)
							elem= binding.getJavaElement();
					}
				} else if (parent instanceof MethodDeclaration) {
					ITypeBinding parentTypeBinding= Bindings.getBindingOfParentType(node);
					if (parentTypeBinding != null)
						elem= parentTypeBinding.getJavaElement();
				}
			}
		}
		final IType type= (IType)elem;
		if (type == null) {
			openQuickHierarchy();
			return;
		}

		final String dummyString= new String();
		final ArrayList links= new ArrayList();
		IRunnableWithProgress runnable= new IRunnableWithProgress() {

			public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
				if (monitor == null) {
					monitor= new NullProgressMonitor();
				}
				try {
					String methodLabel= JavaElementLabels.getElementLabel(fElement, JavaElementLabels.DEFAULT_QUALIFIED);
					monitor.beginTask(Messages.format(JavaEditorMessages.JavaElementImplementationHyperlink_search_method_implementors, methodLabel), 100);
					SearchRequestor requestor= new SearchRequestor() {
						public void acceptSearchMatch(SearchMatch match) throws CoreException {
							if (match.getAccuracy() == SearchMatch.A_ACCURATE) {
								IJavaElement element= (IJavaElement)match.getElement();
								if (element instanceof IMember) {
									IMember declaredClass= (IMember)element.getParent();
									if (declaredClass != null) {
										int flags= declaredClass.getFlags();
										int memberFlags= ((IMember)element).getFlags();
										if (!Flags.isInterface(flags) && !((Flags.isAbstract(flags)) && Flags.isAbstract(memberFlags))) {
											links.add(element);
											if (links.size() > 1) {
												throw new OperationCanceledException(dummyString);
											}
										}
									}
								}
							}
						}
					};
					int limitTo= IJavaSearchConstants.DECLARATIONS | IJavaSearchConstants.IGNORE_DECLARING_TYPE | IJavaSearchConstants.IGNORE_RETURN_TYPE;
					SearchPattern pattern= SearchPattern.createPattern(fElement, limitTo);
					Assert.isNotNull(pattern);
					SearchParticipant[] participants= new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant() };
					SearchEngine engine= new SearchEngine();
					engine.search(pattern, participants, SearchEngine.createHierarchyScope(type), requestor, new SubProgressMonitor(monitor, 100));

					if (monitor.isCanceled()) {
						throw new OperationCanceledException();
					}
				} catch (CoreException e) {
					throw new InvocationTargetException(e);
				} finally {
					monitor.done();
				}
			}
		};

		try {
			IRunnableContext context= fEditor.getSite().getWorkbenchWindow();
			context.run(true, true, runnable);
		} catch (InvocationTargetException e) {
			IStatus status= new Status(IStatus.ERROR, JavaPlugin.getPluginId(), IStatus.OK,
					Messages.format(JavaEditorMessages.JavaElementImplementationHyperlink_error_status_message, fElement.getElementName()), e.getCause());
			JavaPlugin.log(status);
			ErrorDialog.openError(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
					JavaEditorMessages.JavaElementImplementationHyperlink_hyperlinkText,
					JavaEditorMessages.JavaElementImplementationHyperlink_error_no_implementations_found_message, status);
		} catch (InterruptedException e) {
			if (e.getMessage() != dummyString) {
				return;
			}
		}

		if (links.size() == 1) {
			fOpenAction.run(new StructuredSelection(links.get(0)));
		} else {
			openQuickHierarchy();
		}
	}

	/**
	 * Opens a quick type hierarchy for the editor's current input.
	 */
	private void openQuickHierarchy() {
		ITextOperationTarget textOperationTarget= (ITextOperationTarget)fEditor.getAdapter(ITextOperationTarget.class);
		textOperationTarget.doOperation(JavaSourceViewer.SHOW_HIERARCHY);
	}
}