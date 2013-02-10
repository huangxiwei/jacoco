/*******************************************************************************
 * Copyright (c) 2009, 2013 Mountainminds GmbH & Co. KG and Contributors
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Marc R. Hoffmann - initial API and implementation
 *    Martin Hare Robertson - filters
 *    
 *******************************************************************************/
package org.jacoco.core.internal.analysis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.jacoco.core.analysis.ICounter;
import org.jacoco.core.analysis.IMethodCoverage;
import org.jacoco.core.analysis.ISourceNode;
import org.jacoco.core.internal.analysis.filters.ICoverageFilterStatus;
import org.jacoco.core.internal.flow.Instruction;
import org.jacoco.core.internal.flow.LabelInfo;
import org.jacoco.core.internal.flow.MethodProbesVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;

/**
 * A {@link MethodProbesVisitor} that analyzes which statements and branches of
 * a method has been executed based on given probe data.
 */
public class MethodAnalyzer extends MethodProbesVisitor {

	private final boolean[] probes;

	private final ICoverageFilterStatus coverageFilterStatus;

	private final MethodCoverageImpl coverage;

	private int currentLine = ISourceNode.UNKNOWN_LINE;

	private int firstLine = ISourceNode.UNKNOWN_LINE;

	private int lastLine = ISourceNode.UNKNOWN_LINE;

	// Due to ASM issue #315745 there can be more than one label per instruction
	private final List<Label> currentLabel = new ArrayList<Label>(2);

	/** List of all analyzed instructions */
	private final List<Instruction> instructions = new ArrayList<Instruction>();

	/** List of all predecessors of covered probes */
	private final List<Instruction> coveredProbes = new ArrayList<Instruction>();

	/** List of all predecessors of disabled probes */
	private final List<Instruction> disabledProbes = new ArrayList<Instruction>();

	/** List of all jumps encountered */
	private final List<Jump> jumps = new ArrayList<Jump>();
	private final List<Jump> probeJumps = new ArrayList<Jump>();

	/** Last instruction in byte code sequence */
	private Instruction lastInsn;

	private static class TryBlockStart {
		private final Label label;

		@Override
		public int hashCode() {
			return label.hashCode();
		}

		@Override
		public boolean equals(final Object obj) {
			if (obj instanceof TryBlockStart) {
				return label.equals(((TryBlockStart) obj).label);
			}
			return false;
		}

		public TryBlockStart(final Label start) {
			this.label = start;
		}
	}

	private static class TryBlock {
		private final Label start;
		private final Label end;
		private final Label handler;
		private final String handlerType;

		public TryBlock(final Label start, final Label end,
				final Label handler, final String handlerType) {
			this.start = start;
			this.end = end;
			this.handler = handler;
			this.handlerType = handlerType;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((end == null) ? 0 : end.hashCode());
			result = prime * result
					+ ((handler == null) ? 0 : handler.hashCode());
			result = prime * result
					+ ((handlerType == null) ? 0 : handlerType.hashCode());
			result = prime * result + ((start == null) ? 0 : start.hashCode());
			return result;
		}

		@Override
		public boolean equals(final Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (!(obj instanceof TryBlock)) {
				return false;
			}
			final TryBlock other = (TryBlock) obj;
			if (end == null) {
				if (other.end != null) {
					return false;
				}
			} else if (!end.equals(other.end)) {
				return false;
			}
			if (handler == null) {
				if (other.handler != null) {
					return false;
				}
			} else if (!handler.equals(other.handler)) {
				return false;
			}
			if (handlerType == null) {
				if (other.handlerType != null) {
					return false;
				}
			} else if (!handlerType.equals(other.handlerType)) {
				return false;
			}
			if (start == null) {
				if (other.start != null) {
					return false;
				}
			} else if (!start.equals(other.start)) {
				return false;
			}
			return true;
		}
	}

	private final Map<TryBlockStart, List<TryBlock>> tryBlockGroups = new LinkedHashMap<TryBlockStart, List<TryBlock>>();
	private final Map<Label, TryBlockStart> finallyBlockTryBlockStart = new HashMap<Label, TryBlockStart>();

	private static class MethodAtom {
		private final Label label;
		private final Instruction instruction;
		private final int probeIndex;

		public MethodAtom(final Label label) {
			this.label = label;
			this.instruction = null;
			this.probeIndex = -1;
		}

		public MethodAtom(final Instruction instruction) {
			this.label = null;
			this.instruction = instruction;
			this.probeIndex = -1;
		}

		public MethodAtom(final int probeIndex) {
			this.label = null;
			this.instruction = null;
			this.probeIndex = probeIndex;
		}

