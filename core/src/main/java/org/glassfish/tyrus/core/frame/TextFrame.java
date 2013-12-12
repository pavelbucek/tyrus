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

package org.glassfish.tyrus.core.frame;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;

import org.glassfish.tyrus.core.Frame;
import org.glassfish.tyrus.core.StrictUtf8;
import org.glassfish.tyrus.core.Utf8DecodingError;
import org.glassfish.tyrus.core.Utf8Utils;
import org.glassfish.tyrus.core.WebSocket;

public class TextFrame extends TyrusFrame {

    private final Charset utf8 = new StrictUtf8();
    private final CharsetDecoder currentDecoder = utf8.newDecoder();
    private final String textPayload;
    private final boolean continuation;

    private ByteBuffer remainder;

    public TextFrame(Frame frame, ByteBuffer remainder) {
        super(frame);
        this.textPayload = utf8Decode(isFin(), getPayloadData(), remainder);
        this.continuation = false;
    }

    public TextFrame(Frame frame, ByteBuffer remainder, boolean continuation) {
        super(frame);
        this.textPayload = utf8Decode(isFin(), getPayloadData(), remainder);
        this.continuation = continuation;
    }

    public TextFrame(String message, boolean continuation, boolean fin) {
        super(Frame.builder().payloadData(Utf8Utils.encode(new StrictUtf8(), message)).opcode(continuation ? (byte) 0x00 : (byte) 0x01).fin(fin).build());
        this.continuation = continuation;
        this.textPayload = message;
    }

    public String getTextPayload() {
        return textPayload;
    }

    /**
     * Nothing to be concerned with.
     * <p/>
     * Move along...
     */
    public ByteBuffer getRemainder() {
        return remainder;
    }

    @Override
    public void respond(WebSocket socket) {

        if (continuation) {
            socket.onFragment(isFin(), this);
        } else {
            if (isFin()) {
                socket.onMessage(this);
            } else {
                socket.onFragment(false, this);
            }
        }

    }

    private String utf8Decode(boolean finalFragment, byte[] data, ByteBuffer remainder) {
        final ByteBuffer b = getByteBuffer(data, remainder);
        int n = (int) (b.remaining() * currentDecoder.averageCharsPerByte());
        CharBuffer cb = CharBuffer.allocate(n);
        String res;
        while (true) {
            CoderResult result = currentDecoder.decode(b, cb, finalFragment);
            if (result.isUnderflow()) {
                if (finalFragment) {
                    currentDecoder.flush(cb);
                    if (b.hasRemaining()) {
                        throw new IllegalStateException("Final UTF-8 fragment received, but not all bytes consumed by decode process");
                    }
                    currentDecoder.reset();
                } else {
                    if (b.hasRemaining()) {
                        this.remainder = b;
                    }
                }
                cb.flip();
                res = cb.toString();
                break;
            }
            if (result.isOverflow()) {
                CharBuffer tmp = CharBuffer.allocate(2 * n + 1);
                cb.flip();
                tmp.put(cb);
                cb = tmp;
                continue;
            }
            if (result.isError() || result.isMalformed()) {
                throw new Utf8DecodingError("Illegal UTF-8 Sequence");
            }
        }

        return res;
    }

    ByteBuffer getByteBuffer(final byte[] data, ByteBuffer remainder) {
        if (remainder == null) {
            return ByteBuffer.wrap(data);
        } else {
            final int rem = remainder.remaining();
            final byte[] orig = remainder.array();
            byte[] b = new byte[rem + data.length];
            System.arraycopy(orig, orig.length - rem, b, 0, rem);
            System.arraycopy(data, 0, b, rem, data.length);
            return ByteBuffer.wrap(b);
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder(super.toString());
        sb.append(", textPayload='").append(textPayload).append('\'');
        return sb.toString();
    }
}
