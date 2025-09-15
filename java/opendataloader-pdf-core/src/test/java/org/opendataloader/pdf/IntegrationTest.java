/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.opendataloader.pdf.processors.DocumentProcessor;
import org.opendataloader.pdf.api.Config;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class IntegrationTest {

    static Stream<Arguments> integrationTestParams() {
        return Stream.of(
                Arguments.of("1901.03003.pdf")
        );
    }

    @ParameterizedTest(name = "{index}: ({0}) => {0}")
    @MethodSource("integrationTestParams")
    public void test(String fileName) throws IOException {
        Path pdfPath = Paths.get("../../resources", fileName);
        Path jsonPath = Paths.get("../../resources", fileName.replace(".pdf", ".json"));
        File pdfFile = pdfPath.toFile();
        File jsonFile = jsonPath.toFile();

        Config config = new Config();
        config.setGenerateMarkdown(true);
        config.setGenerateHtml(true);
        config.setGeneratePDF(true);
        config.setOutputFolder("../../resources/temp");
        DocumentProcessor.processFile(pdfFile.getAbsolutePath(), config);

        Path resultPath = Paths.get("../../resources/temp", fileName.replace(".pdf", ".json"));
        File resultJson = resultPath.toFile();

        ObjectMapper mapper = new ObjectMapper();
        JsonNode tree1 = mapper.readTree(new FileInputStream(jsonFile));
        JsonNode tree2 = mapper.readTree(new FileInputStream(resultJson));
        checkJsonNodes(tree1, tree2);
    }

    private static void checkJsonNodes(JsonNode node1, JsonNode node2) {
        Assertions.assertEquals(node1.get("type"), node2.get("type"));
        checkArrayFields(node1, node2, "kids");
        checkArrayFields(node1, node2, "rows");
        checkArrayFields(node1, node2, "cells");
        checkArrayFields(node1, node2, "list items");
    }

    private static void checkArrayFields(JsonNode node1, JsonNode node2, String fieldName) {
        JsonNode child1 = node1.get(fieldName);
        JsonNode child2 = node2.get(fieldName);
        Assertions.assertEquals(child1 != null, child2 != null);
        if (child1 != null && child2 != null) {
            ArrayNode array1 = (ArrayNode) child1;
            ArrayNode array2 = (ArrayNode) child2;
            Assertions.assertEquals(array1.size(), array2.size());
            for (int i = 0; i < array2.size(); i++) {
                checkJsonNodes(array1.get(i), array2.get(i));
            }
        }
    }
}
