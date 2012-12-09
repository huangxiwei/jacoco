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
package org.jacoco.core.analysis;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.jacoco.core.data.ISourceFileLocator;

/**
 * Parser of coverage directives
 */
public interface IDirectivesParser {

	/**
	 * Data class representing a directive
	 */
	public static class Directive {
		/**
		 * @param lineNum
		 * @param coverageOn
		 */
		public Directive(final int lineNum, final boolean coverageOn) {
			this.lineNum = lineNum;
			this.coverageOn = coverageOn;
		}

		/**
		 * Line number of the directive
		 */
		public final int lineNum;
		/**
		 * Whether to switch coverage on/off
		 */
		public final boolean coverageOn;

		@Override
		public String toString() {
			return lineNum + ":" + coverageOn;
		}
	}

	/**
	 * Parser for directives in source code
	 */
	public static class SourceFileDirectivesParser implements IDirectivesParser {
		private static final Set<String> ON_DIRECTIVES;
		private static final Set<String> OFF_DIRECTIVES;
		static {
			final Set<String> onDirectives = new HashSet<String>();
			onDirectives.add("///CLOVER:ON");
			onDirectives.add("///COVERAGE:ON");
			ON_DIRECTIVES = Collections.unmodifiableSet(onDirectives);

			final Set<String> offDirectives = new HashSet<String>();
			offDirectives.add("///CLOVER:OFF");
			offDirectives.add("///COVERAGE:OFF");
			OFF_DIRECTIVES = Collections.unmodifiableSet(offDirectives);
		}

		private final ISourceFileLocator sourceLocator;
		private final boolean requireComment;

		/**
		 * @param sourceLocator
		 *            Object for locating source code
		 * @param requireComment
		 *            If true, only off directives with a comment will be
		 *            recognised
		 */
		public SourceFileDirectivesParser(
				final ISourceFileLocator sourceLocator,
				final boolean requireComment) {
			this.sourceLocator = sourceLocator;
			this.requireComment = requireComment;
		}

		public List<Directive> parseDirectives(final String packageName,
				final String sourceFilename) {
			final List<Directive> directives = new LinkedList<Directive>();

			try {
				final Reader sourceReader = sourceLocator.getSourceFile(
						packageName, sourceFilename);

				if (sourceReader != null) {
					final BufferedReader bufSourceReader = new BufferedReader(
							sourceReader);
					try {
						int lineNum = 1;
						String line;
						while ((line = bufSourceReader.readLine()) != null) {
							final String trimmedLine = line.trim();

							boolean foundDirective = false;
							for (final String offDirective : OFF_DIRECTIVES) {
								if (trimmedLine.startsWith(offDirective)) {
									if (!requireComment
											|| (trimmedLine.length() > offDirective
													.length())) {
										// Either no comment is required or
										// there is a comment
										directives.add(new Directive(lineNum,
												false));
										foundDirective = true;
										break;
									}
								}
							}

							if (!foundDirective) {
								for (final String onDirective : ON_DIRECTIVES) {
									if (trimmedLine.startsWith(onDirective)) {
										directives.add(new Directive(lineNum,
												true));
									}
								}
							}

							lineNum++;
						}
					} finally {
						bufSourceReader.close();
					}
				}
			} catch (final IOException e) {
				throw new RuntimeException(e);
			}

			return directives;
		}

	}

	/**
	 * Return coverage directives associated with the specified className
	 * 
	 * @param packageName
	 * @param sourceFilename
	 * @return Queue of directives in the order which they apply.
	 */
	public List<Directive> parseDirectives(String packageName,
			String sourceFilename);
}