		@Override
		public boolean equals(final Object obj) {
			if (!(obj instanceof MethodAtom)) {
				return false;
			}
			final MethodAtom that = (MethodAtom) obj;
			if (label != null) {
				return label.equals(that.label);
			}
			if (instruction != null) {
				return instruction.equals(that.instruction);
			} else {
				return (probeIndex == that.probeIndex);
			}
		}

		@Override
		public int hashCode() {
			if (label != null) {
				return label.hashCode();
			}
			if (instruction != null) {
				return instruction.hashCode();
			} else {
				return Integer.valueOf(probeIndex).hashCode();
			}
		}
	}

	private final Map<Integer, Instruction> probeInsns = new HashMap<Integer, Instruction>();
	private final List<MethodAtom> methodAtoms = new ArrayList<MethodAtom>();

	@Override
	public void visitTryCatchBlock(final Label start, final Label end,
			final Label handler, final String type) {
		if (start == handler) {
			// Ignore
		} else {
			TryBlockStart blockStart;
			if (type == null) {
				if (!finallyBlockTryBlockStart.containsKey(handler)) {
					finallyBlockTryBlockStart.put(handler, new TryBlockStart(
							start));
				}
				blockStart = finallyBlockTryBlockStart.get(handler);
			} else {
				blockStart = new TryBlockStart(start);
			}

			if (!tryBlockGroups.containsKey(blockStart)) {
				tryBlockGroups.put(blockStart, new ArrayList<TryBlock>());
			}
			final List<TryBlock> catchBlocks = tryBlockGroups.get(blockStart);
			catchBlocks.add(new TryBlock(start, end, handler, type));
		}

		super.visitTryCatchBlock(start, end, handler, type);
	}

	/**
	 * New Method analyzer for the given probe data.
	 * 
	 * @param name
	 *            method name
	 * @param desc
	 *            description of the method
	 * @param signature
	 *            optional parameterized signature
	 * 
	 * @param probes
	 *            recorded probe date of the containing class or
	 *            <code>null</code> if the class is not executed at all
	 * @param coverageFilterStatus
	 *            filter which restricts the coverage data
	 */
	public MethodAnalyzer(final String name, final String desc,
			final String signature, final boolean[] probes,
			final ICoverageFilterStatus coverageFilterStatus) {
		super();
		this.probes = probes;
		this.coverageFilterStatus = coverageFilterStatus;
		this.coverage = new MethodCoverageImpl(name, desc, signature);
	}

	/**
	 * Returns the coverage data for this method after this visitor has been
	 * processed.
	 * 
	 * @return coverage data for this method
	 */
	public IMethodCoverage getCoverage() {
		return coverage;
	}

	@Override
	public void visitLabel(final Label label) {
		methodAtoms.add(new MethodAtom(label));
		currentLabel.add(label);
		if (!LabelInfo.isSuccessor(label)) {
			lastInsn = null;
		}
	}

	@Override
	public void visitLineNumber(final int line, final Label start) {
		currentLine = line;

		if (firstLine > line || lastLine == ISourceNode.UNKNOWN_LINE) {
			firstLine = line;
		}
		if (lastLine < line) {
			lastLine = line;
		}
	}

	@Override
	public void visitInsn(final int opcode) {
		final Instruction insn = new Instruction(opcode, currentLine,
				coverageFilterStatus.enabled());
		instructions.add(insn);
		methodAtoms.add(new MethodAtom(insn));
		if (lastInsn != null) {
			insn.setPredecessor(lastInsn);
		}

		final int labelCount = currentLabel.size();
		if (labelCount > 0) {
			for (int i = labelCount; --i >= 0;) {
				LabelInfo.setInstruction(currentLabel.get(i), insn);
			}
			currentLabel.clear();
		}
		lastInsn = insn;
	}

	@Override
	public void visitIntInsn(final int opcode, final int operand) {
		visitInsn(opcode);
	}

	@Override
	public void visitVarInsn(final int opcode, final int var) {
		visitInsn(opcode);
	}

	@Override
	public void visitTypeInsn(final int opcode, final String type) {
		visitInsn(opcode);
	}

	@Override
	public void visitFieldInsn(final int opcode, final String owner,
			final String name, final String desc) {
		visitInsn(opcode);
	}

	@Override
	public void visitMethodInsn(final int opcode, final String owner,
			final String name, final String desc) {
		visitInsn(opcode);
	}

