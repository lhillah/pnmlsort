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
package fr.lip6.move.pnml.sort.impl;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import javax.xml.bind.ValidationException;

import org.slf4j.LoggerFactory;

import com.ximpleware.extended.AutoPilotHuge;
import com.ximpleware.extended.NavExceptionHuge;
import com.ximpleware.extended.ParseExceptionHuge;
import com.ximpleware.extended.VTDGenHuge;
import com.ximpleware.extended.VTDNavHuge;
import com.ximpleware.extended.XMLMemMappedBuffer;
import com.ximpleware.extended.XPathEvalExceptionHuge;
import com.ximpleware.extended.XPathParseExceptionHuge;

import fr.lip6.move.pnml.sort.MainPNMLSort;
import fr.lip6.move.pnml.sort.exceptions.InternalException;
import fr.lip6.move.pnml.sort.exceptions.InvalidPNMLTypeException;
import fr.lip6.move.pnml.sort.exceptions.PNMLSortException;
import fr.lip6.move.pnml.sort.utils.PNMLSortUtils;

public final class BasicPNMLSorter implements PNMLSorter {
	private static final String CANCEL = "CANCEL";
	private static final String NL = "\n";
	private static final String TAB = "\t";
	private static final String STOP = "STOP";
	private static final String NET = "NET";
	private static final String PAGE = "PAGE";
	private static final String PLACES = "PLACES";
	private static final String TRANSITIONS = "TRANSITIONS";
	private static final String ARCS = "ARCS";
	private static final String WS = " ";

	private File currentInputFile;
	private org.slf4j.Logger journal;
	private File outPSFile;
	private OutChannelBean ocbPS;
	private BlockingQueue<String> psQueue;

	/**
	 * Net names.
	 */
	private List<String> netsList;
	/**
	 * key: net name value: list of page ids
	 */
	private Map<String, List<String>> netPages;
	/**
	 * key: page id value: list of place names
	 */
	private Map<String, List<String>> pagePlaces;
	/**
	 * key: page id value: list of transition names
	 */
	private Map<String, List<String>> pageTrans;
	/**
	 * key: page id value: list of arc ids
	 */
	private Map<String, List<String>> pageArcs;
	/**
	 * key: page id value: list of page ids
	 */
	private Map<String, List<String>> pageSubPages;
	/**
	 * key: arc id value: array of source and target id
	 */
	private Map<String, String[]> arcSrcTrg;

	// To look for arc sources and target
	/**
	 * key: place id value: place name
	 */
	private Map<String, String> plIdNames;
	/**
	 * key: transition id value transition name
	 */
	private Map<String, String> trIdNames;
	private StringBuilder tabulation, netOutput;

	public void sortPNML(File inFile, File outFile) throws PNMLSortException,
			IOException {
		journal = LoggerFactory.getLogger(BasicPNMLSorter.class
				.getCanonicalName());
		try {
			this.currentInputFile = inFile;
			journal.info("Checking preconditions on input file format: {} ",
					inFile.getCanonicalPath());
			PNMLSortUtils.checkIsPnmlFile(inFile);
			journal.info("Exporting into Sorted PNML: {}",
					inFile.getCanonicalPath());

			sortPNMLDocument(inFile, outFile);

		} catch (ValidationException
				| fr.lip6.move.pnml.sort.exceptions.InvalidFileTypeException
				| fr.lip6.move.pnml.sort.exceptions.InvalidFileException
				| InternalException | XPathEvalExceptionHuge e) {
			// journal.error(e.getMessage());
			// MainPNML2BPN.printStackTrace(e);
			throw new PNMLSortException(e);
		} catch (IOException | InterruptedException e) {
			try {
				throw e;
			} catch (Exception e1) {
				e1.printStackTrace();
				throw new PNMLSortException(e.getCause());
			}
		}
	}

