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

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.jacoco.core.analysis.IDirectivesParser;
import org.jacoco.core.analysis.IDirectivesParser.Directive;
import org.jacoco.core.analysis.ILine;
import org.jacoco.core.analysis.IMethodCoverage;
import org.jacoco.core.internal.analysis.filters.CommentExclusionsCoverageFilter;
import org.jacoco.core.internal.analysis.filters.ICoverageFilterStatus.ICoverageFilter;
import org.jacoco.core.internal.flow.IProbeIdGenerator;
import org.jacoco.core.internal.flow.LabelFlowAnalyzer;
import org.jacoco.core.internal.flow.MethodProbesAdapter;
import org.junit.Before;
import org.junit.Test;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;

/**
 * Unit tests for {@link MethodAnalyzer}.
 */
public class MethodAnalyzerTest implements IProbeIdGenerator {

	private int nextProbeId;

	private boolean[] probes;

	private MethodNode method;

	private IMethodCoverage result;

	private List<Directive> coverageDirectives;

	@Before
	public void setup() {
		nextProbeId = 0;
		method = new MethodNode();
		method.tryCatchBlocks = new ArrayList<TryCatchBlockNode>();
		probes = new boolean[32];
		coverageDirectives = new LinkedList<Directive>();
	}

	public int nextId() {
		return nextProbeId++;
	}

	// === Scenario: linear Sequence without branches ===

	private void createLinearSequence() {
		method.visitLineNumber(1001, new Label());
		method.visitInsn(Opcodes.NOP);
		method.visitLineNumber(1002, new Label());
		method.visitInsn(Opcodes.RETURN);
	}

	@Test
	public void testLinearSequenceNotCovered1() {
		createLinearSequence();
		runMethodAnalzer();
		assertEquals(1, nextProbeId);

		assertLine(1001, 1, 0, 0, 0);
		assertLine(1002, 1, 0, 0, 0);
	}

	@Test
	public void testLinearSequenceNotCovered2() {
		createLinearSequence();
		probes = null;
		runMethodAnalzer();
		assertEquals(1, nextProbeId);

		assertLine(1001, 1, 0, 0, 0);
		assertLine(1002, 1, 0, 0, 0);
	}

	@Test
	public void testLinearSequenceCovered() {
		createLinearSequence();
		probes[0] = true;
		runMethodAnalzer();

		assertLine(1001, 0, 1, 0, 0);
		assertLine(1002, 0, 1, 0, 0);
	}

	@Test
	public void testLinearSequenceCoverageDisabled() {
		createLinearSequence();
		coverageDirectives.add(new Directive(1001, false));
		runMethodAnalzerWithCoverageDirectivesFilter();

		assertLine(1001, 0, 0, 0, 0);
		assertLine(1002, 0, 0, 0, 0);
	}

	@Test
	public void testLinearSequenceCoveragePartiallyDisabled() {
		createLinearSequence();
		coverageDirectives.add(new Directive(1002, false));
		runMethodAnalzerWithCoverageDirectivesFilter();

		assertLine(1001, 1, 0, 0, 0);
		assertLine(1002, 0, 0, 0, 0);
	}

	@Test
	public void testLinearSequenceCoverageMultipleDirectivesDisabled() {
		createLinearSequence();
		coverageDirectives.add(new Directive(10, false));
		coverageDirectives.add(new Directive(20, true));
		coverageDirectives.add(new Directive(30, false));
		runMethodAnalzerWithCoverageDirectivesFilter();

		assertLine(1001, 0, 0, 0, 0);
		assertLine(1002, 0, 0, 0, 0);
	}

	@Test
	public void testLinearSequenceCoverageNoDirectives() {
		createLinearSequence();
		runMethodAnalzerWithCoverageDirectivesFilter();

		assertLine(1001, 1, 0, 0, 0);
		assertLine(1002, 1, 0, 0, 0);
	}

	// === Scenario: simple if branch ===

	private void createIfBranch() {
		method.visitLineNumber(1001, new Label());
		method.visitVarInsn(Opcodes.ILOAD, 1);
		Label l1 = new Label();
		method.visitJumpInsn(Opcodes.IFEQ, l1);
		method.visitLineNumber(1002, new Label());
		method.visitLdcInsn("a");
		method.visitInsn(Opcodes.ARETURN);
		method.visitLabel(l1);
		method.visitLineNumber(1003, l1);
		method.visitLdcInsn("b");
		method.visitInsn(Opcodes.ARETURN);
	}