	@Override
	public void visitInvokeDynamicInsn(final String name, final String desc,
			final Handle bsm, final Object... bsmArgs) {
		visitInsn(Opcodes.INVOKEDYNAMIC);
	}

	@Override
	public void visitJumpInsn(final int opcode, final Label label) {
		visitInsn(opcode);
		jumps.add(new Jump(lastInsn, label));
	}

	@Override
	public void visitLdcInsn(final Object cst) {
		visitInsn(Opcodes.LDC);
	}

	@Override
	public void visitIincInsn(final int var, final int increment) {
		visitInsn(Opcodes.IINC);
	}

	@Override
	public void visitTableSwitchInsn(final int min, final int max,
			final Label dflt, final Label... labels) {
		visitSwitchInsn(Opcodes.TABLESWITCH, dflt, labels);
	}

	@Override
	public void visitLookupSwitchInsn(final Label dflt, final int[] keys,
			final Label[] labels) {
		visitSwitchInsn(Opcodes.LOOKUPSWITCH, dflt, labels);
	}

	private void visitSwitchInsn(final int opcode, final Label dflt,
			final Label[] labels) {
		visitInsn(opcode);
		LabelInfo.resetDone(labels);
		jumps.add(new Jump(lastInsn, dflt));
		LabelInfo.setDone(dflt);
		for (final Label l : labels) {
			if (!LabelInfo.isDone(l)) {
				jumps.add(new Jump(lastInsn, l));
				LabelInfo.setDone(l);
			}
		}
	}

	@Override
	public void visitMultiANewArrayInsn(final String desc, final int dims) {
		visitInsn(Opcodes.MULTIANEWARRAY);
	}

	@Override
	public void visitProbe(final int probeId) {
		addProbe(probeId);
		addProbeAtom(probeId);
		lastInsn = null;
	}

	@Override
	public void visitJumpInsnWithProbe(final int opcode, final Label label,
			final int probeId) {
		addProbeAtom(probeId);

		visitInsn(opcode);
		probeJumps.add(new Jump(lastInsn, label));
		addProbe(probeId);
	}

	@Override
	public void visitInsnWithProbe(final int opcode, final int probeId) {
		addProbeAtom(probeId);

		visitInsn(opcode);
		addProbe(probeId);
	}

	@Override
	public void visitTableSwitchInsnWithProbes(final int min, final int max,
			final Label dflt, final Label[] labels) {
		visitSwitchInsnWithProbes(Opcodes.TABLESWITCH, dflt, labels);
	}

	@Override
	public void visitLookupSwitchInsnWithProbes(final Label dflt,
			final int[] keys, final Label[] labels) {
		visitSwitchInsnWithProbes(Opcodes.LOOKUPSWITCH, dflt, labels);
	}

	private void visitSwitchInsnWithProbes(final int opcode, final Label dflt,
			final Label[] labels) {
		visitInsn(opcode);
		LabelInfo.resetDone(dflt);
		LabelInfo.resetDone(labels);
		visitSwitchTarget(dflt);
		for (final Label l : labels) {
			visitSwitchTarget(l);
		}
	}

	private void visitSwitchTarget(final Label label) {
		final int id = LabelInfo.getProbeId(label);
		if (!LabelInfo.isDone(label)) {
			if (id == LabelInfo.NO_PROBE) {
				jumps.add(new Jump(lastInsn, label));
			} else {
				addProbe(id);
				addProbeAtom(id);
			}
			LabelInfo.setDone(label);
		}
	}

