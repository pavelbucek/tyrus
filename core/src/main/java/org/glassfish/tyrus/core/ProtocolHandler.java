/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2013 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * http://glassfish.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package org.glassfish.tyrus.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.websocket.CloseReason;
import javax.websocket.Extension;
import javax.websocket.SendHandler;
import javax.websocket.SendResult;
import javax.websocket.WebSocketContainer;

import org.glassfish.tyrus.core.frame.BinaryFrame;
import org.glassfish.tyrus.core.frame.CloseFrame;
import org.glassfish.tyrus.core.frame.TextFrame;
import org.glassfish.tyrus.core.frame.TyrusFrame;
import org.glassfish.tyrus.spi.CompletionHandler;
import org.glassfish.tyrus.spi.UpgradeRequest;
import org.glassfish.tyrus.spi.UpgradeResponse;
import org.glassfish.tyrus.spi.Writer;

public final class ProtocolHandler {

    public static final int MASK_SIZE = 4;

    private final AtomicBoolean onClosedCalled = new AtomicBoolean(false);
    private final boolean maskData;
    private final ParsingState state = new ParsingState();

    private WebSocket webSocket;
    private byte outFragmentedType;
    private long writeTimeoutMs = -1;
    private WebSocketContainer container;
    private List<Extension> extensions;
    private Writer writer;
    private byte inFragmentedType;
    private boolean processingFragment;
    private boolean sendingFragment = false;
    private ExtendedExtension.ExtensionContext extensionContext;

    private ByteBuffer remainder = null;

    public Writer getWriter() {
        return writer;
    }

    ProtocolHandler(boolean maskData) {
        this.maskData = maskData;
    }

    public Handshake handshake(WebSocketApplication app, UpgradeRequest request, UpgradeResponse response, ExtendedExtension.ExtensionContext extensionContext) {
        final Handshake handshake = createHandShake(request, extensionContext);
        handshake.respond(response, app);
        this.extensionContext = extensionContext;
        this.extensions = new ArrayList<Extension>();
        this.extensions.addAll(app.getSupportedExtensions());
        Collections.reverse(extensions);
        return handshake;
    }

    public void setWriter(Writer handler) {
        this.writer = handler;
    }

    /**
     * Client side.
     *
     * @param webSocket TODO.
     */
    public void setWebSocket(WebSocket webSocket) {
        this.webSocket = webSocket;
    }

    /**
     * Client side.
     *
     * @param extensionContext TODO.
     */
    public void setExtensionContext(ExtendedExtension.ExtensionContext extensionContext) {
        this.extensionContext = extensionContext;
    }

    /**
     * Client side.
     *
     * @param extensions TODO.
     */
    public void setExtensions(List<Extension> extensions) {
        this.extensions = extensions;
    }

    /**
     * Create {@link Handshake} on server side.
     *
     * @param webSocketRequest representation of received initial HTTP request.
     * @return new {@link Handshake} instance.
     */
    Handshake createHandShake(UpgradeRequest webSocketRequest, ExtendedExtension.ExtensionContext extensionContext) {
        return Handshake.createServerHandShake(webSocketRequest, extensionContext);
    }

    /**
     * Create {@link Handshake} on client side.
     *
     * @param webSocketRequest representation of HTTP request to be sent.
     * @return new {@link Handshake} instance.
     */
    public Handshake createClientHandShake(UpgradeRequest webSocketRequest) {
        return Handshake.createClientHandShake(webSocketRequest);
    }

    public final Future<Frame> send(Frame frame, boolean useTimeout) {
        return send(frame, null, useTimeout);
    }

    public final Future<Frame> send(Frame frame) {
        return send(frame, null, true);
    }

    Future<Frame> send(Frame frame,
                       CompletionHandler<Frame> completionHandler, Boolean useTimeout) {
        return write(frame, completionHandler, useTimeout);
    }

    Future<Frame> send(ByteBuffer frame,
                       CompletionHandler<Frame> completionHandler, Boolean useTimeout) {
        return write(frame, completionHandler, useTimeout);
    }

