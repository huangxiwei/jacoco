/*******************************************************************************
 * Copyright (c) 2009, 2013 Mountainminds GmbH & Co. KG and Contributors
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Marc R. Hoffmann - initial API and implementation
 *    
 *******************************************************************************/
package org.jacoco.core.internal.analysis;

import java.util.ArrayList;
import java.util.List;

import org.jacoco.core.internal.analysis.filters.CompositeCoverageFilter;
import org.jacoco.core.internal.analysis.filters.EmptyConstructorCoverageFilter;
import org.jacoco.core.internal.analysis.filters.ICoverageFilterStatus.ICoverageFilter;
import org.jacoco.core.internal.analysis.filters.ImplicitEnumMethodsCoverageFilter;
import org.jacoco.core.internal.analysis.filters.SynchronizedExitCoverageFilter;
import org.jacoco.core.internal.instr.InstrSupport;
import org.junit.Before;
import org.junit.Test;
import org.objectweb.asm.Opcodes;

/**
 * Unit tests for {@link ClassAnalyzer}.
 */
public class ClassAnalyzerTest {

	private ClassAnalyzer analyzer;

	@Before
	public void setup() {

		final List<ICoverageFilter> filters = new ArrayList<ICoverageFilter>();
		filters.add(new ImplicitEnumMethodsCoverageFilter());
		filters.add(new EmptyConstructorCoverageFilter());
		filters.add(new SynchronizedExitCoverageFilter());

		analyzer = new ClassAnalyzer(0x0000, null, new StringPool(),
				new CompositeCoverageFilter(filters));
		analyzer.visit(Opcodes.V1_5, Opcodes.ACC_PUBLIC, "Foo", null,
				"java/lang/Object", null);
	}

	@Test(expected = IllegalStateException.class)
	public void testAnalyzeInstrumentedClass1() {
		analyzer.visitField(InstrSupport.DATAFIELD_ACC,
				InstrSupport.DATAFIELD_NAME, InstrSupport.DATAFIELD_DESC, null,
				null);
	}

	@Test(expected = IllegalStateException.class)
	public void testAnalyzeInstrumentedClass2() {
		analyzer.visitMethod(InstrSupport.INITMETHOD_ACC,
				InstrSupport.INITMETHOD_NAME, InstrSupport.INITMETHOD_DESC,
				null, null);
	}

}
