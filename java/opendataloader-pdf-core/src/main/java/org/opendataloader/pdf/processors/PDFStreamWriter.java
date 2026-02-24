package org.opendataloader.pdf.processors;

import org.apache.pdfbox.io.IOUtils;
import org.verapdf.as.ASAtom;
import org.verapdf.cos.*;
import org.verapdf.operator.InlineImageOperator;
import org.verapdf.operator.Operator;
import org.verapdf.parser.Operators;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

public class PDFStreamWriter {

	public PDFStreamWriter() {

	}

	public byte[] write(List<Object> tokens) throws IOException {
		try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             PrintWriter printWriter = new PrintWriter(out)) {
			for (Object rawToken : tokens) {
                write(rawToken, printWriter, out);
			}
			return out.toByteArray();
		}
	}

    private void write(Object rawToken, PrintWriter printWriter, OutputStream out) throws IOException {
        if (rawToken instanceof COSArray) {
            printWriter.write("[");
            for (COSObject item : (COSArray) rawToken) {
                write(item.getDirectBase(), printWriter, out);
            }
            printWriter.write("]");
            printWriter.write(" ");
        } else if (rawToken instanceof COSDictionary) {
                printWriter.write("<<");
                for (Map.Entry<ASAtom, COSObject> item : ((COSDictionary)rawToken).getEntrySet()) {
                    printWriter.write(item.getKey().toString());
                    printWriter.write(" ");
                    write(item.getValue(), printWriter, out);
                }
                printWriter.write(">>");
                printWriter.write(" ");
        } else if (rawToken instanceof COSString) {
            COSString string = (COSString) rawToken;
            if (string.isHexadecimal()) {
                printWriter.write("<");
                printWriter.write(string.getHexString());
                printWriter.write(">");
                printWriter.write(" ");
            } else {
                printWriter.write("(");
                byte[] bytes = ((COSString) rawToken).get();
                for (byte currentByte : bytes) {
                    if (currentByte == 40 || currentByte == 41) {
                        printWriter.write('\\');
                    }
                    printWriter.write(currentByte);
                }
                printWriter.write(")");
                printWriter.write(" ");
            }
        } else if (rawToken instanceof COSBoolean) {
            printWriter.write(((COSBoolean) rawToken).getBoolean().toString());
            printWriter.write(" ");
        } else if (rawToken instanceof COSBase) {
            printWriter.write(rawToken.toString());
            printWriter.write(" ");
        } else if (rawToken instanceof COSObject) {
            write(((COSObject) rawToken).getDirectBase(), printWriter, out);
        } else if (rawToken instanceof Operator) {
            Operator operator = (Operator) rawToken;
            printWriter.println(((Operator) rawToken).getOperator());
            if (Operators.BI.equals(operator.getOperator())) {
                InlineImageOperator inlineImageOperator = (InlineImageOperator) operator;
                for (Map.Entry<ASAtom, COSObject> item : inlineImageOperator.getImageParameters().getEntrySet()) {
                    printWriter.write(item.getKey().toString());
                    printWriter.write(" ");
                    write(item.getValue(), printWriter, out);
                }
                printWriter.println(Operators.ID);
                IOUtils.copy(inlineImageOperator.getImageData(), out);
                printWriter.write("\n");
                printWriter.println(Operators.EI);
            }
        }
    }
}
