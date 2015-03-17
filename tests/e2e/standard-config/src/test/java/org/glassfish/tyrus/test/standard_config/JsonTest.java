/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2015 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.tyrus.test.standard_config;

import java.io.IOException;
import java.io.StringReader;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.DecodeException;
import javax.websocket.Decoder;
import javax.websocket.DeploymentException;
import javax.websocket.EncodeException;
import javax.websocket.Encoder;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.OnMessage;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import javax.json.Json;
import javax.json.JsonException;
import javax.json.JsonObject;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.server.Server;
import org.glassfish.tyrus.test.tools.TestContainer;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests the JSON format.
 *
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class JsonTest extends TestContainer {

    private CountDownLatch messageLatch;

    private String receivedMessage;

    private static final String SENT_MESSAGE = "{\"NAME\" : \"Danny\"}";

    private final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

    @Test
    public void testJson() throws DeploymentException {
        Server server = startServer(JsonTestEndpoint.class);

        messageLatch = new CountDownLatch(1);

        try {
            ClientManager client = createClient();
            client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig config) {
                    try {
                        session.addMessageHandler(new MessageHandler.Whole<String>() {
                            @Override
                            public void onMessage(String message) {
                                System.out.println("Received message: " + message);
                                receivedMessage = message;
                                messageLatch.countDown();
                            }
                        });
                        session.getBasicRemote().sendText(SENT_MESSAGE);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }, cec, getURI(JsonTestEndpoint.class));
            messageLatch.await(5, TimeUnit.SECONDS);
            Assert.assertTrue("The received message is {REPLY : Danny}",
                              receivedMessage.equals("{\"REPLY\":\"Danny\"}"));
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }

    /**
     * @author Danny Coward (danny.coward at oracle.com)
     */
    @ServerEndpoint(
            value = "/json2",
            encoders = {JsonEncoder.class},
            decoders = {JsonDecoder.class}
    )
    public static class JsonTestEndpoint {

        @OnMessage
        public JsonObject helloWorld(JsonObject message) {
            return Json.createObjectBuilder().add("REPLY", message.get("NAME")).build();
        }

    }

    /**
     * @author Danny Coward (danny.coward at oracle.com)
     */
    public static class JsonDecoder implements Decoder.Text<JsonObject> {

        @Override
        public JsonObject decode(String s) throws DecodeException {
            try {
                return Json.createReader(new StringReader(s)).readObject();
            } catch (JsonException je) {
                throw new DecodeException(s, "JSON not decoded", je);
            }
        }

        @Override
        public boolean willDecode(String s) {
            return true;
        }
    }

    /**
     * @author Danny Coward (danny.coward at oracle.com)
     */
    public static class JsonEncoder implements Encoder.Text<JsonObject> {

        @Override
        public String encode(JsonObject o) throws EncodeException {
            return o.toString();
        }
    }
}
