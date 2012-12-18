/*******************************************************************************
 * Copyright (c) 2009, 2012 Mountainminds GmbH & Co. KG and Contributors
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Marc R. Hoffmann - initial API and implementation
 *    
 *******************************************************************************/
package org.jacoco.core.internal.flow;

/**
 * Representation of a byte code instruction for analysis. Internally used for
 * analysis.
 */
public class Instruction {

	private final int opcode;

	private final int line;

	private boolean coverageEnabled;

	private int branches;

	private int coveredBranches;

	private Instruction predecessor;

	/**
	 * New instruction at the given line.
	 * 
	 * @param opcode
	 *            opcode of this instruction. no args are included.
	 * @param line
	 *            source line this instruction belongs to
	 * @param coverageEnabled
	 *            whether coverage is enabled for this instruction
	 */
	public Instruction(final int opcode, final int line,
			final boolean coverageEnabled) {
		this.opcode = opcode;
		this.line = line;
		this.coverageEnabled = coverageEnabled;
		this.branches = 0;
		this.coveredBranches = 0;
	}

	/**
	 * Adds an branch to this instruction.
	 */
	public void addBranch() {
		branches++;
	}

	/**
	 * Sets the given instruction as a predecessor of this instruction. This
	 * will add an branch to the predecessor.
	 * 
	 * @see #addBranch()
	 * @param predecessor
	 *            predecessor instruction
	 */
	public void setPredecessor(final Instruction predecessor) {
		this.predecessor = predecessor;
		predecessor.addBranch();
	}

	/**
	 * Marks one branch of this instruction as covered. Also recursively marks
	 * all predecessor instructions as covered if this is the first covered
	 * branch.
	 */
	public void setCovered() {
		for (Instruction i = this; i != null && i.coveredBranches++ == 0;) {
			i = i.predecessor;
		}
	}

	/**
	 * Resets the number of branches for this instruction to zero. Also
	 * recursively updates a single previous coverageEnabled instruction to have
	 * one less branch.
	 */
	public void setDisabled() {
		for (Instruction i = this; i != null; i = i.predecessor) {
			if (i.coverageEnabled) {
				if (i.branches > 1) {
					i.branches--;
				}
				break;
			}
		}
	}

	/**
	 * Marks one branch of this instruction as covered. Also recursively marks
	 * all predecessor instructions as covered if this is the first covered
	 * branch up to the first instruction which is on a line earlier than the
	 * current line.
	 */
	public void setLineCovered() {
		for (Instruction i = this; i != null && i.coveredBranches++ == 0;) {
			i = i.predecessor;
			if ((i != null) && (i.line != this.line)) {
				break;
			}
		}
	}

	/**
	 * @return The opcode of this instruction
	 */
	public int getOpcode() {
		return opcode;
	}

	/**
	 * Returns the source line this instruction belongs to.
	 * 
	 * @return corresponding source line
	 */
	public int getLine() {
		return line;
	}

	/**
	 * Returns the total number of branches starting from this instruction.
	 * 
	 * @return total number of branches
	 */
	public int getBranches() {
		return branches;
	}

	/**
	 * Returns the number of covered branches starting from this instruction.
	 * 
	 * @return number of covered branches
	 */
	public int getCoveredBranches() {
		return coveredBranches;
	}

	/**
	 * @return true if the coverage is enabled
	 */
	public boolean isCoverageEnabled() {
		return coverageEnabled;
	}

	/**
	 * Mark this instruction is disabled
	 */
	public void disable() {
		coverageEnabled = false;
	}
}