	@Override
	public void visitEnd() {
		// Wire jumps:
		for (final Jump j : jumps) {
			LabelInfo.getInstruction(j.target).setPredecessor(j.source);
		}

		// Clean up try/catch/finally block data
		final Map<TryBlockStart, List<Label>> finallyBlockStartLabelsGroups = computeFinallyBlockStartLabelsGroups();

		final List<FinallyBlockEndInsns> finallyBlocks = new ArrayList<FinallyBlockEndInsns>();

		while (!finallyBlockStartLabelsGroups.isEmpty()) {
			final Entry<TryBlockStart, List<Label>> entry = removeFirstEntryFromMap(finallyBlockStartLabelsGroups);
			final List<Label> finallyBlockStartLabels = entry.getValue();

			final int[] finallyBlockPointers = new int[entry.getValue().size()];
			for (int ii = 0; ii < finallyBlockPointers.length; ii++) {
				finallyBlockPointers[ii] = methodAtoms.indexOf(new MethodAtom(
						finallyBlockStartLabels.get(ii)));
				finallyBlockPointers[ii]++;
			}
			// Skip over extra instruction and label in catch(anything) block
			finallyBlockPointers[0] += 2;

			System.out.println("Examining try/catch/block");
			System.out.println("Starting pointers: "
					+ Arrays.toString(finallyBlockPointers));

			if ((finallyBlockPointers.length == 2)
					&& (finallyBlockPointers[0] == finallyBlockPointers[1])) {
				continue;
			}

			// Initialize finally block end insn ponters array
			final int[] finallyBlockEndInsnPointers = new int[finallyBlockPointers.length];
			for (int ii = 0; ii < finallyBlockEndInsnPointers.length; ii++) {
				finallyBlockEndInsnPointers[ii] = finallyBlockPointers[ii];
			}

			scanningLoop: while (true) {
				// Examine all current pointers
				if (isInconsistentAtomTypes(finallyBlockPointers)) {
					break scanningLoop;
				}

				printConsistentPointers(finallyBlockPointers);

				boolean isInsnAtom = false;
				if (isProbeAtom(finallyBlockPointers[0])) {
					if (isAnyNonPrimaryProbeCovered(finallyBlockPointers)) {
						markPrimaryFinallyBlockProbeCovered(finallyBlockPointers);
					}
				} else if (isLabelAtom(finallyBlockPointers[0])) {
					collapseDuplicateTryFinallyBlocks(finallyBlockPointers,
							finallyBlockStartLabelsGroups);
				} else if (isInsnAtom(finallyBlockPointers[0])) {
					isInsnAtom = true;
					if (isInconsistentInsns(finallyBlockPointers)) {
						break scanningLoop;
					} else {
						disableCoverageOfNonPrimaryFinallyBlock(finallyBlockPointers);
					}
				}

				// Copy latest pointers into the end array
				if (isInsnAtom) {
					for (int ii = 0; ii < finallyBlockPointers.length; ii++) {
						finallyBlockEndInsnPointers[ii] = finallyBlockPointers[ii];
					}
				}

				// Increment all pointers
				for (int ii = 0; ii < finallyBlockPointers.length; ii++) {
					finallyBlockPointers[ii]++;
					if ((finallyBlockPointers[ii] >= methodAtoms.size())
							|| (finallyBlockPointers[ii] < 0)) {
						break scanningLoop;
					}
				}
			}

			System.out.println("End pointers: "
					+ Arrays.toString(finallyBlockEndInsnPointers));

			// Print some insns after the end of the consistent insns
			for (int ii = 1; ii <= 5; ii++) {
				final int[] lookahead = new int[finallyBlockEndInsnPointers.length];
				for (int jj = 0; jj < lookahead.length; jj++) {
					lookahead[jj] = finallyBlockEndInsnPointers[jj] + ii;
				}
				printInconsistentPointers(lookahead);
			}

			// Skip extra label in exception copies and unify final probes
			if (isProbeAtom(finallyBlockEndInsnPointers[0] + 1)) {
				finallyBlockEndInsnPointers[0] += 1;
				for (int ii = 1; ii < finallyBlockEndInsnPointers.length; ii++) {
					finallyBlockEndInsnPointers[ii] += 1;
					if (!isProbeAtom(finallyBlockEndInsnPointers[ii])) {
						finallyBlockEndInsnPointers[ii] += 1;
					}
				}
				if (isConsistentAtomTypes(finallyBlockEndInsnPointers)
						&& isProbeAtom(finallyBlockEndInsnPointers[0])) {
					printConsistentPointers(finallyBlockEndInsnPointers);
					if (isAnyNonPrimaryProbeCovered(finallyBlockEndInsnPointers)) {
						markPrimaryFinallyBlockProbeCovered(finallyBlockEndInsnPointers);
					}
				}

				System.out.println("A: Pre-Fixed end insn pointers: "
						+ Arrays.toString(finallyBlockEndInsnPointers));

				// Seek to the final instructions
				finallyBlockEndInsnPointers[0] += 4;
				for (int ii = 1; ii < (finallyBlockEndInsnPointers.length - 1); ii++) {
					finallyBlockEndInsnPointers[ii] += 1;
				}
				if (isInsnAtom(finallyBlockEndInsnPointers[finallyBlockEndInsnPointers.length - 1] + 1)) {
					finallyBlockEndInsnPointers[finallyBlockEndInsnPointers.length - 1] += 1;
				} else {
					finallyBlockEndInsnPointers[finallyBlockEndInsnPointers.length - 1] -= 1;
				}
				disableCoverageOfNonPrimaryFinallyBlock(finallyBlockEndInsnPointers);
			} else {
				System.out.println("B: Pre-Fixed end insn pointers: "
						+ Arrays.toString(finallyBlockEndInsnPointers));

				// Seek to the final instructions
				for (int ii = 3; ii <= 4; ii++) {
					if (isInsnAtom(finallyBlockEndInsnPointers[0] + ii)) {
						finallyBlockEndInsnPointers[0] += ii;
						break;
					}
				}
				for (int ii = 1; ii < (finallyBlockEndInsnPointers.length - 1); ii++) {
					finallyBlockEndInsnPointers[ii] += 2;
				}
				disableCoverageOfNonPrimaryFinallyBlock(finallyBlockEndInsnPointers);
			}

			System.out.println("Fixed end insn pointers: "
					+ Arrays.toString(finallyBlockEndInsnPointers));

			// Construct details about the end of each finally block
			if (finallyBlockEndInsnPointers[0] > -1) {
				final Instruction primaryEndInsn = methodAtoms
						.get(finallyBlockEndInsnPointers[0]).instruction;
				final List<Instruction> otherEndInsns = new ArrayList<Instruction>();
				for (int ii = 1; ii < finallyBlockEndInsnPointers.length; ii++) {
					final Instruction insn = methodAtoms
							.get(finallyBlockEndInsnPointers[ii]).instruction;
					otherEndInsns.add(insn);
				}

				final FinallyBlockEndInsns block = new FinallyBlockEndInsns(
						primaryEndInsn, otherEndInsns);
				finallyBlocks.add(block);
			}

			System.out.println("# done try/catch/block");
		}

		// Propagate probe values:
		for (final Instruction p : coveredProbes) {
			p.setCovered();
		}
		for (final Instruction p : disabledProbes) {
			p.setDisabled();
		}

		// Propagate coverage into finally block duplicates
		for (final FinallyBlockEndInsns block : finallyBlocks) {
			if (!isCovered(block.primaryEndInsn)
					&& isAnyCovered(block.otherEndInsns)) {
				block.primaryEndInsn.setCovered();
			}
		}

		// Report result:
		coverage.ensureCapacity(firstLine, lastLine);
		for (final Instruction i : instructions) {
			final int total = i.getBranches();
			final int covered = i.getCoveredBranches();
			final boolean coverageEnabled = i.isCoverageEnabled();
			final ICounter instrCounter;
			if (!coverageEnabled) {
				instrCounter = CounterImpl.COUNTER_0_0;
			} else if (covered > 0) {
				instrCounter = CounterImpl.COUNTER_0_1;
			} else {
				instrCounter = CounterImpl.COUNTER_1_0;
			}
			final ICounter branchCounter;
			if ((total > 1) && coverageEnabled) {
				branchCounter = CounterImpl.getInstance(total - covered,
						covered);
			} else {
				branchCounter = CounterImpl.COUNTER_0_0;
			}
			coverage.increment(instrCounter, branchCounter, i.getLine());
		}
		coverage.incrementMethodCounter();
	}

