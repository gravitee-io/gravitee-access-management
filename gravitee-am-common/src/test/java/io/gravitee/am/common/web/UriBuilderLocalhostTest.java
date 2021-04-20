/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.am.common.web;

import static org.junit.runners.Parameterized.Parameters;

import java.util.Arrays;
import java.util.Collection;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@RunWith(Parameterized.class)
public class UriBuilderLocalhostTest {

    private String hostType;
    private String host;
    private boolean result;

    public UriBuilderLocalhostTest(String hostType, String host, boolean result) {
        this.hostType = hostType;
        this.host = host;
        this.result = result;
    }

    @Parameters(name = "Test host type={0} expecting to be a localhost={2} : {1}")
    public static Collection<Object[]> data() {
        return Arrays.asList(
            new Object[][] {
                //named localhost test case
                { "name", "localhost", true },
                { "name", "LOCALHOST", true },
                { "name", "gravitee.io", false },
                //ipv4 localhost test case
                { "ipv4", "127.0.0.1", true },
                { "ipv4", "127.0.0.001", true },
                { "ipv4", "127.0.00.1", true },
                { "ipv4", "127.00.0.1", true },
                { "ipv4", "127.000.000.001", true },
                { "ipv4", "127.0000.0000.1", true },
                { "ipv4", "127.0.01", true },
                { "ipv4", "127.1", true },
                { "ipv4", "127.001", true },
                { "ipv4", "127.0.0.254", true },
                { "ipv4", "127.63.31.15", true },
                { "ipv4", "127.255.255.254", true },
                { "ipv4", "192.168.0.1", false },
                { "ipv4", "10.1.2.3", false },
                //ipv6 localhost test case
                { "ipv6", "0:0:0:0:0:0:0:1", true },
                { "ipv6", "0:0:0:0:0:0:0:1", true },
                { "ipv6", "::1", true },
                { "ipv6", "0::1", true },
                { "ipv6", "0:0:0::1", true },
                { "ipv6", "0000::0001", true },
                { "ipv6", "0000:0:0000::0001", true },
                { "ipv6", "0000:0:0000::1", true },
                { "ipv6", "0::0:1", true },
                { "ipv6", "0001::1", false },
                { "ipv6", "dead:beef::1", false },
                { "ipv6", "::dead:beef:1", false },
            }
        );
    }

    @Test
    public void test() {
        Assert.assertEquals(this.result, UriBuilder.isLocalhost(this.host));
    }
}