	@Test
	public void testIfBranchNotCovered() {
		createIfBranch();
		runMethodAnalzer();
		assertEquals(2, nextProbeId);

		assertLine(1001, 2, 0, 2, 0);
		assertLine(1002, 2, 0, 0, 0);
		assertLine(1003, 2, 0, 0, 0);
	}

	@Test
	public void testIfBranchCovered1() {
		createIfBranch();
		probes[0] = true;
		runMethodAnalzer();

		assertLine(1001, 0, 2, 1, 1);
		assertLine(1002, 0, 2, 0, 0);
		assertLine(1003, 2, 0, 0, 0);
	}

	@Test
	public void testIfBranchCovered2() {
		createIfBranch();
		probes[1] = true;
		runMethodAnalzer();

		assertLine(1001, 0, 2, 1, 1);
		assertLine(1002, 2, 0, 0, 0);
		assertLine(1003, 0, 2, 0, 0);
	}

	@Test
	public void testIfBranchCovered3() {
		createIfBranch();
		probes[0] = true;
		probes[1] = true;
		runMethodAnalzer();

		assertLine(1001, 0, 2, 0, 2);
		assertLine(1002, 0, 2, 0, 0);
		assertLine(1003, 0, 2, 0, 0);
	}

	@Test
	public void testIfBranchCoverageDisabled() {
		createIfBranch();
		coverageDirectives.add(new Directive(1001, false));
		runMethodAnalzerWithCoverageDirectivesFilter();
		assertEquals(2, nextProbeId);

		assertLine(1001, 0, 0, 0, 0);
		assertLine(1002, 0, 0, 0, 0);
		assertLine(1003, 0, 0, 0, 0);
	}

	@Test
	public void testIfBranchElseCoverageDisabled() {
		createIfBranch();
		probes[0] = true;
		coverageDirectives.add(new Directive(1003, false));
		runMethodAnalzerWithCoverageDirectivesFilter();
		assertEquals(2, nextProbeId);

		assertLine(1001, 0, 2, 0, 0);
		assertLine(1002, 0, 2, 0, 0);
		assertLine(1003, 0, 0, 0, 0);
	}

	// === Scenario: branch which merges back ===

	private void createIfBranchMerge() {
		method.visitLineNumber(1001, new Label());
		method.visitVarInsn(Opcodes.ILOAD, 1);
		Label l1 = new Label();
		method.visitJumpInsn(Opcodes.IFEQ, l1);
		method.visitLineNumber(1002, new Label());
		method.visitInsn(Opcodes.NOP);
		method.visitLabel(l1);
		method.visitLineNumber(1003, l1);
		method.visitInsn(Opcodes.RETURN);
	}

	@Test
	public void testIfBranchMergeNotCovered() {
		createIfBranchMerge();
		runMethodAnalzer();
		assertEquals(3, nextProbeId);

		assertLine(1001, 2, 0, 2, 0);
		assertLine(1002, 1, 0, 0, 0);
		assertLine(1003, 1, 0, 0, 0);
	}

	@Test
	public void testIfBranchMergeCovered1() {
		createIfBranchMerge();
		probes[0] = true;
		runMethodAnalzer();

		assertLine(1001, 0, 2, 1, 1);
		assertLine(1002, 1, 0, 0, 0);
		assertLine(1003, 1, 0, 0, 0);
	}

	@Test
	public void testIfBranchMergeCovered2() {
		createIfBranchMerge();
		probes[1] = true;
		runMethodAnalzer();

		assertLine(1001, 0, 2, 1, 1);
		assertLine(1002, 0, 1, 0, 0);
		assertLine(1003, 1, 0, 0, 0);
	}

	@Test
	public void testIfBranchMergeCovered3() {
		createIfBranchMerge();
		probes[0] = true;
		probes[1] = true;
		probes[2] = true;
		runMethodAnalzer();

		assertLine(1001, 0, 2, 0, 2);
		assertLine(1002, 0, 1, 0, 0);
		assertLine(1003, 0, 1, 0, 0);
	}

