package jp.hane.gesture.core;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.function.Consumer;

/**
 * Minimal WebSocket server (RFC 6455) with no dependencies.
 * The AI app (browser) connects here as a client and streams pose JSON.
 * Handles: handshake, text frames (incl. basic fragmentation), ping/pong, close.
 */
public final class WsServer {
    private static final String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private final int port;
    private final Consumer<String> onMessage;
    private volatile boolean running;
    private ServerSocket serverSocket;
    private Thread acceptThread;

    public WsServer(int port, Consumer<String> onMessage) {
        this.port = port;
        this.onMessage = onMessage;
    }

    public void start() {
        if (running) return;
        running = true;
        acceptThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                while (running) {
                    Socket s = serverSocket.accept();
                    Thread t = new Thread(() -> handleClient(s));
                    t.setDaemon(true);
                    t.start();
                }
            } catch (IOException e) {
                if (running) System.err.println("[HaneGesture] WsServer error: " + e.getMessage());
            }
        }, "hanegesture-ws-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    public void stop() {
        running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
    }

    public boolean isRunning() { return running; }

    private void handleClient(Socket s) {
        try (s) {
            InputStream in = s.getInputStream();
            OutputStream out = s.getOutputStream();
            if (!handshake(in, out)) return;
            readFrames(in, out);
        } catch (IOException ignored) {
            // client disconnected
        }
    }

    private boolean handshake(InputStream in, OutputStream out) throws IOException {
        StringBuilder head = new StringBuilder();
        int b;
        while (!head.toString().endsWith("\r\n\r\n")) {
            b = in.read();
            if (b < 0) return false;
            head.append((char) b);
            if (head.length() > 16384) return false;
        }
        String key = null;
        for (String line : head.toString().split("\r\n")) {
            int c = line.indexOf(':');
            if (c > 0 && line.substring(0, c).trim().equalsIgnoreCase("Sec-WebSocket-Key")) {
                key = line.substring(c + 1).trim();
            }
        }
        if (key == null) return false;
        String accept;
        try {
            accept = Base64.getEncoder().encodeToString(
                    MessageDigest.getInstance("SHA-1").digest((key + GUID).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            return false; // SHA-1 is guaranteed by the JCA spec; treat as fatal for this client
        }
        String resp = "HTTP/1.1 101 Switching Protocols\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + accept + "\r\n\r\n";
        out.write(resp.getBytes(StandardCharsets.UTF_8));
        out.flush();
        return true;
    }

    private void readFrames(InputStream in, OutputStream out) throws IOException {
        StringBuilder fragmented = null;
        while (running) {
            int b0 = readByte(in);
            int b1 = readByte(in);
            boolean fin = (b0 & 0x80) != 0;
            int opcode = b0 & 0x0F;
            boolean masked = (b1 & 0x80) != 0;
            long len = b1 & 0x7F;
            if (len == 126) len = (readByte(in) << 8) | readByte(in);
            else if (len == 127) {
                len = 0;
                for (int i = 0; i < 8; i++) len = (len << 8) | readByte(in);
            }
            if (len > 1_000_000) return; // sanity cap
            byte[] mask = null;
            if (masked) {
                mask = new byte[4];
                readFully(in, mask);
            }
            byte[] payload = new byte[(int) len];
            readFully(in, payload);
            if (mask != null) {
                for (int i = 0; i < payload.length; i++) payload[i] ^= mask[i % 4];
            }
            switch (opcode) {
                case 0x0: // continuation
                case 0x1: // text
                case 0x2: // binary (treated as text)
                    String text = new String(payload, StandardCharsets.UTF_8);
                    if (fragmented == null && fin) {
                        deliver(text);
                    } else {
                        if (fragmented == null) fragmented = new StringBuilder();
                        fragmented.append(text);
                        if (fin) { deliver(fragmented.toString()); fragmented = null; }
                    }
                    break;
                case 0x8: // close
                    sendFrame(out, 0x8, new byte[0]);
                    return;
                case 0x9: // ping -> pong
                    sendFrame(out, 0xA, payload);
                    break;
                default:
                    break; // ignore pong and others
            }
        }
    }

    private void deliver(String text) {
        try { onMessage.accept(text); } catch (Exception ignored) {}
    }

    private synchronized void sendFrame(OutputStream out, int opcode, byte[] payload) throws IOException {
        out.write(0x80 | opcode);
        if (payload.length < 126) {
            out.write(payload.length);
        } else if (payload.length < 65536) {
            out.write(126);
            out.write((payload.length >> 8) & 0xFF);
            out.write(payload.length & 0xFF);
        } else {
            out.write(127);
            for (int i = 7; i >= 0; i--) out.write((int) ((payload.length >> (8 * i)) & 0xFF));
        }
        out.write(payload);
        out.flush();
    }

    private int readByte(InputStream in) throws IOException {
        int b = in.read();
        if (b < 0) throw new EOFException("stream closed");
        return b;
    }

    private void readFully(InputStream in, byte[] buf) throws IOException {
        int off = 0;
        while (off < buf.length) {
            int n = in.read(buf, off, buf.length - off);
            if (n < 0) throw new EOFException("stream closed");
            off += n;
        }
    }
}
