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
package org.eclipse.jdt.internal.corext.refactoring.typeconstraints.types;

import org.eclipse.jdt.core.dom.ITypeBinding;

import org.eclipse.jdt.internal.corext.Assert;


public final class CaptureType extends AbstractTypeVariable {
	
	private WildcardType fWildcard;
	
	protected CaptureType(TypeEnvironment environment) {
		super(environment);
	}

	protected void initialize(ITypeBinding binding) {
		Assert.isTrue(binding.isCapture());
		super.initialize(binding);
		fWildcard= (WildcardType) getEnvironment().create(binding.getWildcard());
	}
	
	public int getKind() {
		return CAPTURE_TYPE;
	}
	
	public WildcardType getWildcard() {
		return fWildcard;
	}
	
	public TType getErasure() {
		return fWildcard.getErasure(); //TODO: remove this workaround for bug 93093
	}
	
	public boolean doEquals(TType type) {
		return getBindingKey().equals(((CaptureType)type).getBindingKey());
	}
	
	public int hashCode() {
		return getBindingKey().hashCode();
	}
	
	protected boolean doCanAssignTo(TType lhs) {
		switch (lhs.getKind()) {
			case NULL_TYPE: 
			case VOID_TYPE: return false;
			case PRIMITIVE_TYPE:
				
			case ARRAY_TYPE:
				return canAssignFirstBoundTo(lhs);
			
			case GENERIC_TYPE: return false;
			
			case STANDARD_TYPE: 
			case PARAMETERIZED_TYPE:
			case RAW_TYPE:
				return canAssignOneBoundTo(lhs);

			case UNBOUND_WILDCARD_TYPE:
			case EXTENDS_WILDCARD_TYPE:
			case SUPER_WILDCARD_TYPE:
				return ((WildcardType)lhs).checkAssignmentBound(this);
				
			case TYPE_VARIABLE:
				return false; //fWildcard.doCanAssignTo(lhs);
			
			case CAPTURE_TYPE:
				return ((CaptureType)lhs).checkLowerBound(this.getWildcard());
				
		}
		return false;
	}
	
	protected boolean checkLowerBound(TType rhs) {
		if (! getWildcard().isSuperWildcardType())
			return false;
		
		return rhs.canAssignTo(getWildcard().getBound());
	}
	
	private boolean canAssignFirstBoundTo(TType lhs) {
		if (fWildcard.isExtendsWildcardType() && fWildcard.getBound().isArrayType()) {
			// capture of ? extends X[]
			return fWildcard.getBound().canAssignTo(lhs);
		}
		// TODO: doesn't work due to bug 93093:
//		if (fBounds.length > 0 && fBounds[0].isArrayType()) {
//			// capture of ? extends X[]
//			return fBounds[0].canAssignTo(lhs);
//		}
		return false;
	}
	
	public String getName() {
		return ""; //$NON-NLS-1$
	}
	
	protected String getPlainPrettySignature() {
		return "capture-of " + fWildcard.getPrettySignature(); //$NON-NLS-1$
	}
}