	private void printConsistentPointers(final int[] finallyBlockPointers) {
		if (isProbeAtom(finallyBlockPointers[0])) {
			final int[] probeDups = new int[finallyBlockPointers.length];

			for (int ii = 0; ii < probeDups.length; ii++) {
				probeDups[ii] = methodAtoms.get(finallyBlockPointers[ii]).probeIndex;
			}

			System.out.println("Probes: " + Arrays.toString(probeDups));
		} else if (isLabelAtom(finallyBlockPointers[0])) {
			System.out.println("Label");
		} else if (isInsnAtom(finallyBlockPointers[0])) {
			if (!isInconsistentInsns(finallyBlockPointers)) {
				final int insn = methodAtoms.get(finallyBlockPointers[0]).instruction
						.getOpcode();
				System.out.println("Insn: " + OPCODES[insn]);
			}
		}
	}

	private void printInconsistentPointers(final int[] finallyBlockPointers) {
		final StringBuilder str = new StringBuilder();

		for (int jj = 0; jj < finallyBlockPointers.length; jj++) {
			if (finallyBlockPointers[jj] >= methodAtoms.size()) {
				str.append("end-of-method");
			} else {
				if (isProbeAtom(finallyBlockPointers[jj])) {
					final int probe = methodAtoms.get(finallyBlockPointers[jj]).probeIndex;
					str.append("Probe:" + probe + "       ");
				} else if (isLabelAtom(finallyBlockPointers[jj])) {
					str.append("Label        ");
				} else if (isInsnAtom(finallyBlockPointers[jj])) {
					final int insn = methodAtoms.get(finallyBlockPointers[jj]).instruction
							.getOpcode();
					str.append("Insn: " + OPCODES[insn]);
				}
			}
			str.append("\t,");
		}

		str.setLength(str.length() - 1);
		System.out.println(str);
	}

