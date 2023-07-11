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

import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public class UriBuilderLocalhostTest {

    public static Stream<Arguments> params_that_must_test_host() {
        return Stream.of(
                //named localhost test case
                Arguments.of("localhost", true),
                Arguments.of("LOCALHOST", true),
                Arguments.of("gravitee.io", false),
                //ipv4 localhost test case
                Arguments.of("127.0.0.1", true),
                Arguments.of("127.0.0.001", true),
                Arguments.of("127.0.00.1", true),
                Arguments.of("127.00.0.1", true),
                Arguments.of("127.000.000.001", true),
                Arguments.of("127.0000.0000.1", true),
                Arguments.of("127.0.01", true),
                Arguments.of("127.1", true),
                Arguments.of("127.001", true),
                Arguments.of("127.0.0.254", true),
                Arguments.of("127.63.31.15", true),
                Arguments.of("127.255.255.254", true),
                Arguments.of("192.168.0.1", false),
                Arguments.of("10.1.2.3", false),
                //ipv6 localhost test case
                Arguments.of("0:0:0:0:0:0:0:1", true),
                Arguments.of("0:0:0:0:0:0:0:1", true),
                Arguments.of("::1", true),
                Arguments.of("0::1", true),
                Arguments.of("0:0:0::1", true),
                Arguments.of("0000::0001", true),
                Arguments.of("0000:0:0000::0001", true),
                Arguments.of("0000:0:0000::1", true),
                Arguments.of("0::0:1", true),
                Arguments.of("0001::1", false),
                Arguments.of("dead:beef::1", false),
                Arguments.of("::dead:beef:1", false)
        );
    }

    @ParameterizedTest(name = "Test host expecting to be a localhost={1} : {0}")
    @MethodSource("params_that_must_test_host")
    public void must_test_host(String host, boolean expected) {
        assertEquals(expected, UriBuilder.isLocalhost(host));
    }
}
