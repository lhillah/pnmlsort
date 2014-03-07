/**
 *  Copyright 2014 Universite Paris Ouest and Sorbonne Universites, Univ. Paris 06 - CNRS UMR 7606 (LIP6)
 *
 *  All rights reserved.   This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  Project leader / Initial Contributor:
 *    Lom Messan Hillah - <lom-messan.hillah@lip6.fr>
 *
 *  Contributors:
 *    ${ocontributors} - <$oemails}>
 *
 *  Mailing list:
 *    lom-messan.hillah@lip6.fr
 */
package fr.lip6.move.pnml.sort;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.LoggerContext;
import fr.lip6.move.pnml.sort.exceptions.PNMLSortException;
import fr.lip6.move.pnml.sort.impl.PNMLSortFactory;
import fr.lip6.move.pnml.sort.impl.PNMLSorter;

/**
 * Hello world!
 * 
 */
public class MainPNMLSort {
	public static final String NL = "\n";
	public static final String EQ = "=";
	public static final String COLWS = ": ";
	public static final String WSDASH = " -";
	public static final String VERSION = "0.0.1";
	public static final String SORT_EXT = ".sorted";
	public static final String PNML_EXT = ".pnml";
	public static final String PNMLSORT_DEBUG = "PNMLSORT_DEBUG";

	public static final String SORT_ON_ID = "sort.on.id";
	public static final String EXCLUDE_ARCS = "exclude.arcs";
	public static final String EXCLUDE_PLACES = "exclude.places";
	public static final String EXCLUDE_TRANS = "exclude.trans";
	public static final String OUTPUT_MKG = "output.markings";
	public static final String OUTPUT_INSC = "output.inscriptions";

	private static List<String> pathDest;
	private static List<String> pathSrc;
	private static PNMLFilenameFilter pff;
	private static DirFileFilter dff;
	private static boolean isDebug, isSortOnId, isExcludePlaces,
			isExcludeTrans, isExcludeArcs, isOutputMarkings, isOutputInscriptions;
	private static boolean isOption;
	private static org.slf4j.Logger myLog = LoggerFactory
			.getLogger(MainPNMLSort.class.getCanonicalName());

	public static void main(String[] args) {
		long startTime = System.nanoTime();

		StringBuilder msg = new StringBuilder();
		if (args.length < 1) {
			myLog.error("At least the path to one PNML file is expected.");
			return;
		}
		checkDebugMode(msg);
		checkPropertyMode(msg, SORT_ON_ID, false);
		checkPropertyMode(msg, EXCLUDE_PLACES, false);
		checkPropertyMode(msg, EXCLUDE_TRANS, false);
		checkPropertyMode(msg, EXCLUDE_ARCS, false);
		checkPropertyMode(msg, OUTPUT_MKG, true);
		checkPropertyMode(msg, OUTPUT_INSC, true);
		
		try {
			extractSrcDestPaths(args);
		} catch (IOException e1) {
			myLog.error("Could not successfully extract all source files paths. See log.");
			myLog.error(e1.getMessage());
			if (MainPNMLSort.isDebug) {
				e1.printStackTrace();
			}
		}
		PNMLSorter ps = PNMLSortFactory.instance().createBasicPNMLSorter();
		// TODO : optimize with threads
		boolean error = false;
		for (int i = 0; i < pathSrc.size(); i++) {
			try {
				ps.sortPNML(new File(pathSrc.get(i)), new File(pathDest.get(i)));

			} catch (PNMLSortException | IOException e) {
				myLog.error(e.getMessage());
				MainPNMLSort.printStackTrace(e);
				error |= true;
			}
		}

		if (!error) {
			msg.append("Finished successfully.");
			myLog.info(msg.toString());
		} else {
			msg.append("Finished in error.");
			if (!MainPNMLSort.isDebug) {
				msg.append(
						" Activate debug mode to print stacktraces, like so: export ")
						.append(PNMLSORT_DEBUG).append("=true");
			}
			myLog.error(msg.toString());
		}
		long endTime = System.nanoTime();
		myLog.info("Sorting PNML took {} seconds.",
				(endTime - startTime) / 1.0e9);
		LoggerContext loggerContext = (LoggerContext) LoggerFactory
				.getILoggerFactory();
		loggerContext.stop();
		if (error) {
			System.exit(-1);
		}

	}
	
	/**
	 * Checks debug mode. 
	 * @param msg
	 */
	private static void checkDebugMode(StringBuilder msg) {
		String debug = System.getenv(PNMLSORT_DEBUG);
		if ("true".equalsIgnoreCase(debug)) {
			setDebug(true);
		} else {
			setDebug(false);
			msg.append(
					"Debug mode not set. If you want to activate the debug mode (print stackstaces in case of errors), then set the ")
					.append(PNMLSORT_DEBUG)
					.append(" environnement variable like so: export ")
					.append(PNMLSORT_DEBUG).append("=true.");
			myLog.warn(msg.toString());
			msg.delete(0, msg.length());
		}
	}