	private boolean isAnyCovered(final List<Instruction> insns) {
		for (final Instruction insn : insns) {
			if (isCovered(insn)) {
				return true;
			}
		}
		return false;
	}

	private boolean isCovered(final Instruction insn) {
		return (insn.getCoveredBranches() > 0);
	}

	private static class FinallyBlockEndInsns {
		private final Instruction primaryEndInsn;
		private final List<Instruction> otherEndInsns;

		public FinallyBlockEndInsns(final Instruction primaryEndInsn,
				final List<Instruction> otherEndInsns) {
			this.primaryEndInsn = primaryEndInsn;
			this.otherEndInsns = otherEndInsns;
		}
	}

	private void disableCoverageOfNonPrimaryFinallyBlock(
			final int[] finallyBlockPointers) {
		for (int ii = 1; ii < finallyBlockPointers.length; ii++) {
			methodAtoms.get(finallyBlockPointers[ii]).instruction.disable();
		}
	}

	private boolean isInconsistentInsns(final int[] finallyBlockPointers) {
		final int NONE = -1;

		int lastInsn = NONE;

		for (int ii = 0; ii < finallyBlockPointers.length; ii++) {
			final Instruction insn = methodAtoms.get(finallyBlockPointers[ii]).instruction;

			if ((lastInsn == NONE) || (lastInsn == insn.getOpcode())) {
				lastInsn = insn.getOpcode();
			} else {
				return true;
			}
		}
		return false;
	}

	private void collapseDuplicateTryFinallyBlocks(
			final int[] finallyBlockPointers,
			final Map<TryBlockStart, List<Label>> finallyDuplicateStarts) {
		TryBlockStart firstTryBlockStart = null;
		final List<Label> finallyDups = new ArrayList<Label>();
		for (int ii = 0; ii < finallyBlockPointers.length; ii++) {
			final TryBlockStart tryBlockStart = new TryBlockStart(
					methodAtoms.get(finallyBlockPointers[ii]).label);

			if (finallyDuplicateStarts.containsKey(tryBlockStart)) {
				if (firstTryBlockStart == null) {
					firstTryBlockStart = tryBlockStart;
				}

				finallyDups
						.addAll(finallyDuplicateStarts.remove(tryBlockStart));
			}
		}

		if (firstTryBlockStart != null) {
			finallyDuplicateStarts.put(firstTryBlockStart, finallyDups);
		}
	}

	private void markPrimaryFinallyBlockProbeCovered(
			final int[] finallyBlockPointers) {
		final int probeIndex = methodAtoms.get(finallyBlockPointers[0]).probeIndex;
		if (!probes[probeIndex]) {
			final Instruction probeInsn = probeInsns.get(Integer
					.valueOf(probeIndex));
			coveredProbes.add(probeInsn);
		}
	}

	private boolean isAnyNonPrimaryProbeCovered(final int[] finallyBlockPointers) {
		for (int ii = 1; ii < finallyBlockPointers.length; ii++) {
			final int probeIndex = methodAtoms.get(finallyBlockPointers[ii]).probeIndex;
			if (probes[probeIndex]) {
				return true;
			}
		}
		return false;
	}

	private boolean isInsnAtom(final int atomPointer) {
		return (methodAtoms.get(atomPointer).instruction != null);
	}

	private boolean isLabelAtom(final int atomPointer) {
		return (methodAtoms.get(atomPointer).label != null);
	}

	private boolean isProbeAtom(final int atomPointer) {
		return (methodAtoms.get(atomPointer).probeIndex >= 0);
	}

	private boolean isConsistentAtomTypes(final int[] finallyBlockPointers) {
		return !isInconsistentAtomTypes(finallyBlockPointers);
	}

	private boolean isInconsistentAtomTypes(final int[] finallyBlockPointers) {
		final int NONE = 0;
		final int INSN = 1;
		final int LABEL = 2;
		final int PROBE = 3;

		int seenType = NONE;

		for (int ii = 0; ii < finallyBlockPointers.length; ii++) {
			if (methodAtoms.get(finallyBlockPointers[ii]).instruction != null) {
				if ((seenType == NONE) || (seenType == INSN)) {
					seenType = INSN;
				} else {
					return true;
				}
			} else if (methodAtoms.get(finallyBlockPointers[ii]).label != null) {
				if ((seenType == NONE) || (seenType == LABEL)) {
					seenType = LABEL;
				} else {
					return true;
				}
			} else if (methodAtoms.get(finallyBlockPointers[ii]).probeIndex >= 0) {
				if ((seenType == NONE) || (seenType == PROBE)) {
					seenType = PROBE;
				} else {
					return true;
				}
			}
		}
		return false;
	}

