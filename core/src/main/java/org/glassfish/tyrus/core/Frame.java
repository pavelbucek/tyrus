/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved.
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

import java.security.SecureRandom;

/**
 * WebSocket frame representation.
 * <pre>TODO:
 * - masking (isMask is currently ignored)
 * - validation</pre>
 *
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class Frame {

    private final boolean fin;
    private final boolean rsv1;
    private final boolean rsv2;
    private final boolean rsv3;
    private final boolean mask;

    private final byte opcode;
    private final long payloadLength;
    private final int maskingKey;

    private final byte[] payloadData;

    private final boolean controlFrame;

    protected Frame(Frame frame) {
        this.fin = frame.fin;
        this.rsv1 = frame.rsv1;
        this.rsv2 = frame.rsv2;
        this.rsv3 = frame.rsv3;
        this.mask = frame.mask;
        this.opcode = frame.opcode;
        this.payloadLength = frame.payloadLength;
        this.maskingKey = frame.maskingKey;
        this.payloadData = frame.payloadData;

        this.controlFrame = (opcode & 0x08) == 0x08;
    }

    Frame(boolean fin, boolean rsv1, boolean rsv2, boolean rsv3, boolean mask, byte opcode, long payloadLength, int maskingKey, byte[] payloadData) {
        this.fin = fin;
        this.rsv1 = rsv1;
        this.rsv2 = rsv2;
        this.rsv3 = rsv3;
        this.mask = mask;
        this.opcode = opcode;
        this.payloadLength = payloadLength;
        this.maskingKey = maskingKey;
        this.payloadData = payloadData;

        this.controlFrame = (opcode & 0x08) == 0x08;
    }

    public boolean isFin() {
        return fin;
    }

    public boolean isRsv1() {
        return rsv1;
    }

    public boolean isRsv2() {
        return rsv2;
    }

    public boolean isRsv3() {
        return rsv3;
    }

    /**
     * Currently not used.
     *
     * @return not used.
     */
    public boolean isMask() {
        return mask;
    }

    public byte getOpcode() {
        return opcode;
    }

    public long getPayloadLength() {
        return payloadLength;
    }

    public int getMaskingKey() {
        return maskingKey;
    }

    public byte[] getPayloadData() {
        if (payloadData.length == payloadLength) {
            return payloadData;
        } else {
            if (payloadData.length > payloadLength) {
                byte[] tmp = new byte[(int) payloadLength];
                System.arraycopy(payloadData, 0, tmp, 0, (int) payloadLength);
                return tmp;
            }

            return null;
        }
    }

    public boolean isControlFrame() {
        return controlFrame;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Frame{");
        sb.append("fin=").append(fin);
        sb.append(", rsv1=").append(rsv1);
        sb.append(", rsv2=").append(rsv2);
        sb.append(", rsv3=").append(rsv3);
        sb.append(", mask=").append(mask);
        sb.append(", opcode=").append(opcode);
        sb.append(", payloadLength=").append(payloadLength);
        sb.append(", maskingKey=").append(maskingKey);
        sb.append('}');
        return sb.toString();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(Frame frame) {
        return new Builder(frame);
    }

    public final static class Builder {

        private boolean fin;
        private boolean rsv1;
        private boolean rsv2;
        private boolean rsv3;
        private boolean mask;

        private byte opcode;
        private long payloadLength;
        private int maskingKey = new SecureRandom().nextInt();

        private byte[] payloadData;

        public Builder() {
        }

        public Builder(Frame frame) {
            this.fin = frame.fin;
            this.rsv1 = frame.rsv1;
            this.rsv2 = frame.rsv2;
            this.rsv3 = frame.rsv3;
            this.mask = frame.mask;
            this.opcode = frame.opcode;
            this.payloadLength = frame.payloadLength;
            this.maskingKey = frame.maskingKey;
            this.payloadData = frame.payloadData;
        }

        public Frame build() {
            return new Frame(fin, rsv1, rsv2, rsv3, mask, opcode, payloadLength, maskingKey, payloadData);
        }

        public Builder fin(boolean fin) {
            this.fin = fin;
            return this;
        }

        public Builder rsv1(boolean rsv1) {
            this.rsv1 = rsv1;
            return this;
        }

        public Builder rsv2(boolean rsv2) {
            this.rsv2 = rsv2;
            return this;
        }

        public Builder rsv3(boolean rsv3) {
            this.rsv3 = rsv3;
            return this;
        }

        /**
         * Currently not used.
         *
         * @param mask not used.
         * @return updated builder.
         */
        public Builder mask(boolean mask) {
            this.mask = mask;
            return this;
        }

        public Builder opcode(byte opcode) {
            this.opcode = opcode;
            return this;
        }

        public Builder payloadLength(long payloadLength) {
            this.payloadLength = payloadLength;
            return this;
        }

        /**
         * Set masking key. Default value is {@code new SecureRandom().nextInt();}.
         *
         * @param maskingKey masking key.
         * @return updated builder.
         */
        public Builder maskingKey(int maskingKey) {
            this.maskingKey = maskingKey;
            return this;
        }

        /**
         * Set payload data. {@link #payloadLength(long)} is also updated with payloadData.length.
         *
         * @param payloadData data to be set.
         * @return updated builder.
         * @see #payloadLength(long)
         */
        public Builder payloadData(byte[] payloadData) {
            this.payloadData = payloadData;
            this.payloadLength = payloadData.length;
            return this;
        }
    }
}
