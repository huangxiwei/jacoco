/*******************************************************************************
 * Copyright (c) 2009, 2013 Mountainminds GmbH & Co. KG and Contributors
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Evgeny Mandrikov - initial API and implementation
 *    Kyle Lieber - implementation of CheckMojo
 *
 *******************************************************************************/
package org.jacoco.maven;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;
import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.CoverageBuilder;
import org.jacoco.core.analysis.IBundleCoverage;
import org.jacoco.core.analysis.IDirectivesParser;
import org.jacoco.core.data.ExecutionDataStore;
import org.jacoco.report.DirectorySourceFileLocator;
import org.jacoco.report.MultiSourceFileLocator;

/**
 * Creates an IBundleCoverage.
 */
public final class BundleCreator {

	private final MavenProject project;
	private final FileFilter fileFilter;

	/**
	 * Construct a new BundleCreator given the MavenProject and FileFilter.
	 * 
	 * @param project
	 *            the MavenProject
	 * @param fileFilter
	 *            the FileFilter
	 */
	public BundleCreator(final MavenProject project, final FileFilter fileFilter) {
		this.project = project;
		this.fileFilter = fileFilter;
	}

	/**
	 * Create an IBundleCoverage for the given ExecutionDataStore.
	 * 
	 * @param executionDataStore
	 *            the execution data.
	 * @param encoding
	 *            encoding for source files
	 * @param useSourceDirective
	 * @return the coverage data.
	 * @throws IOException
	 */
	public IBundleCoverage createBundle(
			final ExecutionDataStore executionDataStore, final String encoding,
			final boolean useSourceDirective) throws IOException {
		final CoverageBuilder builder = new CoverageBuilder();
		final Analyzer analyzer;
		final File classesDir = new File(this.project.getBuild()
				.getOutputDirectory());

		if (!useSourceDirective) {
			analyzer = new Analyzer(executionDataStore, builder);
		} else {

			final MultiSourceFileLocator locator = new MultiSourceFileLocator(4);
			for (final Object srcDirectory : project.getCompileSourceRoots()) {
				final File srcDirFile = new File((String) srcDirectory);
				locator.add(new DirectorySourceFileLocator(srcDirFile,
						encoding, 4));
			}
			final IDirectivesParser parser = new IDirectivesParser.SourceFileDirectivesParser(
					locator, false);
			analyzer = new Analyzer(executionDataStore, builder, parser);
		}

		@SuppressWarnings("unchecked")
		final List<File> filesToAnalyze = FileUtils.getFiles(classesDir,
				fileFilter.getIncludes(), fileFilter.getExcludes());

		for (final File file : filesToAnalyze) {
			analyzer.analyzeAll(file);
		}

		return builder.getBundle(this.project.getName());
	}
}