    public Future<Frame> send(byte[] data) {
        return send(new BinaryFrame(data, false, true), null, true);
    }

    public void send(final byte[] data, final SendHandler handler) {
        send(new BinaryFrame(data, false, true), new CompletionHandler<Frame>() {
            @Override
            public void failed(Throwable throwable) {
                handler.onResult(new SendResult(throwable));
            }

            @Override
            public void completed(Frame result) {
                handler.onResult(new SendResult());
            }
        }, true);
    }

    public Future<Frame> send(String data) {
        return send(new TextFrame(data, false, true));
    }

    public void send(final String data, final SendHandler handler) {
        send(new TextFrame(data, false, true), new CompletionHandler<Frame>() {
            @Override
            public void failed(Throwable throwable) {
                handler.onResult(new SendResult(throwable));
            }

            @Override
            public void completed(Frame result) {
                handler.onResult(new SendResult());
            }
        }, true);
    }

    public Future<Frame> sendRawFrame(ByteBuffer data) {
        return send(data, null, true);
    }

    public Future<Frame> stream(boolean last, byte[] bytes, int off, int len) {
        if (sendingFragment) {
            if (last) {
                sendingFragment = false;
            }
            return send(new BinaryFrame(Arrays.copyOfRange(bytes, off, off + len), true, last));
        } else {
            sendingFragment = !last;
            return send(new BinaryFrame(Arrays.copyOfRange(bytes, off, off + len), false, last));
        }
    }

    public Future<Frame> stream(boolean last, String fragment) {
        if (sendingFragment) {
            if (last) {
                sendingFragment = false;
            }
            return send(new TextFrame(fragment, true, last));
        } else {
            sendingFragment = !last;
            return send(new TextFrame(fragment, false, last));
        }
    }

    public Future<Frame> close(final int code, final String reason) {
        final CloseFrame outgoingCloseFrame;
        final CloseReason closeReason = new CloseReason(CloseReason.CloseCodes.getCloseCode(code), reason);

        if (code == CloseReason.CloseCodes.NO_STATUS_CODE.getCode() || code == CloseReason.CloseCodes.CLOSED_ABNORMALLY.getCode()
                || code == CloseReason.CloseCodes.TLS_HANDSHAKE_FAILURE.getCode()) {
            outgoingCloseFrame = new CloseFrame(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, reason));
        } else {
            outgoingCloseFrame = new CloseFrame(closeReason);
        }