	@Test
	public void testIfBranchMergeCoverageDisabled() {
		createIfBranchMerge();
		coverageDirectives.add(new Directive(1001, false));
		runMethodAnalzerWithCoverageDirectivesFilter();

		assertLine(1001, 0, 0, 0, 0);
		assertLine(1002, 0, 0, 0, 0);
		assertLine(1003, 0, 0, 0, 0);
	}

	@Test
	public void testIfBranchMergeCoverageSkipIf() {
		createIfBranchMerge();
		coverageDirectives.add(new Directive(1001, false));
		coverageDirectives.add(new Directive(1003, true));
		runMethodAnalzerWithCoverageDirectivesFilter();

		assertLine(1001, 0, 0, 0, 0);
		assertLine(1002, 0, 0, 0, 0);
		assertLine(1003, 1, 0, 0, 0);
	}

	// === Scenario: branch which jump backwards ===

	private void createJumpBackwards() {
		method.visitLineNumber(1001, new Label());
		final Label l1 = new Label();
		method.visitJumpInsn(Opcodes.GOTO, l1);
		final Label l2 = new Label();
		method.visitLabel(l2);
		method.visitLineNumber(1002, l2);
		method.visitInsn(Opcodes.RETURN);
		method.visitLabel(l1);
		method.visitLineNumber(1003, l1);
		method.visitJumpInsn(Opcodes.GOTO, l2);
	}

	@Test
	public void testJumpBackwardsNotCovered() {
		createJumpBackwards();
		runMethodAnalzer();
		assertEquals(1, nextProbeId);

		assertLine(1001, 1, 0, 0, 0);
		assertLine(1002, 1, 0, 0, 0);
		assertLine(1003, 1, 0, 0, 0);
	}

	@Test
	public void testJumpBackwardsCovered() {
		createJumpBackwards();
		probes[0] = true;
		runMethodAnalzer();

		assertLine(1001, 0, 1, 0, 0);
		assertLine(1002, 0, 1, 0, 0);
		assertLine(1003, 0, 1, 0, 0);
	}

	// === Scenario: jump to first instruction ===

