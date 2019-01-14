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
package io.gravitee.am.model.jose;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * See : https://tools.ietf.org/html/rfc7518#section-6.2.1.1
 * See : https://tools.ietf.org/html/rfc5480#section-4
 *
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public enum Curve {

    P_256("P-256","secp256r1"),
    P_384("P-384","secp384r1"),
    P_521("P-521","secp521r1");

    private String name;
    private String stdName;

    Curve(String name, String stdName) {
        this.name = name;
        this.stdName = stdName;
    }

    public String getName() {
        return name;
    }

    public String getStdName() {
        return stdName;
    }

    public static Optional<Curve> getByName(String name) {
        return Stream.of(Curve.values())
                .filter(c -> c.name.equals(name))
                .findFirst();
    }
}