	private void sortPNMLDocument(File inFile, File outFile)
			throws InterruptedException, IOException, PNMLSortException,
			XPathEvalExceptionHuge {
		XMLMemMappedBuffer xb = new XMLMemMappedBuffer();
		VTDGenHuge vg = new VTDGenHuge();

		try {
			xb.readFile(inFile.getCanonicalPath());
			vg.setDoc(xb);
			vg.parse(true);
			VTDNavHuge vn = vg.getNav();
			AutoPilotHuge ap = new AutoPilotHuge(vn);

			outPSFile = new File(PNMLSortUtils.extractBaseName(outFile
					.getCanonicalPath()) + MainPNMLSort.SORT_EXT);
			// Channels for sorted objects
			ocbPS = PNMLSortUtils.openOutChannel(outFile);
			// Queues for sorted PNML objects
			psQueue = initQueue();

			// Start writer
			Thread psWriter = startWriter(ocbPS, psQueue);
			// Init data types
			initDataTypes();
			journal.info(
					"Exporting sorted Petri net(s)' objects from PNML document {}.",
					inFile.getCanonicalPath());
			sortPNMLDocument(ap, vn, psQueue);
			// Stop Writers
			stopWriter(psQueue);
			psWriter.join();
			// Close channels
			closeChannel(ocbPS);
			// clear maps
			clearAllCollections();
			journal.info("See file: {}", outPSFile.getCanonicalPath());

		} catch (NavExceptionHuge | XPathParseExceptionHuge
				| ParseExceptionHuge | InternalException
				| InvalidPNMLTypeException e) {
			emergencyStop(outFile);
			throw new PNMLSortException(e);
		} catch (InterruptedException e) {
			emergencyStop(outFile);
			throw e;
		} catch (IOException e) {
			emergencyStop(outFile);
			throw e;
		}
	}

	private void sortPNMLDocument(AutoPilotHuge ap, VTDNavHuge vn,
			BlockingQueue<String> psQueue2) throws InvalidPNMLTypeException,
			InternalException, NavExceptionHuge, XPathParseExceptionHuge,
			XPathEvalExceptionHuge, InterruptedException {
		vn.toElement(VTDNavHuge.ROOT);
		ap.selectXPath(PNMLPaths.NETS_NAME);
		String name, id;
		List<String> elem;
		while ((ap.evalXPath()) != -1) {
			vn.push();
			name = vn.toString(vn.getText()).trim();
			netsList.add(name);
			vn.toElement(VTDNavHuge.PARENT);
			vn.toElement(VTDNavHuge.PARENT);
			vn.toElement(VTDNavHuge.FIRST_CHILD);
			while (!vn.matchElement("page")) {
				vn.toElement(VTDNavHuge.NEXT_SIBLING);
			}
			id = vn.toString(vn.getAttrVal(PNMLPaths.ID_ATTR));
			if (id != null) {
				elem = netPages.get(name);
				if (elem == null) {
					elem = new ArrayList<>();
					netPages.put(name, elem);
				}
				elem.add(id);
			} else {
				throw new InvalidPNMLTypeException(
						"This net has no inner page. It is not standard-compliant.");
			}
			if (vn.toElement(VTDNavHuge.FIRST_CHILD)) {
				determineNode(id, vn);
			}
			while (vn.toElement(VTDNavHuge.NEXT_SIBLING)) {
				determineNode(id, vn);
			}
			vn.pop();
		}
		ap.resetXPath();
		vn.toElement(VTDNavHuge.ROOT);
		sortNOutputNets();
	}

	private void sortNOutputNets() throws InterruptedException {
		Collections.sort(netsList);
		journal.info("Exporting sorted nets.");
		for (String net : netsList) {
			journal.info("Exporting net {}.", net);
			netOutput.append(NET).append(WS).append(net).append(NL);
			psQueue.put(netOutput.toString());
			netOutput.delete(0, netOutput.length());
			sortNOutputPages(net);
		}
	}

	private void sortNOutputPages(String net) throws InterruptedException {
		List<String> pgs = netPages.get(net);
		if (pgs != null) {
			journal.info("Exporting sorted pages from net {}.", net);
			Collections.sort(pgs);
			incrementTab();
			for (String page : pgs) {
				netOutput.append(tabulation).append(PAGE).append(WS)
						.append(page).append(NL);
				psQueue.put(netOutput.toString());
				netOutput.delete(0, netOutput.length());
				sortNOutputPlaces(page);
				sortNOutputTransitions(page);
				sortNOutputArcs(page);
				sortNOutputSubPages(page);
			}
			decrementTab();
		} else {
			journal.info("No sub-pages to export from net {}.", net);
		}
	}

	private void sortNOutputSubPages(String page) throws InterruptedException {
		List<String> pgs = pageSubPages.get(page);
		if (pgs != null) {
			journal.info("Exporting sorted sub-pages from page {}.", page);
			Collections.sort(pgs);
			incrementTab();
			for (String pg : pgs) {
				netOutput.append(tabulation).append(PAGE).append(WS).append(pg)
						.append(NL);
				psQueue.put(netOutput.toString());
				netOutput.delete(0, netOutput.length());
				sortNOutputPlaces(pg);
				sortNOutputTransitions(pg);
				sortNOutputArcs(pg);
				sortNOutputSubPages(pg);
			}
			decrementTab();
		} else {
			journal.info("No sub-pages to export from page {}.", page);
		}
	}

