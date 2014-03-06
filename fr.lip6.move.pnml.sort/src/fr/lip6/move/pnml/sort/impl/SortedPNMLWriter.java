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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.lip6.move.pnml.sort.MainPNMLSort;
import fr.lip6.move.pnml.sort.utils.PNMLSortUtils;

/**
 * Thread task to write contents read from a queue int a channel.
 * @author lom
 *
 */
public final class SortedPNMLWriter implements Runnable {

	private static final String STOP = "STOP";
	private static final String CANCEL = "CANCEL";
	private OutChannelBean ocb;
	private BlockingQueue<String> queue;
	private Logger log;

	public SortedPNMLWriter(OutChannelBean ocb, BlockingQueue<String> queue) {
		this.ocb = ocb;
		this.queue = queue;
	}

	@Override
	public void run() {
		log = LoggerFactory.getLogger(SortedPNMLWriter.class
				.getCanonicalName() + "#" + Thread.currentThread().getId());
		ByteBuffer bytebuf = ByteBuffer
				.allocateDirect(PNMLSortUtils.BUFFERSIZE);
		List<byte[]> contents = new ArrayList<byte[]>();
		String msg;
		try {
			msg = queue.take();
			while (!STOP.equalsIgnoreCase(msg) && !CANCEL.equalsIgnoreCase(msg)) {
				PNMLSortUtils.writeToChannel(ocb, msg, bytebuf, contents);
				contents.clear();
				msg = queue.take();
			}
		} catch (InterruptedException | IOException e) {
			log.error(e.getMessage());
			MainPNMLSort.printStackTrace(e);
		} 
	}

}
