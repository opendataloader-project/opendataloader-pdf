/*
 * Copyright 2025-2026 Hancom Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opendataloader.pdf.json.serializers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.entities.SemanticFormula;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;

import static org.junit.jupiter.api.Assertions.*;

class FormulaSerializerTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addSerializer(SemanticFormula.class, new FormulaSerializer(SemanticFormula.class));
        mapper.registerModule(module);
    }

    @Test
    void testEquationNumberEmittedWhenResolved() throws Exception {
        BoundingBox bbox = new BoundingBox(0, 0, 0, 100, 20);
        SemanticFormula formula = new SemanticFormula(bbox, "E=mc^2");
        formula.setNumber("3.1");

        String json = mapper.writeValueAsString(formula);
        JsonNode root = mapper.readTree(json);

        assertTrue(root.has("equation number"),
                "equation number field should be present when resolved");
        assertEquals("3.1", root.get("equation number").asText());
    }

    @Test
    void testEquationNumberAbsentWhenNull() throws Exception {
        BoundingBox bbox = new BoundingBox(0, 0, 0, 100, 20);
        SemanticFormula formula = new SemanticFormula(bbox, "F=ma");
        // number not set — remains null

        String json = mapper.writeValueAsString(formula);
        JsonNode root = mapper.readTree(json);

        assertFalse(root.has("equation number"),
                "equation number field should be absent when not resolved");
    }
}
