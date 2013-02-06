/*******************************************************************************
 * Copyright (c) 2009, 2013 Mountainminds GmbH & Co. KG and Contributors
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Martin Hare Robertson - initial API and implementation
 *    
 *******************************************************************************/
package org.jacoco.core.test.validation;

import java.lang.reflect.Method;

import org.jacoco.core.analysis.ICounter;
import org.jacoco.core.test.validation.targets.Target14;
import org.junit.Test;

public class FinallyBlocksTest extends ValidationTestBase {

	public FinallyBlocksTest() {
		super(Target14.class);
	}

	@Override
	protected void run(final Class<?> targetClass) throws Exception {
		// Launch the target class with a single argument
		Method mainMethod = targetClass.getMethod("main", String[].class);
		mainMethod.invoke(Target14.class, new Object[] { new String[0] });
	}

	@Test
	public void testCoverageResult() {

		assertLine("inner-finallyif", ICounter.FULLY_COVERED, 1, 1);
		assertLine("outer-finallyif", ICounter.FULLY_COVERED, 1, 1);
		assertLine("forloop", ICounter.PARTLY_COVERED, 1, 1);

	}

}