	private <S, T> Entry<S, T> removeFirstEntryFromMap(final Map<S, T> map) {
		final Iterator<Entry<S, T>> iter = map.entrySet().iterator();
		final Entry<S, T> entry = iter.next();
		iter.remove();
		return entry;
	}

	private Map<TryBlockStart, List<Label>> computeFinallyBlockStartLabelsGroups() {
		// finally block duplicates:
		// - 1x finally block handler
		// - nx catch block finally handlers
		// - 1x no-exception handler

		final Map<TryBlockStart, List<Label>> finallyBlockStartLabelsGroups = new LinkedHashMap<TryBlockStart, List<Label>>();

		for (final Entry<TryBlockStart, List<TryBlock>> tryBlockGroupEntry : tryBlockGroups
				.entrySet()) {
			final TryBlockStart tryBlockStart = tryBlockGroupEntry.getKey();

			boolean seenFinally = false;
			boolean seenCatch = false;
			for (final TryBlock tryBlock : tryBlockGroupEntry.getValue()) {
				if (tryBlock.handlerType == null) {
					seenFinally = true;
				} else {
					seenCatch = true;
				}
			}

			if (!seenFinally) {
				continue;
			}

			final List<Label> finallyBlockStartLabelsGroup = new ArrayList<Label>();

			// - 1x finally block handler
			for (final TryBlock tryBlock : tryBlockGroupEntry.getValue()) {
				if (tryBlock.handlerType == null) {
					if (tryBlock.start == tryBlockStart.label) {
						finallyBlockStartLabelsGroup.add(tryBlock.handler);
					}
				}
			}

			// - nx catch block finally handlers
			if (seenCatch) {
				for (final TryBlock tryBlock : tryBlockGroupEntry.getValue()) {
					if (tryBlock.handlerType == null) {
						final Label finallyBlock = tryBlock.end;

						final int finallyBlockIndex = methodAtoms
								.indexOf(new MethodAtom(finallyBlock));
						if (isInsnAtom(finallyBlockIndex - 1)) {
							final int insnOpcode = methodAtoms
									.get(finallyBlockIndex - 1).instruction
									.getOpcode();
							if (insnOpcode == Opcodes.ATHROW) {
								break;
							}
						}

						if (!finallyBlockStartLabelsGroup
								.contains(finallyBlock)) {
							finallyBlockStartLabelsGroup.add(finallyBlock);
						}
					}
				}
			}

			// - 1x no-exception handler
			final TryBlock firstCatchBlock = tryBlockGroupEntry.getValue().get(
					0);
			Label firstTryBlockEnd = firstCatchBlock.end;
			final int firstTryBlockEndIndex = methodAtoms
					.indexOf(new MethodAtom(firstTryBlockEnd));

			if (isInsnAtom(firstTryBlockEndIndex - 1)) {
				if (methodAtoms.get(firstTryBlockEndIndex - 1).instruction
						.getOpcode() == Opcodes.MONITOREXIT) {
					// try/finally blocks are handled differently for
					// synchronized blocks but these are handled by a
					// specific filter. The finally code is included
					// *within* the try block.
					continue;
				}
			}

			boolean seenGoto = false;
			Instruction firstInstrAfterTryBlockEnd = null;
			for (int ii = -1; ii <= 3; ii++) {
				if (isLabelAtom(firstTryBlockEndIndex + ii)) {
					firstTryBlockEnd = methodAtoms.get(firstTryBlockEndIndex
							+ ii).label;
				} else if (isInsnAtom(firstTryBlockEndIndex + ii)) {
					final int insnOpcode = methodAtoms
							.get(firstTryBlockEndIndex + ii).instruction
							.getOpcode();
					firstInstrAfterTryBlockEnd = methodAtoms
							.get(firstTryBlockEndIndex + ii).instruction;
					if (insnOpcode == Opcodes.GOTO) {
						seenGoto = true;
						break;
					}

					if (ii >= 0) {
						break;
					}
				}
			}

			if (seenGoto) {
				boolean checkProbeJumps = true;
				for (final Jump jump : jumps) {
					if (jump.source == firstInstrAfterTryBlockEnd) {
						finallyBlockStartLabelsGroup.add(jump.target);
						checkProbeJumps = false;
						break;
					}
				}
				if (checkProbeJumps) {
					for (final Jump jump : probeJumps) {
						if (jump.source == firstInstrAfterTryBlockEnd) {
							finallyBlockStartLabelsGroup.add(jump.target);
							break;
						}
					}
				}
			} else {
				finallyBlockStartLabelsGroup.add(firstTryBlockEnd);
			}

			if (finallyBlockStartLabelsGroup.size() > 1) {
				finallyBlockStartLabelsGroups.put(tryBlockStart,
						finallyBlockStartLabelsGroup);
			}
		}

		return finallyBlockStartLabelsGroups;
	}

