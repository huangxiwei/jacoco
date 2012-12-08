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

public class Target13 {
	public static void main(String[] xiArgs) {
		switch (xiArgs.length) { // $line-switch$
		case 0: {
			System.out.println("zero");
			break;
		}
		case 1: {
			System.out.println("one");
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
