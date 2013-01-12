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
import java.util.HashMap;
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

	/** List of all predecessors of uncovered probes */
	private final List<Instruction> uncoveredProbes = new ArrayList<Instruction>();

	/** List of all predecessors of disabled probes */
	private final List<Instruction> disabledProbes = new ArrayList<Instruction>();

	/** List of all jumps encountered */
	private final List<Jump> jumps = new ArrayList<Jump>();

	/** Last instruction in byte code sequence */
	private Instruction lastInsn;

	/** Map: LineNo: List of sequences */
	private final Map<Integer, List<Sequence>> lineSequences = new HashMap<Integer, List<Sequence>>();

	/** Instructions on the current line */
	private Sequence currentLineSeq = null;

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
		currentLabel.add(label);
		if (!LabelInfo.isSuccessor(label)) {
			lastInsn = null;
		}
	}

	@Override
	public void visitLineNumber(final int line, final Label start) {
		currentLine = line;

		List<Sequence> sequences = lineSequences.get(Integer
				.valueOf(currentLine));
		if (sequences == null) {
			sequences = new ArrayList<Sequence>();
			lineSequences.put(Integer.valueOf(currentLine), sequences);
		}

		currentLineSeq = new Sequence();

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
		if (lastInsn != null) {
			insn.setPredecessor(lastInsn);
		}

		if (currentLineSeq != null) {
			currentLineSeq.add(insn);
			if (currentLineSeq.size() == 1) {
				lineSequences.get(Integer.valueOf(currentLine)).add(
						currentLineSeq);
			}
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
		lastInsn = null;
	}

	@Override
	public void visitJumpInsnWithProbe(final int opcode, final Label label,
			final int probeId) {
		visitInsn(opcode);
		addProbe(probeId);
	}

	@Override
	public void visitInsnWithProbe(final int opcode, final int probeId) {
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
			}
			LabelInfo.setDone(label);
		}
	}

	@Override
	public void visitEnd() {
		// Cluster line duplicates
		/**
		 * List of line clusters, Each line cluster is a list of line
		 * duplicates, Each line duplicate is a list of instructions
		 */
		final Map<Integer, List<Cluster>> lineClusters = new HashMap<Integer, List<Cluster>>();
		for (final Entry<Integer, List<Sequence>> lineSequencesEntry : lineSequences
				.entrySet()) {
			if (lineSequencesEntry.getValue().size() > 1) {
				final List<Sequence> sequences = lineSequencesEntry.getValue();
				final List<Cluster> clusters = new ArrayList<Cluster>();
				for (final Sequence sequence : sequences) {
					addToExistingOrNewCluster(clusters, sequence);
				}

				final List<Cluster> clustersWithMoreThanOneItem = new ArrayList<Cluster>();
				for (final Cluster cluster : clusters) {
					if (cluster.size() > 1) {
						clustersWithMoreThanOneItem.add(cluster);
					}
				}

				if (clustersWithMoreThanOneItem.size() > 0) {
					lineClusters.put(lineSequencesEntry.getKey(),
							clustersWithMoreThanOneItem);
				}
			}
		}
		// Propagate coverage of line duplicates
		final List<Instruction> coveredProbesCopy = new ArrayList<Instruction>(
				coveredProbes);
		for (final Instruction p : coveredProbesCopy) {
			final Integer lineNumObj = Integer.valueOf(p.getLine());
			final List<Cluster> clusters = lineClusters.get(lineNumObj);
			if (clusters != null) {
				final Cluster cluster = findClusterContainingInsn(clusters, p);

				if (cluster != null) {
					final int sequenceIndex = cluster.findSequenceIndex(p);
					if (sequenceIndex > -1) {
						cluster.markSequenceIndexCovered(coveredProbes,
								uncoveredProbes, sequenceIndex);
					}
				}
			}
		}
		// Wire jumps:
		for (final Jump j : jumps) {
			LabelInfo.getInstruction(j.target).setPredecessor(j.source);
		}
		// Propagate probe values:
		for (final Instruction p : coveredProbes) {
			p.setCovered();
		}
		for (final Instruction p : disabledProbes) {
			p.setDisabled();
		}
		// Propagate coverage into line duplicates:
		for (final Entry<Integer, List<Cluster>> lineSequencesEntry : lineClusters
				.entrySet()) {
			final List<Cluster> clusters = lineSequencesEntry.getValue();
			for (final Cluster cluster : clusters) {
				final boolean coveredLastInsn = cluster
						.containsSequenceWithCoveredLastInsn();

				if (coveredLastInsn) {
					final Sequence sequence = cluster.get(0);
					final Instruction lastInsnInSequence = sequence
							.get(sequence.size() - 1);
					if (lastInsnInSequence.getCoveredBranches() == 0) {
						lastInsnInSequence.setLineCovered();
					}
				}

				cluster.disableAllButOneSequence();
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

	private Cluster findClusterContainingInsn(final List<Cluster> clusters,
			final Instruction p) {
		for (final Cluster cluster : clusters) {
			for (final Sequence sequence : cluster) {
				if (sequence.contains(p)) {
					return cluster;
				}
			}
		}
		return null;
	}

	private void addToExistingOrNewCluster(final List<Cluster> clusters,
			final Sequence sequence) {
		for (final Cluster cluster : clusters) {
			final Sequence example = cluster.get(0);

			// NOTE: We ignore ASTORE instructions at the start of a line
			// because the MethodSanitizer class shifts ASTORE instructions
			// which are on their own line onto the next line.

			int targetOpCode = example.get(0).getOpcode();
			if ((targetOpCode == Opcodes.ASTORE) && (example.size() > 1)) {
				targetOpCode = example.get(1).getOpcode();
			}

			int sequenceOpCode = sequence.get(0).getOpcode();
			if ((sequenceOpCode == Opcodes.ASTORE) && (sequence.size() > 1)) {
				sequenceOpCode = sequence.get(1).getOpcode();
			}

			if (targetOpCode == sequenceOpCode) {
				cluster.add(sequence);
				return;
			}
		}

		final Cluster cluster = new Cluster();
		cluster.add(sequence);

		clusters.add(cluster);
	}

	private void addProbe(final int probeId) {
		if (lastInsn != null) {
			lastInsn.addBranch();
			if (probes != null && probes[probeId]) {
				coveredProbes.add(lastInsn);
			} else {
				uncoveredProbes.add(lastInsn);
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
	 * List of instructions
	 */
	@SuppressWarnings("serial")
	private static class Sequence extends ArrayList<Instruction> {
	}

	/**
	 * Cluster of similar instruction sequences
	 */
	@SuppressWarnings("serial")
	private static class Cluster extends ArrayList<Sequence> {

		private int findSequenceIndex(final Instruction p) {
			for (final Sequence sequence : this) {
				final int index = sequence.indexOf(p);
				if (index > -1) {
					return index;
				}
			}
			return -1;
		}

		private void markSequenceIndexCovered(
				final List<Instruction> coveredProbes,
				final List<Instruction> uncoveredProbes, int sequenceIndex) {
			for (final Sequence sequence : this) {
				if (sequence.get(0).getOpcode() == Opcodes.ASTORE) {
					sequenceIndex++;
				}

				if (sequenceIndex < sequence.size()) {
					final Instruction coveredProbe = sequence
							.get(sequenceIndex);
					if (!coveredProbes.contains(coveredProbe)
							&& isCandidateProbe(uncoveredProbes, coveredProbe)) {
						coveredProbes.add(coveredProbe);
					}
				}
			}
		}

		private boolean isCandidateProbe(
				final List<Instruction> uncoveredProbes,
				final Instruction coveredProbe) {
			return uncoveredProbes.contains(coveredProbe);
		}

		private boolean containsSequenceWithCoveredLastInsn() {
			boolean seenCovered = false;
			for (final Sequence sequence : this) {
				final Instruction lastInsnInSequence = sequence.get(sequence
						.size() - 1);
				if (lastInsnInSequence.getCoveredBranches() > 0) {
					seenCovered = true;
				}
			}
			return seenCovered;
		}

		private void disableAllButOneSequence() {
			for (int index = 1; index < this.size(); index++) {
				for (final Instruction instruction : this.get(index)) {
					if (instruction.isCoverageEnabled()) {
						instruction.disable();
					}
				}
			}
		}

	}

}
