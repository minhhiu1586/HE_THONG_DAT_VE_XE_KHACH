package com.datvexe.email;

import com.sun.net.httpserver.HttpServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BrevoClientTest {
    @Test void sendsTransactionalPayloadAndRedactsKey() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        AtomicReference<String> sent = new AtomicReference<>();
        server.createContext("/email", exchange -> {
            assertEquals("test-key",exchange.getRequestHeaders().getFirst("api-key"));
            sent.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            byte[] response = "{\"messageId\":\"accepted-test-message\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(201,response.length); exchange.getResponseBody().write(response); exchange.close();
        }); server.start();
        try {
            var config = new BrevoClient.Config("test-key","sender@example.com","Vexexin",true);
            assertFalse(config.toString().contains("test-key"));
            var client = new BrevoClient(config,HttpClient.newHttpClient(),URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/email"));
            assertEquals("accepted-test-message",client.send(new BrevoClient.Message("ORDER","buyer@example.com","Buyer","Paid","<p>receipt</p>","receipt",java.util.List.of(new BrevoClient.Attachment("Ve-3EYDH.png","cG5n")))));
            var body = new ObjectMapper().readTree(sent.get()); assertEquals("buyer@example.com",body.path("to").get(0).path("email").asText()); var key = body.path("headers").path("idempotencyKey").asText();
            assertEquals(java.util.UUID.nameUUIDFromBytes("vexexin-receipt:ORDER".getBytes(StandardCharsets.UTF_8)).toString(),key);
            assertDoesNotThrow(() -> java.util.UUID.fromString(key));
            assertEquals("Ve-3EYDH.png",body.path("attachment").get(0).path("name").asText());
            assertEquals("cG5n",body.path("attachment").get(0).path("content").asText());
            assertEquals("receipt",body.path("textContent").asText()); assertTrue(body.path("htmlContent").asText().contains("receipt"));
        } finally { server.stop(0); }
    }
    @Test void rejectionAndUncertainResultsAreClassifiedWithoutLeakingProviderBody() throws Exception {
        for (int status : new int[]{403,429,500}) {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.createContext("/",exchange -> { byte[] body = "private-provider-details".getBytes(); exchange.sendResponseHeaders(status,body.length);exchange.getResponseBody().write(body);exchange.close(); }); server.start();
            try {
                var client = new BrevoClient(new BrevoClient.Config("test-key","sender@example.com","Vexexin",true),HttpClient.newHttpClient(),URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/"));
                var failure=assertThrows(BrevoClient.SendFailure.class,()->client.send(new BrevoClient.Message("ORDER","buyer@example.com","Buyer","Paid","html","text")));
                assertEquals(status==429,failure.retry); assertEquals(status>=500,failure.uncertain); assertFalse(failure.getMessage().contains("private"));
            } finally { server.stop(0); }
        }
    }
}
