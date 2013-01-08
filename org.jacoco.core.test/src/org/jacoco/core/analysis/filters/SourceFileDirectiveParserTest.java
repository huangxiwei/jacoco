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
package org.jacoco.core.analysis.filters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.List;

import org.jacoco.core.analysis.IDirectivesParser;
import org.jacoco.core.analysis.IDirectivesParser.Directive;
import org.jacoco.core.data.ISourceFileLocator;
import org.junit.Test;

public class SourceFileDirectiveParserTest {
	@Test
	public void testSourceParsing() {
		ISourceFileLocator locator = new ISourceFileLocator() {
			public int getTabWidth() {
				return 0;
			}

			public Reader getSourceFile(String packageName, String fileName)
					throws IOException {
				return new StringReader("\n" + "  ///COVERAGE:OFF  \n" + "\n"
						+ "  ///COVERAGE:ON   \n" + "\n" + " ///CLOVER:OFF  \n"
						+ "\n" + "  ///CLOVER:ON  \n");
			}
		};
		IDirectivesParser.SourceFileDirectivesParser parser = new IDirectivesParser.SourceFileDirectivesParser(
				locator, false);
		List<Directive> directives = parser.parseDirectives(null, null);

		assertEquals(4, directives.size());
		assertEquals(2, directives.get(0).lineNum);
		assertFalse(directives.get(0).coverageOn);

		assertEquals(4, directives.get(1).lineNum);
		assertTrue(directives.get(1).coverageOn);

		assertEquals(6, directives.get(2).lineNum);
		assertFalse(directives.get(2).coverageOn);

		assertEquals(8, directives.get(3).lineNum);
		assertTrue(directives.get(3).coverageOn);
	}

	@Test
	public void testSourceParsingRequireComments() {
		ISourceFileLocator locator = new ISourceFileLocator() {
			public int getTabWidth() {
				return 0;
			}

			public Reader getSourceFile(String packageName, String fileName)
					throws IOException {
				return new StringReader("\n" + "  ///COVERAGE:OFF  \n" + "\n"
						+ "  ///COVERAGE:ON   \n" + "\n"
						+ " ///COVERAGE:OFF because  \n" + "\n"
						+ "  ///COVERAGE:ON  \n");
			}
		};
		IDirectivesParser.SourceFileDirectivesParser parser = new IDirectivesParser.SourceFileDirectivesParser(
				locator, true);
		List<Directive> directives = parser.parseDirectives(null, null);

		assertEquals(3, directives.size());
		assertEquals(4, directives.get(0).lineNum);
		assertTrue(directives.get(0).coverageOn);

		assertEquals(6, directives.get(1).lineNum);
		assertFalse(directives.get(1).coverageOn);

		assertEquals(8, directives.get(2).lineNum);
		assertTrue(directives.get(2).coverageOn);
	}

	@Test
	public void testMissingSourceParsing() {
		ISourceFileLocator locator = new ISourceFileLocator() {
			public int getTabWidth() {
				return 0;
			}

			public Reader getSourceFile(String packageName, String fileName)
					throws IOException {
				return null;
			}
		};
		IDirectivesParser.SourceFileDirectivesParser parser = new IDirectivesParser.SourceFileDirectivesParser(
				locator, false);
		List<Directive> directives = parser.parseDirectives(null, null);

		assertEquals(0, directives.size());
	}
}
