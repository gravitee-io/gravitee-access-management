/*
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

export function buildCertificate(suffix) {
    return {
        "type": "javakeystore-am-certificate",
        "configuration": "{\"jks\":\"{\\\"name\\\":\\\"server.jks\\\",\\\"type\\\":\\\"\\\",\\\"size\\\":2237,\\\"content\\\":\\\"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\\\"}\",\"storepass\":\"letmein\",\"alias\":\"mytestkey\",\"keypass\":\"changeme\"}",
        "name": "Certficate " + suffix
    }
}