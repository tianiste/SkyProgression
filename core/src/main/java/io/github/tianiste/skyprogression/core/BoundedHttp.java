package io.github.tianiste.skyprogression.core;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.*;

/** Worker-thread transport: bounds both the entire response lifetime and its size. */
public final class BoundedHttp {
    private BoundedHttp() {}
    public static HttpResponse<String> get(HttpClient client, URI uri, Duration timeout, long maxBytes) throws Exception {
        var request=HttpRequest.newBuilder(uri).timeout(timeout).GET().build();
        var pending=client.sendAsync(request,HttpResponse.BodyHandlers.limiting(HttpResponse.BodyHandlers.ofString(),maxBytes));
        try { return pending.get(timeout.toMillis(),TimeUnit.MILLISECONDS); }
        finally { if(!pending.isDone())pending.cancel(true); }
    }
}
