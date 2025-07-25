/*
 * Licensees holding valid commercial licenses may use this software in
 * accordance with the terms contained in a written agreement between
 * you and Dual Lab srl. Alternatively, the terms and conditions that were
 * accepted by the licensee when buying and/or downloading the
 * software do apply.
 */
package com.hancom.layout;

import com.hancom.layout.processors.DocumentProcessor;
import com.hancom.layout.utils.Config;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.stream.Stream;

public class IntegrationTest {

    static Stream<Arguments> integrationTestParams() {
        return Stream.of(
                Arguments.of("2. 제안요청서(생성형 AI 구축 및 학습 용역).pdf"),
                Arguments.of("202309_KB 한국대표그룹주.pdf"),
                Arguments.of("2125318_의사국 의안과_의안원문_hwp.pdf"),
                Arguments.of("2125318_환경노동위원회_검토보고서.[2125318]탄중법 검토보고서(이수진의원안)_hwp.pdf"),
                Arguments.of("[월간운용보고서]교보악사위대한중소형밸류증권자투자신탁1호(주식)_20230930_030400214801.pdf"),
                Arguments.of("A Multi-Object Rectified Attention Network for Scene Text Recognition.pdf"),
                Arguments.of("DB차이나1_20230930_KRZ500237201.pdf"),
                Arguments.of("교보악사삼성전자투게더30증권투자신탁(채권혼합)_202211_판매안함.pdf"),
                Arguments.of("국회회의록_21대_414회_1차_환경노동위원회_hwp.pdf"),
                Arguments.of("덕신_01.TGC-50 [배관알곤]_조선선재(주).pdf"),
                Arguments.of("세보엠이씨_04. CGA-35[용접]_조선선재(주).pdf"),
                Arguments.of("제안요청서(수정)_230524 (1).pdf"),
                Arguments.of("특허_1020210175798.pdf")
        );
    }

    @ParameterizedTest(name = "{index}: ({0}) => {0}")
    @MethodSource("integrationTestParams")
    public void test(String fileName) throws IOException {
        File folder = new File(this.getClass().getResource("files/pdf/Korean/").getFile());
        Config config = new Config();
        config.setOutputFolder("temp");
        File pdf = new File(folder.getAbsolutePath() + "/" + fileName);
        DocumentProcessor.processFile(pdf.getAbsolutePath(), config);
        File resultJson = new File("temp/" + pdf.getName().replace(".pdf", ".json"));
        InputStream jsonFileInputStream = IntegrationTest.class.getResourceAsStream("files/json/Korean/" +
                pdf.getName().replace(".pdf", ".json"));
        ObjectMapper mapper = new ObjectMapper();
        JsonNode tree1 = mapper.readTree(jsonFileInputStream);
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
            ArrayNode array1 = (ArrayNode)child1;
            ArrayNode array2 = (ArrayNode)child2;
            Assertions.assertEquals(array1.size(), array2.size());
            for (int i = 0; i < array2.size(); i++) {
                checkJsonNodes(array1.get(i), array2.get(i));
            }
        }
    }
}
