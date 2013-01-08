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
package org.jacoco.core.test.validation.targets;

/**
 * try/finally blocks including branches. The analyzer will collapse the
 * bytecode duplicates of the finally block.
 */
public class Target14 {
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			System.out.println("outer");
			try {
				System.out.println("inner");
			} catch (NullPointerException ex) {
				System.out.println("inner-npe");
			} finally {
				if (args.length == 0) { // $line-inner-finallyif$
					System.out.println("inner-finally");
				}
			}
		} catch (RuntimeException ex) {
			System.out.println("outer-rte");
		} finally {
			if (args.length == 0) { // $line-outer-finallyif$
				System.out.println("outer-finally");
			}
		}

		for (nop(); args.length != 0; nop()) { // $line-forloop$
			System.out.println("loop");
		}
	}

	private static void nop() {
		/* do nothing */
	}
}
