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
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

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

	/** Last instruction in byte code sequence */
	private Instruction lastInsn;

	/** Map: LineNo: List of line duplicates: List of instructions */
	private final Map<Integer, List<List<Instruction>>> lineInsDups = new TreeMap<Integer, List<List<Instruction>>>();

	/** Instructions on the current line */
	private List<Instruction> currentLineIns = null;

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

		List<List<Instruction>> lineInsDup = lineInsDups.get(Integer
				.valueOf(currentLine));
		if (lineInsDup == null) {
			lineInsDup = new ArrayList<List<Instruction>>();
			lineInsDups.put(Integer.valueOf(currentLine), lineInsDup);
		}

		currentLineIns = new ArrayList<Instruction>();

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

		if (currentLineIns != null) {
			currentLineIns.add(insn);
			if (currentLineIns.size() == 1) {
				lineInsDups.get(Integer.valueOf(currentLine)).add(
						currentLineIns);
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
		final Map<Integer, List<List<List<Instruction>>>> lineInsDupClustersMap = new TreeMap<Integer, List<List<List<Instruction>>>>();
		for (final Entry<Integer, List<List<Instruction>>> lineInsDupEntry : lineInsDups
				.entrySet()) {
			if (lineInsDupEntry.getValue().size() > 1) {
				final List<List<Instruction>> lineInsDup = lineInsDupEntry
						.getValue();
				final List<List<List<Instruction>>> allclusters = new ArrayList<List<List<Instruction>>>();
				for (final List<Instruction> lineIns : lineInsDup) {
					addToCluster(allclusters, lineIns);
				}

				final List<List<List<Instruction>>> clusters = new ArrayList<List<List<Instruction>>>();
				for (final List<List<Instruction>> cluster : allclusters) {
					if (cluster.size() > 1) {
						clusters.add(cluster);
					}
				}

				if (clusters.size() > 0) {
					lineInsDupClustersMap.put(lineInsDupEntry.getKey(),
							clusters);
				}
			}
		}
		// Propagate coverage of line duplicates
		final List<Instruction> coveredProbesCopy = new ArrayList<Instruction>(
				coveredProbes);
		for (final Instruction p : coveredProbesCopy) {
			final Integer lineNumObj = Integer.valueOf(p.getLine());
			final List<List<List<Instruction>>> clusters = lineInsDupClustersMap
					.get(lineNumObj);
			if (clusters != null) {
				final List<List<Instruction>> cluster = findCluster(clusters, p);
				if (cluster != null) {
					final int coveredIndex = findIndex(cluster, p);
					if (coveredIndex > -1) {
						for (final List<Instruction> lineIns : cluster) {
							if (coveredIndex < lineIns.size()) {
								final Instruction coveredProbe = lineIns
										.get(coveredIndex);
								if (!coveredProbes.contains(coveredProbe)) {
									coveredProbes.add(coveredProbe);
								}
							}
						}
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
		for (final Entry<Integer, List<List<List<Instruction>>>> entry : lineInsDupClustersMap
				.entrySet()) {
			final List<List<List<Instruction>>> clusters = entry.getValue();
			for (final List<List<Instruction>> cluster : clusters) {
				boolean seenCovered = false;
				for (final List<Instruction> lineIns : cluster) {
					if (lineIns.get(lineIns.size() - 1).getCoveredBranches() > 0) {
						seenCovered = true;
					}
				}
				if (seenCovered) {
					final List<Instruction> lineIns = cluster.get(0);
					final Instruction lastLineIns = lineIns
							.get(lineIns.size() - 1);
					if (lastLineIns.getCoveredBranches() == 0) {
						lastLineIns.setLineCovered();
					}
				}
				// Disable all but the first duplicate
				for (int index = 1; index < cluster.size(); index++) {
					for (final Instruction instruction : cluster.get(index)) {
						if (instruction.isCoverageEnabled()) {
							instruction.disable();
						}
					}
				}
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

	private int findIndex(final List<List<Instruction>> cluster,
			final Instruction p) {
		for (final List<Instruction> lineIns : cluster) {
			final int index = lineIns.indexOf(p);
			if (index > -1) {
				return index;
			}
		}
		return -1;
	}

	private List<List<Instruction>> findCluster(
			final List<List<List<Instruction>>> clusters, final Instruction p) {
		for (final List<List<Instruction>> cluster : clusters) {
			for (final List<Instruction> lineIns : cluster) {
				if (lineIns.contains(p)) {
					return cluster;
				}
			}
		}
		return null;
	}

	private void addToCluster(final List<List<List<Instruction>>> clusters,
			final List<Instruction> lineIns) {
		for (final List<List<Instruction>> cluster : clusters) {
			final List<Instruction> example = cluster.get(0);

			// HACK: Ignore ASTORE instructions at the start of a line
			int exampleOpCode = example.get(0).getOpcode();
			if ((exampleOpCode == Opcodes.ASTORE) && (example.size() > 1)) {
				exampleOpCode = example.get(1).getOpcode();
			}

			// HACK: Ignore ASTORE instructions at the start of a line
			int lineInsOpCode = lineIns.get(0).getOpcode();
			if ((lineInsOpCode == Opcodes.ASTORE) && (lineIns.size() > 1)) {
				lineInsOpCode = lineIns.get(1).getOpcode();
			}

			if (exampleOpCode == lineInsOpCode) {
				cluster.add(lineIns);
				return;
			}
		}

		final List<List<Instruction>> cluster = new ArrayList<List<Instruction>>();
		cluster.add(lineIns);
		clusters.add(cluster);
	}

	private void addProbe(final int probeId) {
		if (lastInsn != null) {
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

}