	/**
	 * 
	 * @param msg
	 * @param propertyName
	 * @param propDefault
	 */
	private static void checkPropertyMode(StringBuilder msg, String propertyName, boolean propDefault) {
		String prop = System.getProperty(propertyName);
		if (prop != null && Boolean.valueOf(prop)) {
			setProperty(propertyName, true);
			isOption = true;
			myLog.warn("Option {} enabled.", propertyName);
		} else {
			setProperty(propertyName, propDefault);
			msg.append("Property ")
					.append(propertyName)
					.append(" is not set. Default is ").append(propDefault).append(". If you want to set it, then invoke this program with ")
					.append(propertyName).append(" property like so: java -D")
					.append(propertyName)
					.append("=").append(!propDefault).append(" [JVM OPTIONS] -jar ...");
			myLog.warn(msg.toString());
			msg.delete(0, msg.length());
		}
	}

	private static void setProperty(String propertyName, boolean value) {
		if (SORT_ON_ID.equalsIgnoreCase(propertyName)) {
			isSortOnId = value;
		} else if (EXCLUDE_PLACES.equalsIgnoreCase(propertyName)) {
			isExcludePlaces = value;
		} else if (EXCLUDE_TRANS.equalsIgnoreCase(propertyName)) {
			isExcludeTrans = value;
		} else if (EXCLUDE_ARCS.equalsIgnoreCase(propertyName)) {
			isExcludeArcs = value;
		} else if (OUTPUT_MKG.equalsIgnoreCase(propertyName)) {
			isOutputMarkings = value;
		} else if (OUTPUT_INSC.equalsIgnoreCase(propertyName)) {
			isOutputInscriptions = value;
		}
	}

	/**
	 * Extracts PNML files (scans directories recursively) from command-line
	 * arguments.
	 * 
	 * @param args
	 * @throws IOException
	 */
	private static void extractSrcDestPaths(String[] args) throws IOException {
		pathDest = new ArrayList<String>();
		pathSrc = new ArrayList<String>();
		File srcf;
		File[] srcFiles;
		pff = new PNMLFilenameFilter();
		dff = new DirFileFilter();
		for (String s : args) {
			srcf = new File(s);
			if (srcf.isFile()) {
				pathSrc.add(s);
				pathDest.add(s.replaceAll(PNML_EXT, SORT_EXT));
			} else if (srcf.isDirectory()) {
				srcFiles = extractSrcFiles(srcf, pff, dff);
				for (File f : srcFiles) {
					pathSrc.add(f.getCanonicalPath());
					pathDest.add(f.getCanonicalPath().replaceAll(PNML_EXT,
							SORT_EXT));
				}
			}
		}
	}

	private static File[] extractSrcFiles(File srcf, PNMLFilenameFilter pff,
			DirFileFilter dff) {
		List<File> res = new ArrayList<File>();

		// filter PNML files
		File[] pfiles = srcf.listFiles(pff);
		res.addAll(Arrays.asList(pfiles));

		// filter directories
		pfiles = srcf.listFiles(dff);
		for (File f : pfiles) {
			res.addAll(Arrays.asList(extractSrcFiles(f, pff, dff)));
		}

		return res.toArray(new File[0]);
	}

	private static final class PNMLFilenameFilter implements FilenameFilter {
		@Override
		public boolean accept(File dir, String name) {
			return name.endsWith(PNML_EXT);
		}
	}

	private static final class DirFileFilter implements FileFilter {
		@Override
		public boolean accept(File pathname) {
			return pathname.isDirectory();
		}
	}

	/**
	 * Returns true if debug mode is set.
	 * 
	 * @return
	 */
	public static boolean isDebug() {
		return isDebug;
	}
	
	public static synchronized void setDebug(boolean b) {
		MainPNMLSort.isDebug = b;
	}


	/**
	 * Prints the stack trace of the exception passed as parameter.
	 * 
	 * @param e
	 */
	public static synchronized void printStackTrace(Exception e) {
		if (MainPNMLSort.isDebug) {
			e.printStackTrace();
		}
	}

	public static boolean isSortOnId() {
		return isSortOnId;
	}

	public static void setSortOnId(boolean isSortOnId) {
		MainPNMLSort.isSortOnId = isSortOnId;
	}

	public static boolean isExcludeArcs() {
		return isExcludeArcs;
	}

	public static void setExcludeArcs(boolean isExcludeArcs) {
		MainPNMLSort.isExcludeArcs = isExcludeArcs;
	}

	public static boolean isExcludePlaces() {
		return isExcludePlaces;
	}

	public static void setExcludePlaces(boolean isExcludePlaces) {
		MainPNMLSort.isExcludePlaces = isExcludePlaces;
	}

	public static boolean isExcludeTrans() {
		return isExcludeTrans;
	}

	public static void setExcludeTrans(boolean isExcludeTrans) {
		MainPNMLSort.isExcludeTrans = isExcludeTrans;
	}

	public static boolean isOption() {
		return isOption;
	}

	public static void setOption(boolean isOption) {
		MainPNMLSort.isOption = isOption;
	}

	public static boolean isOutputMarkings() {
		return isOutputMarkings;
	}

	public static void setOutputMarkings(boolean isOutputMarkings) {
		MainPNMLSort.isOutputMarkings = isOutputMarkings;
	}

	public static boolean isOutputInscriptions() {
		return isOutputInscriptions;
	}

	public static void setOutputInscriptions(boolean isOutputInscriptions) {
		MainPNMLSort.isOutputInscriptions = isOutputInscriptions;
	}
}
