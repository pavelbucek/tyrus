/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2015 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.tyrus.test.e2e.jdk8;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.OnMessage;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.server.Server;
import org.glassfish.tyrus.test.tools.TestContainer;

import org.junit.Test;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class MultipleServerEndpointAnnotationTest extends TestContainer {

    @ServerEndpoint("/multipleServerEndpoint1")
    @ServerEndpoint("/multipleServerEndpoint2")
    public static class MultipleServerEndpointAnnotationEndpoint {

        @OnMessage
        public void echo(Session session, String message) throws IOException {
            session.getBasicRemote().sendText(message + " (from your server)");
            session.close();
        }
    }

    @Test
    public void testMultipleServerEndpointAnnotation() throws DeploymentException, InterruptedException, IOException {
        final Server server = startServer(MultipleServerEndpointAnnotationEndpoint.class);

        try {
            performTest("/multipleServerEndpoint1");
            performTest("/multipleServerEndpoint2");

        } catch (Exception e) {
            e.printStackTrace();
            fail();
        } finally {
            stopServer(server);
        }
    }

    private void performTest(String endpointPath) throws DeploymentException, IOException, InterruptedException {
        final CountDownLatch messageLatch = new CountDownLatch(1);
        final CountDownLatch onCloseLatch = new CountDownLatch(1);

        final ClientManager client = createClient();
        client.connectToServer(new Endpoint() {
            @Override
            public void onOpen(Session session, EndpointConfig EndpointConfig) {

                try {
                    session.addMessageHandler(String.class, message -> {
                        System.out.println("### Received: " + message);

                        if (message.equals("Do or do not, there is no try. (from your server)")) {
                            messageLatch.countDown();
                        }
                    });

                    session.getBasicRemote().sendText("Do or do not, there is no try.");
                } catch (IOException e) {
                    // do nothing
                }
            }

            @Override
            public void onClose(Session session, CloseReason closeReason) {
                System.out.println("### Client session closed: " + closeReason);
                onCloseLatch.countDown();
            }

        }, ClientEndpointConfig.Builder.create().build(), getURI(endpointPath));

        assertTrue(messageLatch.await(1, TimeUnit.SECONDS));
        assertTrue(onCloseLatch.await(1, TimeUnit.SECONDS));
    }
}
