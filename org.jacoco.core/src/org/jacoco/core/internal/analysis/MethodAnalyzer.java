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

	/**
	 * List of all jumps encountered.
	 */
	private final List<Jump> jumps = new ArrayList<Jump>();

	/** List of all jumps (including those with probes) encountered */
	private final List<Jump> allJumps = new ArrayList<Jump>();

	/** Last instruction in byte code sequence */
	private Instruction lastInsn;

	/**
	 * An "atom" of a method. One of:
	 * <ul>
	 * <li>Label
	 * <li>Instruction
	 * <li>Probe
	 * </ul>
	 */
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

	private final Map<Integer, Instruction> probeIndexToInstructionPriorToProbe = new HashMap<Integer, Instruction>();
	private final List<MethodAtom> methodAtoms = new ArrayList<MethodAtom>();

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

	private static class TryCatchBlock {
		private final Label start;
		private final Label end;
		private final Label handler;
		private final String catchType;

		public TryCatchBlock(final Label start, final Label end,
				final String catchType, final Label handler) {
			this.start = start;
			this.end = end;
			this.catchType = catchType;
			this.handler = handler;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((end == null) ? 0 : end.hashCode());
			result = prime * result
					+ ((handler == null) ? 0 : handler.hashCode());
			result = prime * result
					+ ((catchType == null) ? 0 : catchType.hashCode());
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
			if (!(obj instanceof TryCatchBlock)) {
				return false;
			}
			final TryCatchBlock other = (TryCatchBlock) obj;
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
			if (catchType == null) {
				if (other.catchType != null) {
					return false;
				}
			} else if (!catchType.equals(other.catchType)) {
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

	private final Map<Label, List<TryCatchBlock>> tryCatchBlocksGroupedByStartLabel = new LinkedHashMap<Label, List<TryCatchBlock>>();

	private final Map<Label, Label> finallyHandlerStartLabelToTryCatchBlockStartLabel = new HashMap<Label, Label>();

	@Override
	public void visitTryCatchBlock(final Label start, final Label end,
			final Label handler, final String type) {
		if (start == handler) {
			// Ignore
		} else {
			Label tryCatchBlockStart = start;
			if (type == null) {
				// @formatter:off
				/*
				 * Consider the following Java source:
				 * try {
				 *   A();
				 * } catch (Exception ex) {
				 *   B();
				 * } finally {
				 *   C();
				 * }
				 * 
				 * This may be compiled to the following:
				 * 0,1.{
				 *   A();
				 * 1.} Exception => E.
				 * 0.} Anything => A.
				 * => NoEx.
				 * E,2.{
				 *   B();
				 * 2.} Anything => A.
				 *   C();
				 *   => End.
				 * E.}
				 * A.{
				 *   C();
				 *   => End.
				 * A.}
				 * NoEx.{
				 *   C();
				 *   => End.
				 * NoEx.}
				 * End.
				 * 
				 * Note the finally block which starts within a catch block and 
				 * is handled by the same catch anything block at the end.
				 */
				// @formatter:on
				if (!finallyHandlerStartLabelToTryCatchBlockStartLabel
						.containsKey(handler)) {
					finallyHandlerStartLabelToTryCatchBlockStartLabel.put(
							handler, start);
				} else {
					tryCatchBlockStart = finallyHandlerStartLabelToTryCatchBlockStartLabel
							.get(handler);
				}
			}

			if (!tryCatchBlocksGroupedByStartLabel
					.containsKey(tryCatchBlockStart)) {
				tryCatchBlocksGroupedByStartLabel.put(tryCatchBlockStart,
						new ArrayList<TryCatchBlock>());
			}
			final List<TryCatchBlock> tryCatchBlocks = tryCatchBlocksGroupedByStartLabel
					.get(tryCatchBlockStart);
			tryCatchBlocks.add(new TryCatchBlock(start, end, type, handler));
		}

		super.visitTryCatchBlock(start, end, handler, type);
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
		allJumps.add(new Jump(lastInsn, label));
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
		// Complete list of jumps
		allJumps.addAll(jumps);

		final List<FinallyBlockEndInstructions> finallyBlockEndInstructions = new ArrayList<FinallyBlockEndInstructions>();

		final Map<Label, List<Label>> finallyBlockGroups = computeFinallyBlockGroups();
		if (finallyBlockGroups.size() > 0) {
			System.out.println("# Coverage of: " + coverage.getName());
		}

		while (!finallyBlockGroups.isEmpty()) {
			final Entry<Label, List<Label>> entry = removeFirstEntryFromMap(finallyBlockGroups);
			final List<Label> finallyBlockStarts = entry.getValue();

			final int[] methodAtomPointers = new int[entry.getValue().size()];
			for (int ii = 0; ii < methodAtomPointers.length; ii++) {
				methodAtomPointers[ii] = methodAtoms.indexOf(new MethodAtom(
						finallyBlockStarts.get(ii)));
				// Skip over the Label
				methodAtomPointers[ii]++;
			}
			// Skip over extra instruction (astore - save caught exception) and
			// label in catch(anything) block
			methodAtomPointers[0] += 2;

			System.out.println("Examining try/catch/block");
			System.out.println("Starting pointers: "
					+ Arrays.toString(methodAtomPointers));

			// Initialize finally block end insn ponters array
			final int[] methodInsnPointers = new int[methodAtomPointers.length];
			for (int ii = 0; ii < methodInsnPointers.length; ii++) {
				methodInsnPointers[ii] = methodAtomPointers[ii];
			}

			scanningLoop: while (true) {
				// Examine all current pointers
				if (isInconsistentAtomTypes(methodAtomPointers)) {
					break scanningLoop;
				}

				printConsistentPointers(methodAtomPointers);

				boolean isInsnAtom = false;
				if (isProbeAtom(methodAtomPointers[0])) {
					if (isAnyNonPrimaryProbeCovered(methodAtomPointers)) {
						markPrimaryFinallyBlockProbeCovered(methodAtomPointers);
					}
				} else if (isLabelAtom(methodAtomPointers[0])) {
					collapseDuplicateTryFinallyBlocks(methodAtomPointers,
							finallyBlockGroups);
				} else if (isInsnAtom(methodAtomPointers[0])) {
					isInsnAtom = true;
					if (isInconsistentInsns(methodAtomPointers)) {
						break scanningLoop;
					} else {
						disableCoverageOfNonPrimaryFinallyBlock(methodAtomPointers);
					}
				}

				// Copy latest pointers into the end array
				if (isInsnAtom) {
					for (int ii = 0; ii < methodAtomPointers.length; ii++) {
						methodInsnPointers[ii] = methodAtomPointers[ii];
					}
				}

				// Increment all pointers
				for (int ii = 0; ii < methodAtomPointers.length; ii++) {
					methodAtomPointers[ii]++;
					if ((methodAtomPointers[ii] >= methodAtoms.size())
							|| (methodAtomPointers[ii] < 0)) {
						break scanningLoop;
					}
				}
			}

			System.out.println("End pointers: "
					+ Arrays.toString(methodInsnPointers));

			// Print some insns after the end of the consistent insns
			for (int ii = 1; ii <= 5; ii++) {
				final int[] lookahead = new int[methodInsnPointers.length];
				for (int jj = 0; jj < lookahead.length; jj++) {
					lookahead[jj] = methodInsnPointers[jj] + ii;
				}
				printInconsistentPointers(lookahead);
			}

			// Skip extra label in exception copies and unify final probes
			if (isProbeAtom(methodInsnPointers[0] + 1)) {
				methodInsnPointers[0] += 1;
				for (int ii = 1; ii < methodInsnPointers.length; ii++) {
					methodInsnPointers[ii] += 1;
					if (!isProbeAtom(methodInsnPointers[ii])) {
						methodInsnPointers[ii] += 1;
					}
				}
				if (isConsistentAtomTypes(methodInsnPointers)
						&& isProbeAtom(methodInsnPointers[0])) {
					printConsistentPointers(methodInsnPointers);
					if (isAnyNonPrimaryProbeCovered(methodInsnPointers)) {
						markPrimaryFinallyBlockProbeCovered(methodInsnPointers);
					}
				}

				System.out.println("A: Pre-Fixed end insn pointers: "
						+ Arrays.toString(methodInsnPointers));

				// Seek to the final instructions
				methodInsnPointers[0] += 4;
				for (int ii = 1; ii < (methodInsnPointers.length - 1); ii++) {
					methodInsnPointers[ii] += 1;
				}
				if (isInsnAtom(methodInsnPointers[methodInsnPointers.length - 1] + 1)) {
					methodInsnPointers[methodInsnPointers.length - 1] += 1;
				} else {
					methodInsnPointers[methodInsnPointers.length - 1] -= 1;
				}
				disableCoverageOfNonPrimaryFinallyBlock(methodInsnPointers);
			} else {
				System.out.println("B: Pre-Fixed end insn pointers: "
						+ Arrays.toString(methodInsnPointers));

				// Seek to the final instructions
				for (int ii = 3; ii <= 4; ii++) {
					if (isInsnAtom(methodInsnPointers[0] + ii)) {
						methodInsnPointers[0] += ii;
						break;
					}
				}
				for (int ii = 1; ii < (methodInsnPointers.length - 1); ii++) {
					methodInsnPointers[ii] += 2;
				}
				disableCoverageOfNonPrimaryFinallyBlock(methodInsnPointers);
			}

			System.out.println("Fixed end insn pointers: "
					+ Arrays.toString(methodInsnPointers));

			// Construct details about the end of each finally block
			if (methodInsnPointers[0] > -1) {
				final Instruction primaryEndInsn = methodAtoms
						.get(methodInsnPointers[0]).instruction;
				final List<Instruction> otherEndInsns = new ArrayList<Instruction>();
				for (int ii = 1; ii < methodInsnPointers.length; ii++) {
					final Instruction insn = methodAtoms
							.get(methodInsnPointers[ii]).instruction;
					otherEndInsns.add(insn);
				}

				final FinallyBlockEndInstructions block = new FinallyBlockEndInstructions(
						primaryEndInsn, otherEndInsns);
				finallyBlockEndInstructions.add(block);
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
		for (final FinallyBlockEndInstructions block : finallyBlockEndInstructions) {
			if (!isCovered(block.catchAnythingEndInstructions)
					&& isAnyCovered(block.otherEndInstructions)) {
				block.catchAnythingEndInstructions.setCovered();
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

	private static class FinallyBlockEndInstructions {
		private final Instruction catchAnythingEndInstructions;
		private final List<Instruction> otherEndInstructions;

		public FinallyBlockEndInstructions(
				final Instruction catchAnythingEndInstructions,
				final List<Instruction> otherEndInstructions) {
			this.catchAnythingEndInstructions = catchAnythingEndInstructions;
			this.otherEndInstructions = otherEndInstructions;
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
			final int[] methodAtomPointers,
			final Map<Label, List<Label>> finallyBlockGroups) {
		Label firstLabel = null;
		final List<Label> finallyDups = new ArrayList<Label>();
		for (int ii = 0; ii < methodAtomPointers.length; ii++) {
			final Label tryBlockStart = methodAtoms.get(methodAtomPointers[ii]).label;

			if (finallyBlockGroups.containsKey(tryBlockStart)) {
				if (firstLabel == null) {
					firstLabel = tryBlockStart;
				}

				finallyDups.addAll(finallyBlockGroups.remove(tryBlockStart));
			}
		}

		if (firstLabel != null) {
			finallyBlockGroups.put(firstLabel, finallyDups);
		}
	}

	private void markPrimaryFinallyBlockProbeCovered(
			final int[] finallyBlockPointers) {
		final int probeIndex = methodAtoms.get(finallyBlockPointers[0]).probeIndex;
		if (!probes[probeIndex]) {
			final Instruction probeInsn = probeIndexToInstructionPriorToProbe
					.get(Integer.valueOf(probeIndex));
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

	private Map<Label, List<Label>> computeFinallyBlockGroups() {
		// @formatter:off
		/*
		There are two try/catch block layouts which are used:
		
		1) no-exception block at the end:
		
		0.{
		1.{
		2.{
		  A()
		  goto NoEx
		1.} catch (Ex1) => Ex1:
		2.} catch (Ex1) => Ex2:
		Ex1.{
		  doEx1()
		0.} catch (Anything) => Anything:
		  doFinally()
		  goto AfterTry
		}
		Ex2.{
		3. {
		  doEx1()
		3.} catch (Anything) => Anything:
		  doFinally()
		  goto AfterTry
		}
		Anything.{
			doFinally()
			rethrow ex;
		}
		NoEx.{
			doFinally()
		}
		AfterTry:
		
		2) no-exception block without jump:
			
		0.{
		1.{
		2.{
		  A()
		0.} catch (Anything) =	> Anything:
		1.} catch (Ex1) => Ex1:
		2.} catch (Ex1) => Ex2:
		  doFinally()
		  goto AfterTry
		Ex1.{
		3. {
		  doEx1()
		3.} catch (Anything) => Anything:
		  doFinally()
		  goto AfterTry
		}
		Ex2.{
		4. {
		  doEx1()
		4.} catch (Anything) => Anything:
		  doFinally()
		  goto AfterTry
		}
		Anything.{
			doFinally()
			rethrow ex;
		}
		AfterTry:
		 */
		
		// @formatter:on

		// finally block duplicates:
		// - 1x finally block handler
		// - nx catch block finally handlers
		// - 1x no-exception handler

		final Map<Label, List<Label>> finallyBlockStartLabelsGroups = new LinkedHashMap<Label, List<Label>>();

		for (final Entry<Label, List<TryCatchBlock>> tryBlockGroupEntry : tryCatchBlocksGroupedByStartLabel
				.entrySet()) {
			final Label tryBlockStart = tryBlockGroupEntry.getKey();

			boolean seenFinally = false;
			boolean seenCatch = false;
			Label catchEnd = null;
			for (final TryCatchBlock tryBlock : tryBlockGroupEntry.getValue()) {
				if (tryBlock.catchType == null) {
					seenFinally = true;
				} else {
					seenCatch = true;
					catchEnd = tryBlock.end;
				}
			}

			if (!seenFinally) {
				continue;
			}

			final List<Label> finallyBlockStarts = new ArrayList<Label>();

			// - 1x finally block handler
			for (final TryCatchBlock tryBlock : tryBlockGroupEntry.getValue()) {
				if (tryBlock.catchType == null) {
					if (tryBlock.start == tryBlockStart) {
						finallyBlockStarts.add(tryBlock.handler);
					}
				}
			}

			// - nx catch block finally handlers
			if (seenCatch) {
				catchBlocks: for (final TryCatchBlock tryBlock : tryBlockGroupEntry
						.getValue()) {
					if ((tryBlock.catchType == null)
							&& (tryBlock.end != catchEnd)) {
						final Label finallyBlock = tryBlock.end;

						final int finallyBlockIndex = methodAtoms
								.indexOf(new MethodAtom(finallyBlock));
						for (int ii = 3; ii >= 1; ii--) {
							if (isInsnAtom(finallyBlockIndex - ii)) {
								final int insnOpcode = methodAtoms
										.get(finallyBlockIndex - ii).instruction
										.getOpcode();
								if (insnOpcode == Opcodes.ATHROW) {
									continue catchBlocks;
								}
							}
						}

						if (!finallyBlockStarts.contains(finallyBlock)) {
							finallyBlockStarts.add(finallyBlock);
						}
					}
				}
			}

			// - 1x no-exception handler
			final TryCatchBlock firstTryCatchBlock = tryBlockGroupEntry
					.getValue().get(0);
			final int firstTryBlockEndIndex = methodAtoms
					.indexOf(new MethodAtom(firstTryCatchBlock.end));

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

			Instruction gotoInsn = null;
			for (int ii = -1; ii <= 3; ii++) {
				if (isInsnAtom(firstTryBlockEndIndex + ii)) {
					final Instruction insn = methodAtoms
							.get(firstTryBlockEndIndex + ii).instruction;

					if (insn.getOpcode() == Opcodes.GOTO) {
						gotoInsn = insn;
						break;
					}

					if (ii >= 0) {
						break;
					}
				}
			}

			Label noExceptionBlockStart = null;
			if (gotoInsn != null) {
				for (final Jump jump : allJumps) {
					if (jump.source == gotoInsn) {
						noExceptionBlockStart = jump.target;
						break;
					}
				}
			} else {
				final int endPointer = methodAtoms.indexOf(new MethodAtom(
						firstTryCatchBlock.end));
				final int handlerPointer = methodAtoms.indexOf(new MethodAtom(
						firstTryCatchBlock.handler));

				if (endPointer == (handlerPointer + 2)) {
					// Suppress handler start label
				} else {
					noExceptionBlockStart = firstTryCatchBlock.end;
				}
			}

			if (noExceptionBlockStart != null) {
				finallyBlockStarts.add(noExceptionBlockStart);
			}

			if (finallyBlockStarts.size() > 1) {
				finallyBlockStartLabelsGroups.put(tryBlockStart,
						finallyBlockStarts);
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
			probeIndexToInstructionPriorToProbe.put(Integer.valueOf(probeId),
					lastInsn);
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