	private void addProbeAtom(final int probeId) {
		if (lastInsn != null) {
			methodAtoms.add(new MethodAtom(probeId));
		}
	}

	private void addProbe(final int probeId) {
		if (lastInsn != null) {
			probeInsns.put(Integer.valueOf(probeId), lastInsn);
			lastInsn.addBranch();
			if (probes != null && probes[probeId]) {
				coveredProbes.add(lastInsn);
			}

			if (!lastInsn.isCoverageEnabled()) {
				disabledProbes.add(lastInsn);
			}
		}
	}

	private static class Jump {

		final Instruction source;
		final Label target;

		Jump(final Instruction source, final Label target) {
			this.source = source;
			this.target = target;
		}
	}

	/**
	 * The names of the Java Virtual Machine opcodes.
	 */
	public static final String[] OPCODES;

	static {
		final String s = "NOP,ACONST_NULL,ICONST_M1,ICONST_0,ICONST_1,ICONST_2,"
				+ "ICONST_3,ICONST_4,ICONST_5,LCONST_0,LCONST_1,FCONST_0,"
				+ "FCONST_1,FCONST_2,DCONST_0,DCONST_1,BIPUSH,SIPUSH,LDC,,,"
				+ "ILOAD,LLOAD,FLOAD,DLOAD,ALOAD,,,,,,,,,,,,,,,,,,,,,IALOAD,"
				+ "LALOAD,FALOAD,DALOAD,AALOAD,BALOAD,CALOAD,SALOAD,ISTORE,"
				+ "LSTORE,FSTORE,DSTORE,ASTORE,,,,,,,,,,,,,,,,,,,,,IASTORE,"
				+ "LASTORE,FASTORE,DASTORE,AASTORE,BASTORE,CASTORE,SASTORE,POP,"
				+ "POP2,DUP,DUP_X1,DUP_X2,DUP2,DUP2_X1,DUP2_X2,SWAP,IADD,LADD,"
				+ "FADD,DADD,ISUB,LSUB,FSUB,DSUB,IMUL,LMUL,FMUL,DMUL,IDIV,LDIV,"
				+ "FDIV,DDIV,IREM,LREM,FREM,DREM,INEG,LNEG,FNEG,DNEG,ISHL,LSHL,"
				+ "ISHR,LSHR,IUSHR,LUSHR,IAND,LAND,IOR,LOR,IXOR,LXOR,IINC,I2L,"
				+ "I2F,I2D,L2I,L2F,L2D,F2I,F2L,F2D,D2I,D2L,D2F,I2B,I2C,I2S,LCMP,"
				+ "FCMPL,FCMPG,DCMPL,DCMPG,IFEQ,IFNE,IFLT,IFGE,IFGT,IFLE,"
				+ "IF_ICMPEQ,IF_ICMPNE,IF_ICMPLT,IF_ICMPGE,IF_ICMPGT,IF_ICMPLE,"
				+ "IF_ACMPEQ,IF_ACMPNE,GOTO,JSR,RET,TABLESWITCH,LOOKUPSWITCH,"
				+ "IRETURN,LRETURN,FRETURN,DRETURN,ARETURN,RETURN,GETSTATIC,"
				+ "PUTSTATIC,GETFIELD,PUTFIELD,INVOKEVIRTUAL,INVOKESPECIAL,"
				+ "INVOKESTATIC,INVOKEINTERFACE,INVOKEDYNAMIC,NEW,NEWARRAY,"
				+ "ANEWARRAY,ARRAYLENGTH,ATHROW,CHECKCAST,INSTANCEOF,"
				+ "MONITORENTER,MONITOREXIT,,MULTIANEWARRAY,IFNULL,IFNONNULL,";
		OPCODES = new String[200];
		int i = 0;
		int j = 0;
		int l;
		while ((l = s.indexOf(',', j)) > 0) {
			OPCODES[i++] = j + 1 == l ? null : s.substring(j, l);
			j = l + 1;
		}
	}
}
