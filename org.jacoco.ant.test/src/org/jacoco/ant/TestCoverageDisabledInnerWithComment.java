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
package org.jacoco.ant;

/**
 * Class with coverage disabled without a comment
 */
public class TestCoverageDisabledInnerWithComment {

	public static class InnerClass {

		///COVERAGE:OFF source directive in inner class
		public static void innerMain(String[] args) {
			System.out.println("hello world!");
		}
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		InnerClass.innerMain(args);
	}
}
