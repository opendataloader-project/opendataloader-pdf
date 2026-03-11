/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.processors;

import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;

import java.util.Objects;

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
        boolean isIgnoreCharactersWithoutUnicode = Objects.requireNonNull(
            StaticContainers.getIsIgnoreCharactersWithoutUnicode(),
            "StaticContainers.isIgnoreCharactersWithoutUnicode is not initialized"
        );
        boolean isDataLoader = Objects.requireNonNull(
            StaticContainers.getIsDataLoader(),
            "StaticContainers.isDataLoader is not initialized"
        );
        boolean keepLineBreaks = Objects.requireNonNull(
            StaticContainers.isKeepLineBreaks(),
            "StaticContainers.keepLineBreaks is not initialized"
        );

        return new Snapshot(
            isIgnoreCharactersWithoutUnicode,
            isDataLoader,
            keepLineBreaks
        );
    }

    static void apply(Snapshot snapshot) {
        StaticContainers.setIsIgnoreCharactersWithoutUnicode(snapshot.isIgnoreCharactersWithoutUnicode);
        StaticContainers.setIsDataLoader(snapshot.isDataLoader);
        StaticContainers.setKeepLineBreaks(snapshot.keepLineBreaks);
    }

    /**
     * Clears worker-thread ThreadLocal values to avoid leaking state across reused pool threads.
     */
    static void clear() {
        StaticContainers.setDocument(null);
        StaticContainers.setTableBordersCollection(null);
        StaticContainers.setKeepLineBreaks(false);
        StaticContainers.setIsDataLoader(false);
        StaticContainers.setIsIgnoreCharactersWithoutUnicode(false);
    }

    static final class Snapshot {
        private final boolean isIgnoreCharactersWithoutUnicode;
        private final boolean isDataLoader;
        private final boolean keepLineBreaks;

        private Snapshot(
                boolean isIgnoreCharactersWithoutUnicode,
                boolean isDataLoader,
                boolean keepLineBreaks) {
            this.isIgnoreCharactersWithoutUnicode = isIgnoreCharactersWithoutUnicode;
            this.isDataLoader = isDataLoader;
            this.keepLineBreaks = keepLineBreaks;
        }
    }
}
