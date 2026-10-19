/*
 * Copyright (c) 2026, WSO2 LLC. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.am.analytics.publisher.util;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Minimal forward HTTP proxy for tests. It does not forward anything; it records each request it receives and
 * answers with the configured status (200 by default), or with 407 when proxy authentication is required and the
 * expected credentials are missing.
 */
public class TestHttpProxy implements Closeable {

    private final ServerSocket serverSocket;
    private final String expectedProxyAuthorization;
    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private volatile int responseStatus = 200;

    /**
     * Starts a proxy that does not require authentication.
     */
    public TestHttpProxy() throws IOException {

        this(null, null);
    }

    /**
     * Starts a proxy that requires the given Basic credentials, if both are non-null.
     */
    public TestHttpProxy(String username, String password) throws IOException {

        this.serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        if (username != null && password != null) {
            this.expectedProxyAuthorization = "Basic " + Base64.getEncoder()
                    .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        } else {
            this.expectedProxyAuthorization = null;
        }
        executor.submit(this::acceptConnections);
    }

    public String getHost() {

        return serverSocket.getInetAddress().getHostAddress();
    }

    public int getPort() {

        return serverSocket.getLocalPort();
    }

    public List<RecordedRequest> getRequests() {

        return requests;
    }

    /**
     * Sets the status returned for requests that pass proxy authentication, e.g. 503 to simulate a failing target.
     */
    public void setResponseStatus(int responseStatus) {

        this.responseStatus = responseStatus;
    }

    private void acceptConnections() {

        while (!serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                executor.submit(() -> handleConnection(socket));
            } catch (IOException e) {
                // Server socket closed.
                return;
            }
        }
    }

    private void handleConnection(Socket socket) {

        try (Socket s = socket) {
            // ISO-8859-1 maps bytes 1:1 to chars, so Content-Length can be used as a char count.
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(),
                    StandardCharsets.ISO_8859_1));
            OutputStream out = s.getOutputStream();
            String requestLine;
            // Keep the connection alive so that the client can retry on the same connection after a 407.
            while ((requestLine = in.readLine()) != null) {
                if (requestLine.isEmpty()) {
                    continue;
                }
                Map<String, String> headers = new HashMap<>();
                String line;
                while ((line = in.readLine()) != null && !line.isEmpty()) {
                    int idx = line.indexOf(':');
                    if (idx > 0) {
                        headers.put(line.substring(0, idx).trim().toLowerCase(Locale.ENGLISH),
                                line.substring(idx + 1).trim());
                    }
                }
                readBody(in, headers);
                requests.add(new RecordedRequest(requestLine, headers));

                String response;
                if (expectedProxyAuthorization != null
                        && !expectedProxyAuthorization.equals(headers.get("proxy-authorization"))) {
                    response = "HTTP/1.1 407 Proxy Authentication Required\r\n"
                            + "Proxy-Authenticate: Basic realm=\"test-proxy\"\r\n"
                            + "Content-Length: 0\r\n\r\n";
                } else {
                    response = "HTTP/1.1 " + responseStatus + " Test Response\r\n"
                            + "Content-Type: application/json\r\n"
                            + "Content-Length: 2\r\n\r\n{}";
                }
                out.write(response.getBytes(StandardCharsets.ISO_8859_1));
                out.flush();
            }
        } catch (IOException e) {
            // Client closed the connection.
        }
    }

    private static void readBody(BufferedReader in, Map<String, String> headers) throws IOException {

        if ("chunked".equalsIgnoreCase(headers.get("transfer-encoding"))) {
            String sizeLine;
            while ((sizeLine = in.readLine()) != null) {
                int size = Integer.parseInt(sizeLine.split(";")[0].trim(), 16);
                if (size == 0) {
                    // Trailer section ends with an empty line.
                    String trailer;
                    while ((trailer = in.readLine()) != null && !trailer.isEmpty()) {
                        // ignore trailers
                    }
                    return;
                }
                skip(in, size);
                in.readLine(); // CRLF after each chunk
            }
        } else if (headers.containsKey("content-length")) {
            skip(in, Integer.parseInt(headers.get("content-length")));
        }
    }

    private static void skip(BufferedReader in, int count) throws IOException {

        char[] buffer = new char[count];
        int read = 0;
        while (read < count) {
            int n = in.read(buffer, read, count - read);
            if (n < 0) {
                return;
            }
            read += n;
        }
    }

    @Override
    public void close() throws IOException {

        serverSocket.close();
        executor.shutdownNow();
    }

    /**
     * A request received by the proxy.
     */
    public static class RecordedRequest {

        private final String requestLine;
        private final Map<String, String> headers;

        RecordedRequest(String requestLine, Map<String, String> headers) {

            this.requestLine = requestLine;
            this.headers = headers;
        }

        public String getRequestLine() {

            return requestLine;
        }

        /**
         * @param name header name, case-insensitive
         */
        public String getHeader(String name) {

            return headers.get(name.toLowerCase(Locale.ENGLISH));
        }
    }
}
