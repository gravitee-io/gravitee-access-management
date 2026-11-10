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
import { Card, CardContent, CardHeader, CardTitle, Skeleton } from '@gravitee/graphene-core';
import { useEffect, useState, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { ErrorPanel } from '../../app/components/ErrorPanel';
import { PageHeader } from '../../app/components/PageHeader';
import { useCurrentDomain } from '../../lib/session/domain';
import { usePermissions } from '../../lib/session/permissions';
import { useApplication } from './ApplicationContext';
import { OverviewCodeBlock as CodeBlock, OverviewNote as Note } from './OverviewCodeBlock';
import { useEndpointBaseUrl } from './endpoint-base-url';
import { cleanELParameters, createPkce, exchangesWithPost, type Pkce, requestsWithBasic } from './overview-guide';

const NO_PERMISSION = 'Insufficient permission';

const PKCE_JS = `// generate the code_verifier
const array = new Uint8Array(32);
window.crypto.getRandomValues(array);
const codeVerifier = base64URLEncode(bufferToString(array));

// generate the code_challenge
const data = new TextEncoder().encode(codeVerifier);
window.crypto.subtle.digest('SHA-256', data)
  .then(buffer => base64URLEncode(String.fromCharCode(...new Uint8Array(buffer))))
  .then(codeChallenge => { /* send it to /oauth/authorize */ });`;

const PKCE_JAVA = `// generate the code_verifier
SecureRandom secureRandom = new SecureRandom();
byte[] code = new byte[32];
secureRandom.nextBytes(code);
String codeVerifier = Base64.getUrlEncoder().withoutPadding().encodeToString(code);

// generate the code_challenge
byte[] bytes = codeVerifier.getBytes("US-ASCII");
MessageDigest md = MessageDigest.getInstance("SHA-256");
String codeChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(md.digest(bytes));`;

const CALL_API = `curl -X GET \\
  https://api.gravitee.io/api/v1/data \\
  -H 'Authorization: Bearer access_token'`;

function Section({ title, children }: { readonly title: string; readonly children: ReactNode }) {
    return (
        <Card>
            <CardHeader>
                <CardTitle role="heading" aria-level={2}>
                    {title}
                </CardTitle>
            </CardHeader>
            <CardContent className="space-y-3 text-sm leading-relaxed">{children}</CardContent>
        </Card>
    );
}

function usePkce(enabled: boolean): Pkce | undefined {
    const [pkce, setPkce] = useState<Pkce>();
    useEffect(() => {
        if (enabled) void createPkce().then(setPkce);
    }, [enabled]);
    return pkce;
}

export function ApplicationOverviewPage() {
    const { application, applicationPath } = useApplication();
    const { domain } = useCurrentDomain();
    const canReadOpenId = usePermissions().has('application_openid_read');
    const { data: baseUrl, error, isPending } = useEndpointBaseUrl();

    const oauth = canReadOpenId ? (application.settings?.oauth ?? {}) : {};
    const pkce = usePkce(canReadOpenId && !!oauth.forcePKCE);
    const clientId = canReadOpenId ? (oauth.clientId ?? '') : NO_PERMISSION;
    const firstRedirectUri = oauth.redirectUris?.[0];
    const redirectUri = !canReadOpenId
        ? NO_PERMISSION
        : firstRedirectUri !== undefined
          ? cleanELParameters(firstRedirectUri)
          : 'Not defined';
    const encodedClientId = encodeURIComponent(clientId);
    const responseType = oauth.grantTypes?.includes('authorization_code') ? 'code' : 'token';
    const challenge = pkce ? `&code_challenge=${pkce.challenge}&code_challenge_method=S256` : '';
    const verifier = pkce ? `&code_verifier=${pkce.verifier}` : '';

    if (error) return <ErrorPanel description="The domain's entrypoints could not be retrieved." error={error} />;
    if (isPending) return <Skeleton className="h-32 w-full" />;

    const link = (label: string, path: string) => (
        <Link to={`${applicationPath}/${path}`} className="font-medium text-foreground underline">
            {label}
        </Link>
    );

    if (application.type === 'service') {
        return (
            <div className="flex flex-col gap-6">
                <PageHeader
                    title="Machine-to-machine interaction"
                    description="A service application is a program that calls APIs with no user involved."
                />
                <Section title="Get an access token">
                    <p>Request an access token with the OAuth 2.0 client credentials grant.</p>
                    <Note>
                        <p>Use the client secret you copied when the application was created.</p>
                    </Note>
                    <CodeBlock
                        code={
                            requestsWithBasic(oauth.tokenEndpointAuthMethod)
                                ? `curl -X POST \\\n  ${baseUrl}/oauth/token \\\n  -H 'Content-Type: application/x-www-form-urlencoded' \\\n  -u '${clientId}:|client_secret|' \\\n  -d 'grant_type=client_credentials'`
                                : `curl -X POST \\\n  ${baseUrl}/oauth/token \\\n  -H 'Content-Type: application/x-www-form-urlencoded' \\\n  -d 'grant_type=client_credentials&client_id=${clientId}&client_secret=|client_secret|'`
                        }
                    />
                    <p>If the request is valid, the authorization server issues an access token for calling your APIs.</p>
                </Section>
                <Section title="Call your APIs">
                    <p>Send the access token in the Authorization header.</p>
                    <CodeBlock copyable={false} code={CALL_API} />
                </Section>
            </div>
        );
    }

    if (application.type === 'resource_server') {
        return (
            <div className="flex flex-col gap-6">
                <PageHeader
                    title="Control access to your resources"
                    description="A UMA 2.0 resource server lets end users manage access to their data through policies they define."
                />
                <Section title="Manage resource sets">
                    <p>Put resources under the authorization server&apos;s protection on behalf of their owner.</p>
                    <CodeBlock
                        code={`curl -X POST \\\n  ${baseUrl}/uma/protection/resource_set \\\n  -H 'Authorization: Bearer |PAT|' \\\n  -H 'Content-Type: application/json' \\\n  -d '{\n    "resource_scopes": ["view", "http://photoz.example.com/dev/scopes/print"],\n    "description": "Collection of digital photographs",\n    "name": "Photo Album",\n    "type": "http://www.example.com/rsrcs/photoalbum"\n  }'`}
                    />
                    <Note>
                        <p>
                            The endpoint requires a Protection API Token (PAT): an OAuth 2.0 access token with the uma_protection scope,
                            obtained with any grant that involves the resource owner.
                        </p>
                    </Note>
                </Section>
                <Section title="Manage access policies">
                    <CodeBlock
                        code={`curl -X POST \\\n  ${baseUrl}/uma/protection/resource_set/:resourceId/policies \\\n  -H 'Authorization: Bearer |PAT|' \\\n  -H 'Content-Type: application/json' \\\n  -d '{\n    "name": "My policy",\n    "description": "My policy description",\n    "type": "GROOVY",\n    "condition": "GROOVY script"\n  }'`}
                    />
                </Section>
                <Section title="Request a permission ticket">
                    <p>
                        When a client asks for a protected resource, request the permissions it needs and receive a permission ticket to
                        hand back to the client.
                    </p>
                    <CodeBlock
                        code={`curl -X POST \\\n  ${baseUrl}/uma/protection/permission \\\n  -H 'Authorization: Bearer |PAT|' \\\n  -H 'Content-Type: application/json' \\\n  -d '{\n    "resource_id": ":resourceId",\n    "resource_scopes": ["view"]\n  }'`}
                    />
                </Section>
                <Section title="Obtain a requesting party token (RPT)">
                    <p>The requesting party&apos;s application exchanges the ticket for an access token with the UMA 2.0 grant.</p>
                    <CodeBlock
                        code={`curl -X POST \\\n  ${baseUrl}/oauth/token \\\n  -H 'Authorization: Basic Base64(rqpClientId:rqpClientSecret)' \\\n  -H 'Content-Type: application/x-www-form-urlencoded' \\\n  -d 'grant_type=urn:ietf:params:oauth:grant-type:uma-ticket' \\\n  -d 'ticket=|PT|' \\\n  -d 'claim_token=|ID_Token|' \\\n  -d 'claim_token_format=http://openid.net/specs/openid-connect-core-1_0.html#IDToken'`}
                    />
                </Section>
            </div>
        );
    }

    return (
        <div className="flex flex-col gap-6">
            <PageHeader
                title="Integrate authentication into your application"
                description="Access Management relies on OAuth 2.0 and OpenID Connect. This guide uses this application's settings."
            />
            <Section title="Application information">
                <dl className="grid gap-3 sm:grid-cols-2">
                    <div>
                        <dt className="text-muted-foreground">Domain</dt>
                        <dd className="font-mono">{domain.path}</dd>
                    </div>
                    <div>
                        <dt className="text-muted-foreground">Client ID</dt>
                        <dd className="font-mono">{clientId}</dd>
                    </div>
                </dl>
                <p>You can also find these in the application&apos;s {link('settings', 'settings')}.</p>
            </Section>
            <Section title="Configure your users">
                <p>
                    Users are redirected to Access Management to log in, and authenticated against identity providers such as LDAP, a
                    database, web services or social providers.
                </p>
                <Note>
                    <p>
                        Configure them in the application&apos;s {link('identity providers', 'idp')}. If there are none, contact the
                        administrator of the domain.
                    </p>
                </Note>
            </Section>
            <Section title="Initiate the login flow">
                <p>Redirect users to the following URL to show the login page and start the authentication flow:</p>
                <CodeBlock
                    code={`${baseUrl}/oauth/authorize?client_id=${encodedClientId}&response_type=${responseType}&redirect_uri=${encodeURIComponent(redirectUri)}${challenge}`}
                />
                <Note>
                    <p>Every page shown during the flow can be customized in the application&apos;s {link('design', 'design/forms')}.</p>
                </Note>
                <p>Once authenticated, users return to your application at:</p>
                <CodeBlock code={redirectUri} />
                <p>
                    With either an <b>authorization_code</b>, or an <b>access_token</b> and <b>ID token</b>, depending on the{' '}
                    <code>response_type</code> query parameter.
                </p>
                <Note>
                    <p>With response_type=code, exchange the authorization code for an access token.</p>
                    <p>Use the client secret you copied when the application was created.</p>
                </Note>
                <CodeBlock
                    code={
                        exchangesWithPost(oauth.tokenEndpointAuthMethod)
                            ? `curl -X POST \\\n  ${baseUrl}/oauth/token \\\n  -H 'Content-Type: application/x-www-form-urlencoded' \\\n  -d 'grant_type=authorization_code&code=|code|&redirect_uri=${redirectUri}&client_id=${clientId}&client_secret=|client_secret|${verifier}'`
                            : `curl -X POST \\\n  ${baseUrl}/oauth/token \\\n  -u '${clientId}:|client_secret|' \\\n  -H 'Content-Type: application/x-www-form-urlencoded' \\\n  -d 'grant_type=authorization_code&code=|code|&redirect_uri=${redirectUri}&client_id=${encodedClientId}${verifier}'`
                    }
                />
            </Section>
            <Section title="Proof Key for Code Exchange (PKCE)">
                <p>
                    {oauth.forcePKCE ? 'This application requires PKCE. ' : 'If the application requires PKCE, '}
                    generate a <b>code_verifier</b> for the token endpoint and a <b>code_challenge</b> for the authorization endpoint. Both
                    are cryptographically random values your application creates.
                </p>
                <p role="heading" aria-level={3} className="font-medium">
                    JavaScript
                </p>
                <CodeBlock copyable={false} code={PKCE_JS} />
                <p role="heading" aria-level={3} className="font-medium">
                    Java
                </p>
                <CodeBlock copyable={false} code={PKCE_JAVA} />
            </Section>
            <Section title="Get user information">
                <p>Read the user&apos;s profile from the ID token, or call:</p>
                <CodeBlock code={`curl -X GET \\\n  ${baseUrl}/oidc/userinfo \\\n  -H 'Authorization: Bearer access_token'`} />
            </Section>
            <Section title="Call your APIs">
                <p>
                    Some of the APIs your application calls restrict access to authenticated users with enough privileges. Send the access
                    token in the Authorization header.
                </p>
                <CodeBlock copyable={false} code={CALL_API} />
            </Section>
            <Section title="Log the user out">
                <p>To end a user&apos;s session, send them to:</p>
                <CodeBlock code={`${baseUrl}/logout`} />
                <Note>
                    <p>
                        Tokens are not revoked by default. Add <code>invalidate_tokens=true</code> to revoke the user&apos;s current tokens,
                        and <code>post_logout_redirect_uri</code> or <code>target_url</code> to send them back to your application
                        afterwards.
                    </p>
                </Note>
            </Section>
        </div>
    );
}