	private void createJumpToFirst() {
		final Label l1 = new Label();
		method.visitLabel(l1);
		method.visitLineNumber(1001, l1);
		method.visitVarInsn(Opcodes.ALOAD, 0);
		method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "Foo", "test", "()Z");
		method.visitJumpInsn(Opcodes.IFEQ, l1);
		final Label l2 = new Label();
		method.visitLabel(l2);
		method.visitLineNumber(1002, l2);
		method.visitInsn(Opcodes.RETURN);
	}

	@Test
	public void testJumpToFirstNotCovered() {
		createJumpToFirst();
		runMethodAnalzer();
		assertEquals(2, nextProbeId);

		assertLine(1001, 3, 0, 2, 0);
		assertLine(1002, 1, 0, 0, 0);
	}

	@Test
	public void testJumpToFirstCovered1() {
		createJumpToFirst();
		probes[0] = true;
		runMethodAnalzer();
		assertEquals(2, nextProbeId);

		assertLine(1001, 0, 3, 1, 1);
		assertLine(1002, 1, 0, 0, 0);
	}

	@Test
	public void testJumpToFirstCovered2() {
		createJumpToFirst();
		probes[0] = true;
		probes[1] = true;
		runMethodAnalzer();
		assertEquals(2, nextProbeId);

		assertLine(1001, 0, 3, 0, 2);
		assertLine(1002, 0, 1, 0, 0);
	}

	// === Scenario: table switch ===

	private void createTableSwitch() {
		method.visitLineNumber(1001, new Label());
		method.visitVarInsn(Opcodes.ILOAD, 1);
		Label l1 = new Label();
		Label l2 = new Label();
		Label l3 = new Label();
		method.visitTableSwitchInsn(1, 3, l3, new Label[] { l1, l2, l1 });
		method.visitLabel(l1);
		method.visitLineNumber(1002, l1);
		method.visitIntInsn(Opcodes.BIPUSH, 11);
		method.visitVarInsn(Opcodes.ISTORE, 2);
		method.visitLineNumber(1003, new Label());
		Label l5 = new Label();
		method.visitJumpInsn(Opcodes.GOTO, l5);
		method.visitLabel(l2);
		method.visitLineNumber(1004, l2);
		method.visitIntInsn(Opcodes.BIPUSH, 22);
		method.visitVarInsn(Opcodes.ISTORE, 2);
		method.visitLineNumber(1005, new Label());
		method.visitJumpInsn(Opcodes.GOTO, l5);
		method.visitLabel(l3);
		method.visitLineNumber(1006, l3);
		method.visitInsn(Opcodes.ICONST_0);
		method.visitVarInsn(Opcodes.ISTORE, 2);
		method.visitLabel(l5);
		method.visitLineNumber(1007, l5);
		method.visitVarInsn(Opcodes.ILOAD, 2);
		method.visitInsn(Opcodes.IRETURN);
	}

	@Test
	public void testTableSwitchNotCovered() {
		createTableSwitch();
		runMethodAnalzer();
		assertEquals(4, nextProbeId);

		assertLine(1001, 2, 0, 3, 0);
		assertLine(1002, 2, 0, 0, 0);
		assertLine(1003, 1, 0, 0, 0);
		assertLine(1004, 2, 0, 0, 0);
		assertLine(1005, 1, 0, 0, 0);
		assertLine(1006, 2, 0, 0, 0);
		assertLine(1007, 2, 0, 0, 0);
	}

	@Test
	public void testTableSwitchCovered1() {
		createTableSwitch();
		probes[0] = true;
		probes[3] = true;
		runMethodAnalzer();
		assertEquals(4, nextProbeId);

		assertLine(1001, 0, 2, 2, 1);
		assertLine(1002, 0, 2, 0, 0);
		assertLine(1003, 0, 1, 0, 0);
		assertLine(1004, 2, 0, 0, 0);
		assertLine(1005, 1, 0, 0, 0);
		assertLine(1006, 2, 0, 0, 0);
		assertLine(1007, 0, 2, 0, 0);
	}

	@Test
	public void testTableSwitchCovered2() {
		createTableSwitch();
		probes[2] = true;
		probes[3] = true;
		runMethodAnalzer();
		assertEquals(4, nextProbeId);

		assertLine(1001, 0, 2, 2, 1);
		assertLine(1002, 2, 0, 0, 0);
		assertLine(1003, 1, 0, 0, 0);
		assertLine(1004, 2, 0, 0, 0);
		assertLine(1005, 1, 0, 0, 0);
		assertLine(1006, 0, 2, 0, 0);
		assertLine(1007, 0, 2, 0, 0);
	}

	@Test
	public void testTableSwitchCovered3() {
		createTableSwitch();
		probes[0] = true;
		probes[1] = true;
		probes[2] = true;
		probes[3] = true;
		runMethodAnalzer();
		assertEquals(4, nextProbeId);

		assertLine(1001, 0, 2, 0, 3);
		assertLine(1002, 0, 2, 0, 0);
		assertLine(1003, 0, 1, 0, 0);
		assertLine(1004, 0, 2, 0, 0);
		assertLine(1005, 0, 1, 0, 0);
		assertLine(1006, 0, 2, 0, 0);
		assertLine(1007, 0, 2, 0, 0);
	}

	@Test
	public void testTableSwitchDefaultCoverageDisabled() {
		createTableSwitch();
		coverageDirectives.add(new Directive(1006, false));
		coverageDirectives.add(new Directive(1007, true));
		runMethodAnalzerWithCoverageDirectivesFilter();
		assertEquals(4, nextProbeId);

		assertLine(1001, 2, 0, 2, 0);
		assertLine(1002, 2, 0, 0, 0);
		assertLine(1003, 1, 0, 0, 0);
		assertLine(1004, 2, 0, 0, 0);
		assertLine(1005, 1, 0, 0, 0);
		assertLine(1006, 0, 0, 0, 0);
		assertLine(1007, 2, 0, 0, 0);
	}

	// === Scenario: table switch with merge ===

	private void createTableSwitchMerge() {
		method.visitLineNumber(1001, new Label());
		method.visitInsn(Opcodes.ICONST_0);
		method.visitVarInsn(Opcodes.ISTORE, 2);
		method.visitLineNumber(1002, new Label());
		method.visitVarInsn(Opcodes.ILOAD, 1);
		Label l2 = new Label();
		Label l3 = new Label();
		Label l4 = new Label();
		method.visitTableSwitchInsn(1, 3, l4, new Label[] { l2, l3, l2 });
		method.visitLabel(l2);
		method.visitLineNumber(1003, l2);
		method.visitIincInsn(2, 1);
		method.visitLabel(l3);
		method.visitLineNumber(1004, l3);
		method.visitIincInsn(2, 1);
		method.visitLabel(l4);
		method.visitLineNumber(1005, l4);
		method.visitVarInsn(Opcodes.ILOAD, 2);
		method.visitInsn(Opcodes.IRETURN);
	}

	@Test
	public void testTableSwitchMergeNotCovered() {
		createTableSwitchMerge();
		runMethodAnalzer();
		assertEquals(5, nextProbeId);

		assertLine(1001, 2, 0, 0, 0);
		assertLine(1002, 2, 0, 3, 0);
		assertLine(1003, 1, 0, 0, 0);
		assertLine(1004, 1, 0, 0, 0);
		assertLine(1005, 2, 0, 0, 0);
	}

	@Test
	public void testTableSwitchMergeNotCovered1() {
		createTableSwitchMerge();
		probes[0] = true;
		probes[4] = true;
		runMethodAnalzer();
		assertEquals(5, nextProbeId);

		assertLine(1001, 0, 2, 0, 0);
		assertLine(1002, 0, 2, 2, 1);
		assertLine(1003, 1, 0, 0, 0);
		assertLine(1004, 1, 0, 0, 0);
		assertLine(1005, 0, 2, 0, 0);
	}

	@Test
	public void testTableSwitchMergeNotCovered2() {
		createTableSwitchMerge();
		probes[1] = true;
		probes[3] = true;
		probes[4] = true;
		runMethodAnalzer();
		assertEquals(5, nextProbeId);

		assertLine(1001, 0, 2, 0, 0);
		assertLine(1002, 0, 2, 2, 1);
		assertLine(1003, 1, 0, 0, 0);
		assertLine(1004, 0, 1, 0, 0);
		assertLine(1005, 0, 2, 0, 0);
	}

	@Test
	public void testTableSwitchMergeNotCovered3() {
		createTableSwitchMerge();
		probes[2] = true;
		probes[3] = true;
		probes[4] = true;
		runMethodAnalzer();
		assertEquals(5, nextProbeId);

		assertLine(1001, 0, 2, 0, 0);
		assertLine(1002, 0, 2, 2, 1);
		assertLine(1003, 0, 1, 0, 0);
		assertLine(1004, 0, 1, 0, 0);
		assertLine(1005, 0, 2, 0, 0);
	}

	@Test
	public void testTableSwitchMergeNotCovered4() {
		createTableSwitchMerge();
		probes[0] = true;
		probes[1] = true;
		probes[2] = true;
		probes[3] = true;
		probes[4] = true;
		runMethodAnalzer();
		assertEquals(5, nextProbeId);

		assertLine(1001, 0, 2, 0, 0);
		assertLine(1002, 0, 2, 0, 3);
		assertLine(1003, 0, 1, 0, 0);
		assertLine(1004, 0, 1, 0, 0);
		assertLine(1005, 0, 2, 0, 0);
	}

	// === Scenario: try/catch block ===

	private void createTryCatchBlock() {
		Label l1 = new Label();
		Label l2 = new Label();
		Label l3 = new Label();
		Label l4 = new Label();
		method.visitTryCatchBlock(l1, l2, l3, "java/lang/Exception");
		method.visitLabel(l1);
		method.visitLineNumber(1001, l1);
		method.visitVarInsn(Opcodes.ALOAD, 0);
		method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Throwable",
				"printStackTrace", "()V");
		method.visitLabel(l2);
		method.visitJumpInsn(Opcodes.GOTO, l4);
		method.visitLabel(l3);
		method.visitLineNumber(1002, l3);
		method.visitVarInsn(Opcodes.ASTORE, 1);
		method.visitLabel(l4);
		method.visitLineNumber(1004, l4);
		method.visitInsn(Opcodes.RETURN);
	}

	@Test
	public void testTryCatchBlockNotCovered() {
		createTryCatchBlock();
		runMethodAnalzer();
		assertEquals(3, nextProbeId);
		assertEquals(CounterImpl.getInstance(5, 0),
				result.getInstructionCounter());

		assertLine(1001, 3, 0, 0, 0);
		assertLine(1002, 1, 0, 0, 0);
		assertLine(1004, 1, 0, 0, 0);
	}

	@Test
	public void testTryCatchBlockFullyCovered() {
		createTryCatchBlock();
		probes[0] = true;
		probes[1] = true;
		probes[2] = true;
		runMethodAnalzer();
		assertEquals(3, nextProbeId);
		assertEquals(CounterImpl.getInstance(0, 5),
				result.getInstructionCounter());

		assertLine(1001, 0, 3, 0, 0);
		assertLine(1002, 0, 1, 0, 0);
		assertLine(1004, 0, 1, 0, 0);
	}

	// === Scenario: try/catch/finally duplication ===

	private void createPrintLn(String arg) {
		method.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out",
				"Ljava/io/PrintStream;");
		method.visitLdcInsn(arg);
		method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream",
				"println", "(Ljava/lang/String;)V");
	}

	private void createTryCatchFinallySequence() {

		Label L00 = new Label();
		Label L01 = new Label();
		Label L02 = new Label();
		Label L03 = new Label();
		Label L04 = new Label();
		Label L05 = new Label();
		Label L06 = new Label();
		Label L07 = new Label();
		Label L08 = new Label();
		Label L09 = new Label();

		method.visitTryCatchBlock(L00, L01, L02, "java/lang/RuntimeException");
		method.visitTryCatchBlock(L00, L03, L04, null);

		/* try { System.out.println("A") */
		method.visitLabel(L00);
		method.visitLineNumber(1001, L00);
		createPrintLn("A");
		method.visitLabel(L01);
		method.visitJumpInsn(Opcodes.GOTO, L05);

		/* } catch (Exception e) { */
		method.visitLabel(L02);
		method.visitLineNumber(1002, L02);
		method.visitIntInsn(Opcodes.ASTORE, 1);

		/* System.out.println("B") */
		method.visitLabel(L06);
		method.visitLineNumber(1003, L06);
		createPrintLn("B");

		/* System.out.println("C") */
		method.visitLabel(L03);
		method.visitLineNumber(1005, L03);
		createPrintLn("C");
		method.visitJumpInsn(Opcodes.GOTO, L07);

		/* } catch (Anything) { */
		method.visitLabel(L04);
		method.visitLineNumber(1004, L04);
		method.visitIntInsn(Opcodes.ASTORE, 1);

		/* System.out.println("C") */
		method.visitLabel(L08);
		method.visitLineNumber(1005, L08);
		createPrintLn("C");

		/* Rethrow */
		method.visitLabel(L09);
		method.visitLineNumber(1006, L09);
		method.visitIntInsn(Opcodes.ALOAD, 2);
		method.visitInsn(Opcodes.ATHROW);

		/* System.out.println("C") */
		method.visitLabel(L05);
		method.visitLineNumber(1005, L05);
		createPrintLn("C");

		method.visitLabel(L07);
		method.visitLineNumber(1007, L07);
	}

	@Test
	public void testTryCatchFinallyUncovered() {
		createTryCatchFinallySequence();
		runMethodAnalzer();

		assertEquals(3, nextProbeId);

		assertLine(1001, 4, 0, 0, 0);
		assertLine(1002, 1, 0, 0, 0);
		assertLine(1003, 3, 0, 0, 0);
		assertLine(1004, 1, 0, 0, 0);
		assertLine(1005, 3, 0, 0, 0);
		assertLine(1006, 2, 0, 0, 0);
	}

	@Test
	public void testTryCatchFinallyExceptionBlockCovered() {
		createTryCatchFinallySequence();
		probes[0] = true;
		runMethodAnalzer();
		assertEquals(3, nextProbeId);

		assertLine(1001, 4, 0, 0, 0);
		assertLine(1002, 0, 1, 0, 0);
		assertLine(1003, 0, 3, 0, 0);
		assertLine(1004, 0, 1, 0, 0);
		assertLine(1005, 0, 3, 0, 0);
		assertLine(1006, 0, 2, 0, 0);
	}

	@Test
	public void testTryCatchFinallyCatchAnyBlockCovered() {
		createTryCatchFinallySequence();
		probes[1] = true;
		runMethodAnalzer();
		assertEquals(3, nextProbeId);

		assertLine(1001, 4, 0, 0, 0);
		assertLine(1002, 1, 0, 0, 0);
		assertLine(1003, 3, 0, 0, 0);
		assertLine(1004, 0, 1, 0, 0);
		assertLine(1005, 0, 3, 0, 0);
		assertLine(1006, 0, 2, 0, 0);
	}

	@Test
	public void testTryCatchFinallyNoExceptionBlockCovered() {
		createTryCatchFinallySequence();
		probes[2] = true;
		runMethodAnalzer();
		assertEquals(3, nextProbeId);

		assertLine(1001, 0, 4, 0, 0);
		assertLine(1002, 1, 0, 0, 0);
		assertLine(1003, 3, 0, 0, 0);
		assertLine(1004, 0, 1, 0, 0);
		assertLine(1005, 0, 3, 0, 0);
		assertLine(1006, 0, 2, 0, 0);
	}

	private void createTryCatchFinallyIfSequence() {

		Label L00 = new Label();
		Label L01 = new Label();
		Label L02 = new Label();
		Label L03 = new Label();
		Label L04 = new Label();
		Label L05 = new Label();
		Label L06 = new Label();
		Label L07 = new Label();
		Label L08 = new Label();
		Label L09 = new Label();
		Label L10 = new Label();
		Label L11 = new Label();
		Label L12 = new Label();
		Label L13 = new Label();

		method.visitTryCatchBlock(L00, L01, L02, "java/lang/RuntimeException");
		method.visitTryCatchBlock(L00, L03, L04, null);

		/* try { System.out.println("A") */
		method.visitLabel(L00);
		method.visitLineNumber(1001, L00);
		createPrintLn("A");
		method.visitLabel(L01);
		method.visitJumpInsn(Opcodes.GOTO, L05);

		/* } catch (Exception e) { */
		method.visitLabel(L02);
		method.visitLineNumber(1002, L02);
		method.visitIntInsn(Opcodes.ASTORE, 1);

		/* System.out.println("B") */
		method.visitLabel(L06);
		method.visitLineNumber(1003, L06);
		createPrintLn("B");

		/*
		 * if (value) { System.out.println("C") }
		 */
		method.visitLabel(L03);
		method.visitLineNumber(1005, L03);
		method.visitIntInsn(Opcodes.ILOAD, 1);
		// probe[0]
		method.visitJumpInsn(Opcodes.IFEQ, L07);

		/* System.out.println("C") */
		method.visitLabel(L08);
		method.visitLineNumber(1006, L08);
		createPrintLn("C");
		// probe[1]

		method.visitLabel(L09);
		method.visitJumpInsn(Opcodes.GOTO, L07);

		/* } catch (Anything) { */
		method.visitLabel(L04);
		method.visitLineNumber(1004, L04);
		method.visitIntInsn(Opcodes.ASTORE, 1);

		/*
		 * if (value) { System.out.println("C") }
		 */
		method.visitLabel(L10);
		method.visitLineNumber(1005, L10);
		method.visitIntInsn(Opcodes.ILOAD, 1);
		// probe[2]
		method.visitJumpInsn(Opcodes.IFEQ, L11);

		/* System.out.println("C") */
		method.visitLabel(L12);
		method.visitLineNumber(1006, L12);
		createPrintLn("C");
		// probe[3]

		/* Rethrow */
		method.visitLabel(L11);
		method.visitLineNumber(1007, L11);
		method.visitIntInsn(Opcodes.ALOAD, 2);
		// probe[4]
		method.visitInsn(Opcodes.ATHROW);

		/*
		 * if (value) { System.out.println("C") }
		 */
		method.visitLabel(L05);
		method.visitLineNumber(1005, L05);
		method.visitIntInsn(Opcodes.ILOAD, 1);
		// probe[5]
		method.visitJumpInsn(Opcodes.IFEQ, L07);

		/* System.out.println("C") */
		method.visitLabel(L13);
		method.visitLineNumber(1006, L13);
		createPrintLn("C");
		// probe[6]

		method.visitLabel(L07);
		method.visitLineNumber(1008, L07);
		// probe[7]
	}

	@Test
	public void testTryCatchFinallyIfUncovered() {
		createTryCatchFinallyIfSequence();
		runMethodAnalzer();

		assertEquals(7, nextProbeId);

		assertLine(1001, 4, 0, 0, 0);
		assertLine(1002, 1, 0, 0, 0);
		assertLine(1003, 3, 0, 0, 0);
		assertLine(1004, 1, 0, 0, 0);
		assertLine(1005, 2, 0, 2, 0);
		assertLine(1006, 3, 0, 0, 0);
		assertLine(1007, 2, 0, 0, 0);
	}

	@Test
	public void testTryCatchFinallyIfExceptionBlockCovered() {
		createTryCatchFinallyIfSequence();
		probes[1] = true;
		runMethodAnalzer();
		assertEquals(7, nextProbeId);

		assertLine(1001, 4, 0, 0, 0);
		assertLine(1002, 0, 1, 0, 0);
		assertLine(1003, 0, 3, 0, 0);
		assertLine(1004, 0, 1, 0, 0);
		assertLine(1005, 0, 2, 1, 1);
		assertLine(1006, 0, 3, 0, 0);
		assertLine(1007, 0, 2, 0, 0);
	}

	@Test
	public void testTryCatchFinallyIfCatchAnyBlockCovered() {
		createTryCatchFinallyIfSequence();
		probes[2] = true;
		probes[3] = true;
		probes[4] = true;
		runMethodAnalzer();
		assertEquals(7, nextProbeId);

		assertLine(1001, 4, 0, 0, 0);
		assertLine(1002, 1, 0, 0, 0);
		assertLine(1003, 3, 0, 0, 0);
		assertLine(1004, 0, 1, 0, 0);
		assertLine(1005, 0, 2, 0, 2);
		assertLine(1006, 0, 3, 0, 0);
		assertLine(1007, 0, 2, 0, 0);
	}

	@Test
	public void testTryCatchFinallyIfNoExceptionBlockCovered() {
		createTryCatchFinallyIfSequence();
		probes[6] = true;
		runMethodAnalzer();
		assertEquals(7, nextProbeId);

		assertLine(1001, 0, 4, 0, 0);
		assertLine(1002, 1, 0, 0, 0);
		assertLine(1003, 3, 0, 0, 0);
		assertLine(1004, 0, 1, 0, 0);
		assertLine(1005, 0, 2, 1, 1);
		assertLine(1006, 0, 3, 0, 0);
		assertLine(1007, 0, 2, 0, 0);
	}

	private void runMethodAnalzer() {
		runMethodAnalzer(new ICoverageFilter.NoFilter());
	}

	private void runMethodAnalzerWithCoverageDirectivesFilter() {
		IDirectivesParser parser = new IDirectivesParser() {
			public List<Directive> parseDirectives(String packageName,
					String sourceFilename) {
				return coverageDirectives;
			}
		};
		ICoverageFilter filter = new CommentExclusionsCoverageFilter(parser);
		runMethodAnalzer(filter);
	}

	private void runMethodAnalzer(ICoverageFilter filter) {
		LabelFlowAnalyzer.markLabels(method);
		final MethodAnalyzer analyzer = new MethodAnalyzer("doit", "()V", null,
				probes, filter, true, false);

		// Signal to the filter that a new class is being visited
		filter.includeClass("org/test/TestClass");
		ClassVisitor filterVisitor = filter.visitClass(null);
		if (filterVisitor != null) {
			filterVisitor.visitSource("TestClass.java", null);
		}

		// Run the analysis
		method.accept(new MethodProbesAdapter(filter.visitMethod("doit", "()V",
				analyzer), this));
		result = analyzer.getCoverage();
	}

	private void assertLine(int nr, int insnMissed, int insnCovered,
			int branchesMissed, int branchesCovered) {
		final ILine line = result.getLine(nr);
		assertEquals("Instructions in line " + nr,
				CounterImpl.getInstance(insnMissed, insnCovered),
				line.getInstructionCounter());
		assertEquals("Branches in line " + nr,
				CounterImpl.getInstance(branchesMissed, branchesCovered),
				line.getBranchCounter());
	}

}
