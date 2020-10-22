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
package io.gravitee.am.gateway.handler.vertx.auth.jose;

/**
 * No such KeyId exception is thrown when a JWT with a well known "kid" does not find a matching "kid" in the crypto
 * list.
 */
// TODO to remove when updating to vert.x 4
public final class NoSuchKeyIdException extends RuntimeException {

    private final String id;

    public NoSuchKeyIdException(String alg) {
        this(alg, "<null>");
    }

    public NoSuchKeyIdException(String alg, String kid) {
        super("algorithm [" + alg + "]: " + kid);
        this.id = alg + "#" + kid;
    }

    /**
     * Returns the missing key with the format {@code ALGORITHM + '#' + KEY_ID}.
     * @return the id of the missing key
     */
    public String id() {
        return id;
    }
}
