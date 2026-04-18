package org.opendataloader.pdf.hybrid;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;

import static org.junit.jupiter.api.Assertions.assertTrue;

class HancomAIClientRequestIdTest {

    private MockWebServer server;
    private HancomAIClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        client = new HancomAIClient(
            server.url("").toString().replaceAll("/$", ""),
            new OkHttpClient(),
            new ObjectMapper()
        );
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void callModule_request_id_includes_sha_short() throws Exception {
        byte[] pdfBytes = "PDF_CONTENT_FOR_TEST".getBytes();
        String shaShort = sha256Hex(pdfBytes).substring(0, 12);

        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody("{\"SUCCESS\":true,\"RESULT\":[]}"));

        // invokeCallModule is a package-private test hook added by the patch.
        client.invokeCallModule(pdfBytes, "DOCUMENT_LAYOUT_WITH_OCR");

        RecordedRequest req = server.takeRequest();
        String body = req.getBody().readUtf8();
        assertTrue(body.contains("odl-" + shaShort + "-dla-ocr"),
            "REQUEST_ID should include sha_short and module short name; body=" + body);
    }

    @Test
    void callImageCaptioning_request_id_includes_page_obj() throws Exception {
        byte[] pdfBytes = "PDF_CONTENT_FOR_TEST".getBytes();
        String shaShort = sha256Hex(pdfBytes).substring(0, 12);
        client.setSourcePdfShaShort(shaShort); // test hook

        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody("{\"SUCCESS\":true,\"RESULT\":[[{\"caption\":\"x\"}]]}"));

        client.invokeCallImageCaptioning(new byte[]{1, 2, 3}, /*pageNum*/ 2, /*objectId*/ 7);

        RecordedRequest req = server.takeRequest();
        String body = req.getBody().readUtf8();
        assertTrue(body.contains("odl-" + shaShort + "-p2-o7-caption"),
            "image REQUEST_ID format mismatch; body=" + body);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static String sha256Hex(byte[] b) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return bytesToHex(md.digest(b));
    }
}
