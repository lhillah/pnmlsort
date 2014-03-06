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

public final class PNMLSortFactory {

	
	private static final class PNMLSortFactoryHelper {
		private static volatile PNMLSortFactory INSTANCE;
		static {
			synchronized (PNMLSortFactoryHelper.class) {
				if (INSTANCE == null) {
					synchronized (PNMLSortFactoryHelper.class) {
						INSTANCE = new PNMLSortFactory();
					}
				}
			}
		}

		private PNMLSortFactoryHelper() {
			super();
		}
	}
	
	
	private PNMLSortFactory() {
		super();
	}

	
	public static PNMLSortFactory instance() {
		return PNMLSortFactoryHelper.INSTANCE;
	}


	public PNMLSorter createBasicPNMLSorter() {
		return new BasicPNMLSorter();
	}
}
