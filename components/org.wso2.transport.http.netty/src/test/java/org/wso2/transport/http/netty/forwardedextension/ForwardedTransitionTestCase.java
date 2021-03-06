/*
 * Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.transport.http.netty.forwardedextension;

import io.netty.handler.codec.http.DefaultHttpHeaders;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.transport.http.netty.common.Constants;
import org.wso2.transport.http.netty.config.ForwardedExtensionConfig;
import org.wso2.transport.http.netty.message.HttpCarbonMessage;
import org.wso2.transport.http.netty.util.TestUtil;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNull;

/**
 * A test class for X-Forwarded header transition behaviour.
 */
public class ForwardedTransitionTestCase extends ForwardedClientTemplate {
    @BeforeClass
    public void setUp() {
        senderConfiguration.setForwardedExtensionConfig(ForwardedExtensionConfig.TRANSITION);
        super.setUp();
    }

    @Test
    public void testSingleHeader() {
        try {
            HttpCarbonMessage response = sendRequest(new DefaultHttpHeaders());
            assertEquals(response.getHeader(Constants.FORWARDED), "by=127.0.0.1; proto=http");

            response = sendRequest(new DefaultHttpHeaders()
                    .set(Constants.FORWARDED, "for=192.0.2.11;by=203.0.113.60;proto=http;host=example.com"));
            assertEquals(response.getHeader(Constants.FORWARDED),
                    "for=192.0.2.11, for=203.0.113.60; by=127.0.0.1; host=example.com; proto=http");

            response = sendRequest(new DefaultHttpHeaders().set(Constants.X_FORWARDED_FOR, "192.0.2.43, 203.0.113.60"));
            assertEquals(response.getHeader(Constants.FORWARDED), "for=192.0.2.43, for=203.0.113.60; by=127.0.0.1");
            assertNull(response.getHeader(Constants.X_FORWARDED_FOR));

        } catch (Exception e) {
            TestUtil.handleException("Exception occurred while running postTest", e);
        }
    }

    @Test
    public void testMultipleHeader() {
        try {
            DefaultHttpHeaders headers1 = new DefaultHttpHeaders();
            headers1.set(Constants.FORWARDED, "by=203.0.113.60;proto=http;host=example.com");
            headers1.set(Constants.X_FORWARDED_FOR, "123.34.24.67");
            headers1.set(Constants.X_FORWARDED_BY, "13.134.224.167");

            HttpCarbonMessage response = sendRequest(headers1);
            assertEquals(response.getHeader(Constants.FORWARDED),
                    "for=203.0.113.60; by=127.0.0.1; host=example.com; proto=http");
            assertEquals(response.getHeader(Constants.X_FORWARDED_FOR), "123.34.24.67");

            DefaultHttpHeaders headers2 = new DefaultHttpHeaders();
            headers2.set(Constants.X_FORWARDED_BY, "123.34.24.65");
            headers2.set(Constants.X_FORWARDED_HOST, "www.abc.com");
            headers2.set(Constants.X_FORWARDED_PROTO, "https");

            response = sendRequest(headers2);
            assertEquals(response.getHeader(Constants.FORWARDED),
                    "for=123.34.24.65; by=127.0.0.1; host=www.abc.com; proto=https");
            assertNull(response.getHeader(Constants.X_FORWARDED_FOR));
            assertNull(response.getHeader(Constants.X_FORWARDED_BY));
            assertNull(response.getHeader(Constants.X_FORWARDED_HOST));
            assertNull(response.getHeader(Constants.X_FORWARDED_PROTO));

        } catch (Exception e) {
            TestUtil.handleException("Exception occurred while running postTest", e);
        }
    }
}
