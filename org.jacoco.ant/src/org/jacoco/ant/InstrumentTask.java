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
package org.jacoco.ant;

import static java.lang.String.format;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.Resource;
import org.apache.tools.ant.types.resources.Union;
import org.apache.tools.ant.util.FileUtils;
import org.jacoco.core.instr.Instrumenter;
import org.jacoco.core.runtime.IRuntime;
import org.jacoco.core.runtime.LoggerRuntime;

/**
 * Task for performing offline instrumentation of classes.
 */
public class InstrumentTask extends Task {

	private final Union classfiles = new Union();
	private File destDir = null;

	/**
	 * Returns the nested resource collection for class files.
	 * 
	 * @return resource collection for class files
	 */
	public Union createClassfiles() {
		return classfiles;
	}

	/**
	 * Sets the destination directory.
	 * 
	 * @param destDir
	 *            the destination directory
	 */
	public void setTodir(final File destDir) {
		this.destDir = destDir;
	}

	@Override
	public void execute() throws BuildException {
		final IRuntime runtime = new LoggerRuntime(true);
		final Instrumenter instrumenter = new Instrumenter(runtime);

		int instrCount = 0;

		for (final Iterator<?> i = classfiles.iterator(); i.hasNext();) {
			final Resource resource = (Resource) i.next();

			if (resource.getName().endsWith(".class")) {
				InputStream in = null;
				try {
					final File instrumentedClassFile = new File(destDir,
							resource.getName());
					instrumentedClassFile.getParentFile().mkdirs();

					in = resource.getInputStream();

					final byte[] instrumentedClassBytes = instrumenter
							.instrument(in);
					final FileOutputStream fileOut = new FileOutputStream(
							instrumentedClassFile);
					fileOut.write(instrumentedClassBytes);
					fileOut.flush();
					fileOut.close();

					instrCount++;
				} catch (final IOException e) {
					throw new BuildException(format(
							"Unable to read class file %s", resource), e,
							getLocation());
				} finally {
					FileUtils.close(in);
				}
			}
		}

		log(format("Instrumented %s classes", Integer.toString(instrCount)));
	}
}