	private void sortNOutputArcs(String page) throws InterruptedException {
		List<String> arcs = pageArcs.get(page);
		if (arcs != null) {
			journal.info("Exporting sorted arcs from page {}", page);
			Collections.sort(arcs);
			incrementTab();
			netOutput.append(tabulation).append(ARCS).append(NL);
			incrementTab();
			String[] st;
			// TODO : arc valuation with #val if it is a PT net. See property
			for (String id : arcs) {
				st = arcSrcTrg.get(id);
				netOutput.append(tabulation).append(findSrcOrTrgNode(st[0]))
						.append(WS).append(id).append(WS)
						.append(findSrcOrTrgNode(st[1])).append(NL);
			}
			psQueue.put(netOutput.toString());
			netOutput.delete(0, netOutput.length());
			decrementTab();
			decrementTab();
		} else {
			journal.info("No arcs to export from page {}.", page);
		}
	}

	private String findSrcOrTrgNode(String id) {
		String node = plIdNames.get(id);
		if (node == null) {
			node = trIdNames.get(id);
		}
		// FIXME: null should never happen...
		return node;
	}

	private void sortNOutputTransitions(String page)
			throws InterruptedException {
		List<String> transitions = pageTrans.get(page);
		if (transitions != null) {
			journal.info("Exporting sorted transitions from page {}", page);
			Collections.sort(transitions);
			incrementTab();
			netOutput.append(tabulation).append(TRANSITIONS).append(NL);
			incrementTab();
			for (String tr : transitions) {
				netOutput.append(tabulation).append(tr).append(NL);
			}
			psQueue.put(netOutput.toString());
			netOutput.delete(0, netOutput.length());
			decrementTab();
			decrementTab();
		} else {
			journal.info("No transitions to export from page {}.", page);
		}
	}

	private void sortNOutputPlaces(String page) throws InterruptedException {
		List<String> places = pagePlaces.get(page);
		if (places != null) {
			journal.info("Exporting sorted places from page {}", page);
			Collections.sort(places);
			incrementTab();
			netOutput.append(tabulation).append(PLACES).append(NL);
			incrementTab();
			for (String pl : places) {
				netOutput.append(tabulation).append(pl).append(NL);
			}
			psQueue.put(netOutput.toString());
			netOutput.delete(0, netOutput.length());
			decrementTab();
			decrementTab();
		} else {
			journal.info("No places to export from page {}.", page);
		}
	}

	private void determineNode(String pageId, VTDNavHuge vn)
			throws InternalException, InvalidPNMLTypeException,
			NavExceptionHuge {
		if (vn.matchElement("place")) {
			parseNode(pageId, vn, NodeType.PLACE);
		} else if (vn.matchElement("transition")) {
			parseNode(pageId, vn, NodeType.TRANSITION);
		} else if (vn.matchElement("arc")) {
			parseArc(pageId, vn);
		} else if (vn.matchElement("page")) {
			parsePage(pageId, vn);
		} else if (vn.matchElement("referenceplace")) {
			throw new InternalException(
					"Does not yet support reference places.");
		} else if (vn.matchElement("referencetransition")) {
			throw new InternalException(
					"Does not yet support reference transitions.");
		} else if (vn.matchElement("name")) {
			// do nothing page name does not occur often, so we cannot rely on
			// it.
			journal.info("Discovered page name. Not processed since I cannot rely on it (i.e it is not mandatory).");
		} else {
			// TODO : find right API to print tag name
			throw new InvalidPNMLTypeException(
					"Unknown (or unsupported) PNML node type at this level: "
							+ vn.toString());
		}
	}

	private void parsePage(String pageId, VTDNavHuge vn)
			throws NavExceptionHuge, InternalException,
			InvalidPNMLTypeException {
		String id = vn.toString(vn.getAttrVal(PNMLPaths.ID_ATTR));

		List<String> elem = pageSubPages.get(id);
		if (elem == null) {
			elem = new ArrayList<>();
			pageSubPages.put(id, elem);
		}
		elem.add(id);

		if (vn.toElement(VTDNavHuge.FIRST_CHILD)) {
			determineNode(id, vn);
		}
		while (vn.toElement(VTDNavHuge.NEXT_SIBLING)) {
			determineNode(id, vn);
		}
		vn.toElement(VTDNavHuge.PARENT);
	}

