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
package io.gravitee.am.identityprovider.ldap.authentication;

import io.gravitee.am.identityprovider.ldap.LdapIdentityProviderConfiguration;
import io.gravitee.am.identityprovider.ldap.authentication.encoding.BinaryToTextEncoder;
import io.gravitee.am.identityprovider.ldap.authentication.encoding.PasswordEncoder;
import java.util.Arrays;
import org.ldaptive.*;
import org.ldaptive.auth.AuthenticationCriteria;
import org.ldaptive.auth.AuthenticationHandlerResponse;
import org.ldaptive.auth.PooledCompareAuthenticationHandler;
import org.ldaptive.pool.PooledConnectionFactory;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CompareAuthenticationHandler extends PooledCompareAuthenticationHandler {

    private PasswordEncoder passwordEncoder;
    private BinaryToTextEncoder binaryToTextEncoder;
    private LdapIdentityProviderConfiguration configuration;

    public CompareAuthenticationHandler() {}

    public CompareAuthenticationHandler(final PooledConnectionFactory cf) {
        super(cf);
    }

    public CompareAuthenticationHandler(
        PooledConnectionFactory connectionFactory,
        PasswordEncoder passwordEncoder,
        BinaryToTextEncoder binaryToTextEncoder,
        LdapIdentityProviderConfiguration configuration
    ) {
        this(connectionFactory);
        this.passwordEncoder = passwordEncoder;
        this.binaryToTextEncoder = binaryToTextEncoder;
        this.configuration = configuration;
    }

    @Override
    protected AuthenticationHandlerResponse authenticateInternal(final Connection c, final AuthenticationCriteria criteria)
        throws LdapException {
        final byte[] hash = passwordEncoder.digestCredential(criteria.getCredential());
        String encodedHash = binaryToTextEncoder.encode(hash);
        String encodedHashValue = configuration.isHashEncodedByThirdParty()
            ? encodedHash
            : String.format("{%s}%s", passwordEncoder.getPasswordSchemeLabel(), encodedHash);

        final LdapAttribute la = new LdapAttribute(getPasswordAttribute(), encodedHashValue.getBytes());
        final CompareOperation compare = new CompareOperation(c);
        final CompareRequest request = new CompareRequest(criteria.getDn(), la);
        request.setControls(processRequestControls(criteria));

        final Response<Boolean> compareResponse = compare.execute(request);
        return new AuthenticationHandlerResponse(
            compareResponse.getResult(),
            compareResponse.getResultCode(),
            c,
            compareResponse.getMessage(),
            compareResponse.getControls(),
            compareResponse.getMessageId()
        );
    }

    @Override
    public String toString() {
        return String.format(
            "[%s@%d::factory=%s, passwordAttribute=%s, passwordScheme=%s, controls=%s]",
            getClass().getName(),
            hashCode(),
            getConnectionFactory(),
            getPasswordAttribute(),
            passwordEncoder.getPasswordSchemeLabel(),
            Arrays.toString(getAuthenticationControls())
        );
    }
}
