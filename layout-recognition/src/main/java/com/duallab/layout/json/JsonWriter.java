package com.duallab.layout.json;

import com.duallab.layout.json.serializers.JsonName;
import com.duallab.layout.json.serializers.ObjectMapperHolder;
import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import org.verapdf.as.ASAtom;
import org.verapdf.cos.COSDictionary;
import org.verapdf.cos.COSObjType;
import org.verapdf.cos.COSObject;
import org.verapdf.cos.COSTrailer;
import org.verapdf.gf.model.impl.cos.GFCosInfo;
import org.verapdf.pd.PDDocument;
import org.verapdf.tools.StaticResources;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class JsonWriter {
    private static JsonGenerator getJsonGenerator(String fileName) throws IOException {
        JsonFactory jsonFactory = new JsonFactory();
        return jsonFactory.createGenerator(new File(fileName), JsonEncoding.UTF8)
                .setPrettyPrinter(new DefaultPrettyPrinter())
                .setCodec(ObjectMapperHolder.getObjectMapper());
    }

    public static void writeToJson(String pdfFileName, String outputName, List<List<IObject>> contents) throws IOException {
        String jsonFileName = outputName.substring(0, outputName.length()- 3) + "json";
        JsonGenerator jsonGenerator = getJsonGenerator(jsonFileName);
        jsonGenerator.writeStartObject();
        writeDocumentInfo(jsonGenerator, pdfFileName);
        jsonGenerator.writeArrayFieldStart(JsonName.CHILDREN);
        for (int pageNumber = 0; pageNumber < StaticContainers.getDocument().getNumberOfPages(); pageNumber++) {
            for (IObject content : contents.get(pageNumber)) {
                jsonGenerator.writePOJO(content);
            }
        }

        jsonGenerator.writeEndArray();
        jsonGenerator.writeEndObject();
        jsonGenerator.close();
        System.out.println("Created " + jsonFileName);
    }

    private static void writeDocumentInfo(JsonGenerator generator, String pdfName) throws IOException {
        PDDocument document = StaticResources.getDocument();
        generator.writeStringField(JsonName.FILE_NAME, pdfName);
        generator.writeNumberField(JsonName.NUMBER_OF_PAGES, document.getNumberOfPages());
        COSTrailer trailer = document.getDocument().getTrailer();
        COSObject object = trailer.getKey(ASAtom.INFO);
        GFCosInfo info = new GFCosInfo((COSDictionary)
                (object != null && object.getType() == COSObjType.COS_DICT ?
                        object.getDirectBase() : COSDictionary.construct().get()));
        generator.writeStringField(JsonName.AUTHOR, info.getAuthor() != null ? info.getAuthor() : info.getXMPCreator());
        generator.writeStringField(JsonName.TITLE, info.getTitle() != null ? info.getTitle() : info.getXMPTitle());
    generator.writeStringField(JsonName.CREATION_DATE, info.getCreationDate() != null ?
                info.getCreationDate() : info.getXMPCreateDate());
        generator.writeStringField(JsonName.MODIFICATION_DATE, info.getModDate() != null ?
                info.getModDate() : info.getXMPModifyDate());
    }
}