	private void parseArc(String pageId, VTDNavHuge vn) throws NavExceptionHuge {
		// TODO: parse inscription if it is a PT net. See property
		String id, src, trg;
		List<String> elem;
		id = vn.toString(vn.getAttrVal(PNMLPaths.ID_ATTR));
		src = vn.toString(vn.getAttrVal(PNMLPaths.SRC_ATTR));
		trg = vn.toString(vn.getAttrVal(PNMLPaths.TRG_ATTR));
		elem = pageArcs.get(pageId);
		if (elem == null) {
			elem = new ArrayList<>();
			pageArcs.put(pageId, elem);
		}
		elem.add(id);
		String[] st = new String[] { src, trg };
		arcSrcTrg.put(id, st);
	}

	private void parseNode(String pageId, VTDNavHuge vn, NodeType nt)
			throws InternalException, NavExceptionHuge {
		String id, name;
		List<String> elem;
		id = vn.toString(vn.getAttrVal(PNMLPaths.ID_ATTR));
		vn.toElement(VTDNavHuge.FIRST_CHILD);
		while (!vn.matchElement("name")) {
			vn.toElement(VTDNavHuge.NEXT_SIBLING);
		}
		vn.toElement(VTDNavHuge.FIRST_CHILD);
		while (!vn.matchElement("text")) {
			vn.toElement(VTDNavHuge.NEXT_SIBLING);
		}
		name = vn.toString(vn.getText()).trim();
		// TODO : find initial marking when it is a PT net. See property.
		switch (nt) {
		case PLACE:
			elem = pagePlaces.get(pageId);
			if (elem == null) {
				elem = new ArrayList<>();
				pagePlaces.put(pageId, elem);
			}
			elem.add(name);
			plIdNames.put(id, name);
			break;
		case TRANSITION:
			elem = pageTrans.get(pageId);
			if (elem == null) {
				elem = new ArrayList<>();
				pageTrans.put(pageId, elem);
			}
			elem.add(name);
			trIdNames.put(id, name);
			break;
		default:
			// Not supported
			throw new InternalException("This node type is not supported: "
					+ nt.name());
		}
		vn.toElement(VTDNavHuge.PARENT);
		vn.toElement(VTDNavHuge.PARENT);
	}

	private void initDataTypes() {
		netsList = new ArrayList<>();
		netPages = new HashMap<>();
		pagePlaces = new HashMap<>();
		pageTrans = new HashMap<>();
		pageArcs = new HashMap<>();
		pageSubPages = new HashMap<>();
		plIdNames = new HashMap<>();
		trIdNames = new HashMap<>();
		arcSrcTrg = new HashMap<>();
		tabulation = new StringBuilder();
		netOutput = new StringBuilder();
	}

	private void clearAllCollections() {
		netsList.clear();
		netPages.clear();
		pagePlaces.clear();
		pageTrans.clear();
		pageArcs.clear();
		pageSubPages.clear();
		plIdNames.clear();
		trIdNames.clear();
		arcSrcTrg.clear();
		tabulation.delete(0, tabulation.length());
		netOutput.delete(0, netOutput.length());
	}

	/**
	 * 
	 * @return
	 */
	private BlockingQueue<String> initQueue() {
		BlockingQueue<String> queue = new LinkedBlockingQueue<String>();
		return queue;
	}

	/**
	 * 
	 * @param ocb
	 * @param queue
	 * @return
	 */
	private Thread startWriter(OutChannelBean ocb, BlockingQueue<String> queue) {
		Thread t = new Thread(new SortedPNMLWriter(ocb, queue));
		t.start();
		return t;
	}

	/**
	 * Normal stop of a writer.
	 * 
	 * @param queue
	 * @throws InterruptedException
	 */
	private void stopWriter(BlockingQueue<String> queue)
			throws InterruptedException {
		queue.put(STOP);
	}

	/**
	 * Closes an output channel.
	 * 
	 * @param cb
	 * @throws IOException
	 */
	private void closeChannel(OutChannelBean cb) throws IOException {
		PNMLSortUtils.closeOutChannel(cb);
	}

	private void cancelWriter(BlockingQueue<String> queue)
			throws InterruptedException {
		if (queue != null) {
			queue.put(CANCEL);
		}
	}

	/**
	 * Deletes an output file.
	 * 
	 * @param oFile
	 */
	private void deleteOutputFile(File oFile) {
		if (oFile != null && oFile.exists()) {
			oFile.delete();
		}
	}

	private void emergencyStop(File outFile) throws InterruptedException,
			IOException {
		cancelWriter(psQueue);
		closeChannel(ocbPS);
		deleteOutputFile(outPSFile);
		journal.error("Emergency stop. Cancelled the translation and released opened resources.");
	}

	private void incrementTab() {
		tabulation.append(TAB);
		// return tabulation.toString();
	}

	private void decrementTab() {
		int i = tabulation.lastIndexOf(TAB);
		tabulation.delete(i, tabulation.length());
		// return tabulation.toString();
	}
}
