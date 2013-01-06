/*******************************************************************************
 * Copyright (c) 2009, 2012 Mountainminds GmbH & Co. KG and Contributors
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
 * Switch block with a disabled case. Used to test that the number of branches on the switch
 * line correctly reflects that one of the branches is entirely disabled.
 */
public class Target13 {
	public static void main(String[] xiArgs) {
		switch (xiArgs.length) { // $line-switch$
		case 0: {
			System.out.println("zero");
			break;
		}
		case 1: {
			System.out.println("one-1");
			///COVERAGE:OFF
			System.out.println("one-2");
			///COVERAGE:ON
			System.out.println("one-3");
			break;
		}
		///COVERAGE:OFF
		default: {
			System.out.println("default");
			break;
		}
		///COVERAGE:ON
		}
	}
}