        return send(outgoingCloseFrame, new CompletionHandler<Frame>() {

            @Override
            public void cancelled() {
                if (webSocket != null && !onClosedCalled.getAndSet(true)) {
                    webSocket.onClose(new CloseFrame(closeReason));
                }
            }

            @Override
            public void failed(final Throwable throwable) {
                if (webSocket != null && !onClosedCalled.getAndSet(true)) {
                    webSocket.onClose(new CloseFrame(closeReason));
                }
            }

            @Override
            public void completed(Frame result) {
                if (!maskData && (webSocket != null) && !onClosedCalled.getAndSet(true)) {
                    webSocket.onClose(new CloseFrame(closeReason));
                }
            }
        }, false);
    }

    @SuppressWarnings({"unchecked"})
    private Future<Frame> write(final Frame frame, final CompletionHandler<Frame> completionHandler, boolean useTimeout) {
        final Writer localWriter = writer;
        final TyrusFuture<Frame> future = new TyrusFuture<Frame>();

        if (localWriter == null) {
            throw new IllegalStateException("Connection is null");
        }

        final ByteBuffer byteBuffer = frame(frame);
        localWriter.write(byteBuffer, new CompletionHandlerWrapper(completionHandler, future, frame));

        return future;
    }

    @SuppressWarnings({"unchecked"})
    private Future<Frame> write(final ByteBuffer frame, final CompletionHandler<Frame> completionHandler, boolean useTimeout) {
        final Writer localWriter = writer;
        final TyrusFuture<Frame> future = new TyrusFuture<Frame>();

        if (localWriter == null) {
            throw new IllegalStateException("Connection is null");
        }

        localWriter.write(frame, new CompletionHandlerWrapper(completionHandler, future, null));

        return future;
    }

    /**
     * Convert a byte[] to a long. Used for rebuilding payload length.
     *
     * @param bytes byte array to be converted.
     * @return converted byte array.
     */
    long decodeLength(byte[] bytes) {
        return Utils.toLong(bytes, 0, bytes.length);
    }

    /**
     * Converts the length given to the appropriate framing data: <ol> <li>0-125 one element that is the payload length.
     * <li>up to 0xFFFF, 3 element array starting with 126 with the following 2 bytes interpreted as a 16 bit unsigned
     * integer showing the payload length. <li>else 9 element array starting with 127 with the following 8 bytes
     * interpreted as a 64-bit unsigned integer (the high bit must be 0) showing the payload length. </ol>
     *
     * @param length the payload size
     * @return the array
     */
    byte[] encodeLength(final long length) {
        byte[] lengthBytes;
        if (length <= 125) {
            lengthBytes = new byte[1];
            lengthBytes[0] = (byte) length;
        } else {
            byte[] b = Utils.toArray(length);
            if (length <= 0xFFFF) {
                lengthBytes = new byte[3];
                lengthBytes[0] = 126;
                System.arraycopy(b, 6, lengthBytes, 1, 2);
            } else {
                lengthBytes = new byte[9];
                lengthBytes[0] = 127;
                System.arraycopy(b, 0, lengthBytes, 1, 8);
            }
        }
        return lengthBytes;
    }

    void validate(final byte fragmentType, byte opcode) {
        if (opcode != 0 && opcode != fragmentType && !isControlFrame(opcode)) {
            throw new WebSocketException("Attempting to send a message while sending fragments of another");
        }
    }

    byte checkForLastFrame(Frame frame) {
        byte local = frame.getOpcode();
        if (!frame.isFin()) {
            if (outFragmentedType != 0) {
                local = 0x00;
            } else {
                outFragmentedType = local;
                local &= 0x7F;
            }
            validate(outFragmentedType, local);
        } else if (outFragmentedType != 0) {
            local = (byte) 0x80;
            outFragmentedType = 0;
        } else {
            local |= 0x80;
        }
        return local;
    }

    public void doClose() {
        final Writer localWriter = writer;
        if (localWriter == null) {
            throw new IllegalStateException("Connection is null");
        }

        try {
            localWriter.close();
        } catch (IOException e) {
            throw new IllegalStateException("IOException thrown when closing connection", e);
        }
    }

    /**
     * Sets the timeout for the writing operation.
     *
     * @param timeoutMs timeout in milliseconds.
     */
    public void setWriteTimeout(long timeoutMs) {
        this.writeTimeoutMs = timeoutMs;
    }

    /**
     * Sets the container.
     *
     * @param container container.
     */
    public void setContainer(WebSocketContainer container) {
        this.container = container;
    }

    public ByteBuffer frame(Frame frame) {

        if (extensions != null && extensions.size() > 0) {
            for (Extension extension : extensions) {
                if (extension instanceof ExtendedExtension) {
                    frame = ((ExtendedExtension) extension).processOutgoing(extensionContext, frame);
                }
            }
        }

        byte opcode = checkForLastFrame(frame);
        if (frame.isRsv1()) {
            opcode |= 0x40;
        }
        if (frame.isRsv2()) {
            opcode |= 0x20;
        }
        if (frame.isRsv3()) {
            opcode |= 0x10;
        }

        final byte[] bytes = frame.getPayloadData();
        final byte[] lengthBytes = encodeLength(frame.getPayloadLength());

        // TODO - length limited to int, it should be long (see RFC 9788, chapter 5.2)
        // TODO - in that case, we will need to NOT store dataframe inmemory - introduce maskingByteStream or
        // TODO   maskingByteBuffer
        final int payloadLength = (int) frame.getPayloadLength();
        int length = 1 + lengthBytes.length + payloadLength + (maskData ? MASK_SIZE : 0);
        int payloadStart = 1 + lengthBytes.length + (maskData ? MASK_SIZE : 0);
        final byte[] packet = new byte[length];
        packet[0] = opcode;
        System.arraycopy(lengthBytes, 0, packet, 1, lengthBytes.length);
        if (maskData) {
            Masker masker = new Masker(frame.getMaskingKey());
            packet[1] |= 0x80;
            masker.mask(packet, payloadStart, bytes, payloadLength);
            System.arraycopy(masker.getMask(), 0, packet, payloadStart - MASK_SIZE,
                    MASK_SIZE);
        } else {
            System.arraycopy(bytes, 0, packet, payloadStart, payloadLength);
        }
        return ByteBuffer.wrap(packet);
    }

    /**
     * TODO!
     *
     * @param buffer TODO.
     * @return TODO.
     */
    public Frame unframe(ByteBuffer buffer) {

        try {
            // this do { .. } while cycle was forced by findbugs check - complained about missing break statements.
            do {
                switch (state.state) {
                    case 0:
                        if (buffer.remaining() < 2) {
                            // Don't have enough bytes to read opcode and lengthCode
                            return null;
                        }

                        byte opcode = buffer.get();


                        state.finalFragment = isBitSet(opcode, 7);
                        state.controlFrame = isControlFrame(opcode);
                        state.opcode = (byte) (opcode & 0x7f);
//                        state.tyrusFrame = valueOf(inFragmentedType, state.opcode);
                        if (!state.finalFragment && state.controlFrame) {
                            throw new ProtocolError("Fragmented control frame");
                        }

                        byte lengthCode = buffer.get();

                        state.masked = (lengthCode & 0x80) == 0x80;
                        state.masker = new Masker(buffer);
                        if (state.masked) {
                            lengthCode ^= 0x80;
                        }
                        state.lengthCode = lengthCode;

                        state.state++;
                        break;
                    case 1:
                        if (state.lengthCode <= 125) {
                            state.length = state.lengthCode;
                        } else {
                            if (state.controlFrame) {
                                throw new ProtocolError("Control frame payloads must be no greater than 125 bytes.");
                            }

                            final int lengthBytes = state.lengthCode == 126 ? 2 : 8;
                            if (buffer.remaining() < lengthBytes) {
                                // Don't have enough bytes to read length
                                return null;
                            }
                            state.masker.setBuffer(buffer);
                            state.length = decodeLength(state.masker.unmask(lengthBytes));
                        }
                        state.state++;
                        break;
                    case 2:
                        if (state.masked) {
                            if (buffer.remaining() < MASK_SIZE) {
                                // Don't have enough bytes to read mask
                                return null;
                            }
                            state.masker.setBuffer(buffer);
                            state.masker.readMask();
                        }
                        state.state++;
                        break;
                    case 3:
                        if (buffer.remaining() < state.length) {
                            return null;
                        }

                        state.masker.setBuffer(buffer);
                        final byte[] data = state.masker.unmask((int) state.length);
                        if (data.length != state.length) {
                            throw new ProtocolError(String.format("Data read (%s) is not the expected" +
                                    " size (%s)", data.length, state.length));
                        }

                        // -----

//                        DataFrame dataFrame = state.tyrusFrame.create(state.finalFragment, data);


                        // added!

                        final Frame frame = Frame.builder()
                                .fin(state.finalFragment)
                                .rsv1(isBitSet(state.opcode, 6))
                                .rsv2(isBitSet(state.opcode, 5))
                                .rsv3(isBitSet(state.opcode, 4))
                                .opcode((byte) (state.opcode & 0xf))
                                .payloadLength(state.length)
                                .payloadData(data)
                                .build();

                        // /added!


                        state.recycle();

                        return frame;


//                        return dataFrame;

                    // -----

                    default:
                        // Should never get here
                        throw new IllegalStateException("Unexpected state: " + state.state);
                }
            } while (true);
        } catch (Exception e) {
            state.recycle();
            throw (RuntimeException) e;
        }
    }

    /**
     * TODO.
     * <p/>
     * called after Extension execution.
     * <p/>
     * validates frame + processes its content
     *
     * @param frame TODO.
     * @param socket TODO.
     */
    public void process(Frame frame, WebSocket socket) {
        if (frame.isRsv1() || frame.isRsv2() || frame.isRsv3()) {
            throw new ProtocolError("RSV bit(s) incorrectly set.");
        }

        final byte opcode = frame.getOpcode();
        final boolean fin = frame.isFin();
        if (!isControlFrame(opcode)) {
            final boolean continuationFrame = (opcode == 0);
            if (continuationFrame && !processingFragment) {
                throw new ProtocolError("End fragment sent, but wasn't processing any previous fragments");
            }
            if (processingFragment && !continuationFrame) {
                throw new ProtocolError("Fragment sent but opcode was not 0");
            }
            if (!fin && !continuationFrame) {
                processingFragment = true;
            }
            if (!fin) {
                if (inFragmentedType == 0) {
                    inFragmentedType = opcode;
                }
            }
        }

        TyrusFrame tyrusFrame = TyrusFrame.wrap(frame, inFragmentedType, remainder);

        // TODO - utf8 decoder needs this state to be shared among decoded frames.
        // TODO - investigate whether it can be removed; (this effectively denies lazy decoding)
        if (tyrusFrame instanceof TextFrame) {
            remainder = ((TextFrame) tyrusFrame).getRemainder();
        }

        tyrusFrame.respond(socket);

        if (!isControlFrame(opcode) && fin) {
            inFragmentedType = 0;
            processingFragment = false;
        }
    }


    boolean isControlFrame(byte opcode) {
        return (opcode & 0x08) == 0x08;
    }

    private boolean isBitSet(final byte b, int bit) {
        return ((b >> bit & 1) != 0);
    }

    /**
     * Handler passed to the {@link org.glassfish.tyrus.spi.Writer}.
     */
    private static class CompletionHandlerWrapper extends CompletionHandler<ByteBuffer> {

        private final CompletionHandler<Frame> frameCompletionHandler;
        private final TyrusFuture<Frame> future;
        private final Frame frame;

        private CompletionHandlerWrapper(CompletionHandler<Frame> frameCompletionHandler, TyrusFuture<Frame> future, Frame frame) {
            this.frameCompletionHandler = frameCompletionHandler;
            this.future = future;
            this.frame = frame;
        }

        @Override
        public void cancelled() {
            if (frameCompletionHandler != null) {
                frameCompletionHandler.cancelled();
            }

            if (future != null) {
                future.setFailure(new RuntimeException("frame writing was canceled."));
            }
        }

        @Override
        public void failed(Throwable throwable) {
            if (frameCompletionHandler != null) {
                frameCompletionHandler.failed(throwable);
            }

            if (future != null) {
                future.setFailure(throwable);
            }
        }

        @Override
        public void completed(ByteBuffer result) {
            if (frameCompletionHandler != null) {
                frameCompletionHandler.completed(frame);
            }

            if (future != null) {
                future.setResult(frame);
            }
        }

        @Override
        public void updated(ByteBuffer result) {
            if (frameCompletionHandler != null) {
                frameCompletionHandler.updated(frame);
            }
        }
    }

    private static class ParsingState {
        int state = 0;
        byte opcode = (byte) -1;
        long length = -1;
        TyrusFrame tyrusFrame;
        boolean masked;
        Masker masker;
        boolean finalFragment;
        boolean controlFrame;
        private byte lengthCode = -1;

        void recycle() {
            state = 0;
            opcode = (byte) -1;
            length = -1;
            lengthCode = -1;
            masked = false;
            masker = null;
            finalFragment = false;
            controlFrame = false;
            tyrusFrame = null;
        }
    }
}