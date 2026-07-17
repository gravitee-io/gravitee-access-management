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
package io.gravitee.am.authdevice.notifier.cibafederation.provider;

/** Typed, validated, immutable projection of the fields the notifier consumes from an upstream OP's
 *  OIDC discovery document. Produced by {@link OidcDiscoveryResolver}; {@code issuer} is the value the
 *  OP declares (RFC 8414 §3.3), never derived by string manipulation. */
public record ProviderMetadata(String issuer, String backchannelAuthEndpoint, String tokenEndpoint) {}
