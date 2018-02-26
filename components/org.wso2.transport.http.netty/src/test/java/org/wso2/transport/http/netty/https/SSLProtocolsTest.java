/*
 * Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.transport.http.netty.https;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.wso2.carbon.messaging.exceptions.ServerConnectorException;
import org.wso2.transport.http.netty.common.Constants;
import org.wso2.transport.http.netty.config.ListenerConfiguration;
import org.wso2.transport.http.netty.config.Parameter;
import org.wso2.transport.http.netty.config.SenderConfiguration;
import org.wso2.transport.http.netty.config.TransportsConfiguration;
import org.wso2.transport.http.netty.contentaware.listeners.EchoMessageListener;
import org.wso2.transport.http.netty.contract.HttpClientConnector;
import org.wso2.transport.http.netty.contract.HttpResponseFuture;
import org.wso2.transport.http.netty.contract.HttpWsConnectorFactory;
import org.wso2.transport.http.netty.contract.ServerConnector;
import org.wso2.transport.http.netty.contract.ServerConnectorFuture;
import org.wso2.transport.http.netty.contractimpl.DefaultHttpWsConnectorFactory;
import org.wso2.transport.http.netty.message.HTTPCarbonMessage;
import org.wso2.transport.http.netty.message.HTTPConnectorUtil;
import org.wso2.transport.http.netty.message.HttpMessageDataStreamer;
import org.wso2.transport.http.netty.util.TestUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.AssertJUnit.assertNotNull;

/**
 * Tests for SSL protocols.
 */
public class SSLProtocolsTest {

    private static Logger logger = LoggerFactory.getLogger(SSLProtocolsTest.class);

    private static HttpClientConnector httpClientConnector;
    private static HttpWsConnectorFactory httpWsConnectorFactory;
    private static ServerConnector serverConnector;

    @DataProvider(name = "protocols")

    public static Object[][] cipherSuites() {

        // true = expecting a SSL hand shake failure.
        // false = expecting no errors.
        return new Object[][] { { "TLSv1.1", "TLSv1.1", false, 9009 }, { "TLSv1.2", "TLSv1.1", true, 9008 },
                { "TLSv1.1", "TLSv1.2", true, 9007 } };
    }

    @Test(dataProvider = "protocols")
    /**
     * Set up the client and the server
     * @param clientProtocol SSL enabled protocol of client
     * @param serverProtocol SSL enabled protocol of server
     * @param hasException expecting an exception true/false
     * @param serverPort port
     */
    public void setup(String clientProtocol, String serverProtocol, boolean hasException, int serverPort)
            throws InterruptedException {

        Parameter clientprotocols = new Parameter("sslEnabledProtocols", clientProtocol);
        List<Parameter> clientParams = new ArrayList<>();
        clientParams.add(clientprotocols);

        Parameter serverProtocols = new Parameter("sslEnabledProtocols", serverProtocol);
        List<Parameter> severParams = new ArrayList<>();
        severParams.add(serverProtocols);

        TransportsConfiguration transportsConfiguration = TestUtil
                .getConfiguration("/simple-test-config" + File.separator + "netty-transports.yml");
        Set<SenderConfiguration> senderConfig = transportsConfiguration.getSenderConfigurations();
        senderConfig.forEach(config -> {
            if (config.getId().contains(Constants.HTTPS_SCHEME)) {
                config.setKeyStoreFile(TestUtil.getAbsolutePath(TestUtil.KEY_STORE_FILE_PATH));
                config.setTrustStoreFile(TestUtil.getAbsolutePath(config.getTrustStoreFile()));
                config.setKeyStorePassword(TestUtil.KEY_STORE_PASSWORD);
                config.setParameters(clientParams);
            }
        });

        httpWsConnectorFactory = new DefaultHttpWsConnectorFactory();
        ListenerConfiguration listenerConfiguration = getListenerConfiguration(serverPort, severParams);

        serverConnector = httpWsConnectorFactory
                .createServerConnector(TestUtil.getDefaultServerBootstrapConfig(), listenerConfiguration);
        ServerConnectorFuture future = serverConnector.start();
        future.setHttpConnectorListener(new EchoMessageListener());
        future.sync();

        httpClientConnector = httpWsConnectorFactory
                .createHttpClientConnector(HTTPConnectorUtil.getTransportProperties(transportsConfiguration),
                        HTTPConnectorUtil.getSenderConfiguration(transportsConfiguration, Constants.HTTPS_SCHEME));

        testSSLProtocols(hasException, serverPort);
    }

    private ListenerConfiguration getListenerConfiguration(int serverPort, List<Parameter> severParams) {
        ListenerConfiguration listenerConfiguration = ListenerConfiguration.getDefault();
        listenerConfiguration.setPort(serverPort);
        String verifyClient = "require";
        listenerConfiguration.setVerifyClient(verifyClient);
        listenerConfiguration.setTrustStoreFile(TestUtil.getAbsolutePath(TestUtil.TRUST_STORE_FILE_PATH));
        listenerConfiguration.setKeyStoreFile(TestUtil.getAbsolutePath(TestUtil.KEY_STORE_FILE_PATH));
        listenerConfiguration.setTrustStorePass(TestUtil.KEY_STORE_PASSWORD);
        listenerConfiguration.setKeyStorePass(TestUtil.KEY_STORE_PASSWORD);
        listenerConfiguration.setScheme(Constants.HTTPS_SCHEME);
        listenerConfiguration.setParameters(severParams);
        return listenerConfiguration;
    }

    private void testSSLProtocols(boolean hasException, int serverPort) {
        try {
            String testValue = "Test";
            HTTPCarbonMessage msg = TestUtil.createHttpsPostReq(serverPort, testValue, "");

            CountDownLatch latch = new CountDownLatch(1);
            SSLConnectorListener listener = new SSLConnectorListener(latch);
            HttpResponseFuture responseFuture = httpClientConnector.send(msg);
            responseFuture.setHttpConnectorListener(listener);

            latch.await(5, TimeUnit.SECONDS);

            HTTPCarbonMessage response = listener.getHttpResponseMessage();
            if (hasException) {
                assertNotNull(listener.getThrowables());
                boolean hasSSLException = false;
                for (Throwable throwable : listener.getThrowables()) {
                    if (throwable.getMessage() != null && (
                            throwable.getMessage().contains("javax.net.ssl.SSLHandshakeException") || throwable
                                    .getMessage().contains("handshake_failure"))) {
                        hasSSLException = true;
                        break;
                    }
                }
                assertTrue(hasSSLException);
            } else {
                assertNotNull(response);
                String result = new BufferedReader(
                        new InputStreamReader(new HttpMessageDataStreamer(response).getInputStream())).lines()
                        .collect(Collectors.joining("\n"));
                assertEquals(testValue, result);
            }
        } catch (Exception e) {
            TestUtil.handleException("Exception occurred while running testSSLProtocols", e);
        }
    }

    @AfterClass
    public void cleanUp() throws ServerConnectorException {
        try {
            serverConnector.stop();
            httpWsConnectorFactory.shutdown();
        } catch (Exception e) {
            logger.warn("Interrupted while waiting for response two", e);
        }
    }
}
