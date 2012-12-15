/*******************************************************************************
 * Copyright (c) 2009, 2012 Mountainminds GmbH & Co. KG and Contributors
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Martin Hare Robertson - initial API and implementation
 *    
 *******************************************************************************/
package org.jacoco.core.internal.analysis.filters;

import java.util.HashSet;
import java.util.Set;

import org.jacoco.core.internal.analysis.filters.ICoverageFilterStatus.ICoverageFilter;
import org.jacoco.core.internal.flow.MethodProbesVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Filter that disabled coverage tracking of the exception exit branch from
 * synchronized blocks
 */
public class FinallyCoverageFilter implements ICoverageFilter {

	private boolean enabled = true;

	public boolean enabled() {
		return enabled;
	}

	public boolean includeClass(final String className) {
		return true;
	}

	public ClassVisitor visitClass(final ClassVisitor delegate) {
		return delegate;
	}

	public MethodVisitor preVisitMethod(final String name, final String desc,
			final MethodVisitor delegate) {
		return delegate;
	}

	public MethodProbesVisitor visitMethod(final String name,
			final String desc, final MethodProbesVisitor delegate) {
		enabled = true;
		return new SyncFilterVisitor(delegate);
	}

	private class SyncFilterVisitor extends MethodProbesVisitor {
		private final Set<Label> handlers = new HashSet<Label>();
		private final MethodProbesVisitor delegate;

		private SyncFilterVisitor(final MethodProbesVisitor delegate) {
			super(delegate);
			this.delegate = delegate;
		}

		@Override
		public void visitTryCatchBlock(final Label start, final Label end,
				final Label handler, final String type) {
			if (type == null) {
				handlers.add(handler);
			}
			super.visitTryCatchBlock(start, end, handler, type);
		}

		@Override
		public void visitLabel(final Label label) {
			if (handlers.contains(label)) {
				enabled = false;
			}
			super.visitLabel(label);
		}

		@Override
		public void visitInsn(final int opcode) {
			if (!enabled && (opcode == Opcodes.ATHROW)) {
				enabled = true;
			}
			super.visitInsn(opcode);
		}

		// --- Simple methods that pass on to the delegate ---

		@Override
		public void visitProbe(final int probeId) {
			delegate.visitProbe(probeId);
		}

		@Override
		public void visitJumpInsnWithProbe(final int opcode, final Label label,
				final int probeId) {
			delegate.visitJumpInsnWithProbe(opcode, label, probeId);
		}

		@Override
		public void visitInsnWithProbe(final int opcode, final int probeId) {
			delegate.visitInsnWithProbe(opcode, probeId);
			if (!enabled && (opcode == Opcodes.ATHROW)) {
				enabled = true;
			}
		}

		@Override
		public void visitTableSwitchInsnWithProbes(final int min,
				final int max, final Label dflt, final Label[] labels) {
			delegate.visitTableSwitchInsnWithProbes(min, max, dflt, labels);
		}

		@Override
		public void visitLookupSwitchInsnWithProbes(final Label dflt,
				final int[] keys, final Label[] labels) {
			delegate.visitLookupSwitchInsnWithProbes(dflt, keys, labels);
		}
	}

}
