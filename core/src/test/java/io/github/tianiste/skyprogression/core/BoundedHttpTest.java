package io.github.tianiste.skyprogression.core;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BoundedHttpTest {
    @Test void validResponseCompletes() throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",e->{e.sendResponseHeaders(200,2);e.getResponseBody().write("{}".getBytes());e.close();});server.start();
        try(var client=HttpClient.newHttpClient()) {
            assertEquals("{}",BoundedHttp.get(client,URI.create("http://127.0.0.1:"+server.getAddress().getPort()),Duration.ofSeconds(2),32).body());
        } finally {server.stop(0);}
    }
    @Test void oversizedBodyIsRejected() throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",e->{e.sendResponseHeaders(200,1024);e.getResponseBody().write(new byte[1024]);e.close();});server.start();
        try(var client=HttpClient.newHttpClient()) {
            assertThrows(ExecutionException.class,()->BoundedHttp.get(client,URI.create("http://127.0.0.1:"+server.getAddress().getPort()),Duration.ofSeconds(2),32));
        } finally {server.stop(0);}
    }
    @Test void stalledBodyTimesOutEvenAfterHeadersArrive() throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var release=new CountDownLatch(1);
        server.createContext("/",e->{e.sendResponseHeaders(200,0);e.getResponseBody().write('x');e.getResponseBody().flush();try{release.await(3,TimeUnit.SECONDS);}catch(InterruptedException ex){Thread.currentThread().interrupt();}finally{e.close();}});server.start();
        try(var client=HttpClient.newHttpClient()) {
            assertTimeoutPreemptively(Duration.ofSeconds(2),()->assertThrows(Exception.class,()->BoundedHttp.get(client,URI.create("http://127.0.0.1:"+server.getAddress().getPort()),Duration.ofMillis(250),32)));
        } finally {release.countDown();server.stop(0);}
    }
}
