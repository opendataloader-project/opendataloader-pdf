/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.processors;

import org.verapdf.wcag.algorithms.entities.IDocument;
import org.verapdf.wcag.algorithms.entities.tables.TableBordersCollection;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;

/**
 * Captures and reapplies verapdf StaticContainers ThreadLocal state.
 *
 * <p>Some verapdf StaticContainers values are stored as ThreadLocal and are initialized on the
 * main processing thread. Any worker thread that runs text/table processors must explicitly set
 * these values before accessing APIs like TextLineProcessor, otherwise null ThreadLocal values may
 * trigger NullPointerException.
 */
final class StaticContainersThreadContext {

    private StaticContainersThreadContext() {
        // utility class
    }

    static Snapshot capture() {
        return new Snapshot(
            StaticContainers.getIsIgnoreCharactersWithoutUnicode(),
            StaticContainers.getIsDataLoader(),
            StaticContainers.isKeepLineBreaks(),
            StaticContainers.getDocument(),
            StaticContainers.getTableBordersCollection()
        );
    }

    static void apply(Snapshot snapshot) {
        if (snapshot.document != null) {
            StaticContainers.setDocument(snapshot.document);
        }
        if (snapshot.tableBordersCollection != null) {
            StaticContainers.setTableBordersCollection(snapshot.tableBordersCollection);
        }
        if (snapshot.keepLineBreaks != null) {
            StaticContainers.setKeepLineBreaks(snapshot.keepLineBreaks);
        }
        if (snapshot.isDataLoader != null) {
            StaticContainers.setIsDataLoader(snapshot.isDataLoader);
        }
        if (snapshot.isIgnoreCharactersWithoutUnicode != null) {
            StaticContainers.setIsIgnoreCharactersWithoutUnicode(snapshot.isIgnoreCharactersWithoutUnicode);
        }
    }

    static final class Snapshot {
        private final Boolean isIgnoreCharactersWithoutUnicode;
        private final Boolean isDataLoader;
        private final Boolean keepLineBreaks;
        private final IDocument document;
        private final TableBordersCollection tableBordersCollection;

        private Snapshot(
                Boolean isIgnoreCharactersWithoutUnicode,
                Boolean isDataLoader,
                Boolean keepLineBreaks,
                IDocument document,
                TableBordersCollection tableBordersCollection) {
            this.isIgnoreCharactersWithoutUnicode = isIgnoreCharactersWithoutUnicode;
            this.isDataLoader = isDataLoader;
            this.keepLineBreaks = keepLineBreaks;
            this.document = document;
            this.tableBordersCollection = tableBordersCollection;
        }
    }
}
