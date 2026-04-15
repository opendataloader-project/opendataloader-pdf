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
            out.write("[".getBytes());
            for (COSObject item : (COSArray) rawToken) {
                write(item.getDirectBase(), printWriter, out);
            }
            out.write("]".getBytes());
            out.write(" ".getBytes());
        } else if (rawToken instanceof COSDictionary) {
            out.write("<<".getBytes());
            for (Map.Entry<ASAtom, COSObject> item : ((COSDictionary)rawToken).getEntrySet()) {
                out.write(item.getKey().toString().getBytes());
                out.write(" ".getBytes());
                write(item.getValue(), printWriter, out);
            }
            out.write(">>".getBytes());
            out.write(" ".getBytes());
        } else if (rawToken instanceof COSString) {
            COSString string = (COSString) rawToken;
            if (string.isHexadecimal()) {
                out.write("<".getBytes());
                out.write(string.getHexString().getBytes());
                out.write(">".getBytes());
                out.write(" ".getBytes());
            } else {
                out.write("(".getBytes());
                byte[] bytes = ((COSString) rawToken).get();
                for (byte currentByte : bytes) {
                    if (currentByte == 40 || currentByte == 41 || currentByte == 92) {
                        out.write('\\');
                    }
                    out.write(currentByte);
                }
                out.write(")".getBytes());
                out.write(" ".getBytes());
            }
        } else if (rawToken instanceof COSBoolean) {
            out.write(((COSBoolean) rawToken).getBoolean().toString().getBytes());
            out.write(" ".getBytes());
        } else if (rawToken instanceof COSBase) {
            out.write(rawToken.toString().getBytes());
            out.write(" ".getBytes());
        } else if (rawToken instanceof COSObject) {
            write(((COSObject) rawToken).getDirectBase(), printWriter, out);
        } else if (rawToken instanceof Operator) {
            Operator operator = (Operator) rawToken;
            out.write(((Operator) rawToken).getOperator().getBytes());
            out.write("\n".getBytes());
            if (Operators.BI.equals(operator.getOperator())) {
                InlineImageOperator inlineImageOperator = (InlineImageOperator) operator;
                for (Map.Entry<ASAtom, COSObject> item : inlineImageOperator.getImageParameters().getEntrySet()) {
                    out.write(item.getKey().toString().getBytes());
                    out.write(" ".getBytes());
                    write(item.getValue(), printWriter, out);
                }
                out.write(Operators.ID.getBytes());
                out.write("\n".getBytes());
                IOUtils.copy(inlineImageOperator.getImageData(), out);
                out.write("\n".getBytes());
                out.write(Operators.EI.getBytes());
                out.write("\n".getBytes());
            }
        }
    }
}
