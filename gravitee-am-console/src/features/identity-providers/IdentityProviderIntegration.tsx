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
import {
    Card,
    CardContent,
    CardDescription,
    CardHeader,
    CardTitle,
    CopyableText,
    Field,
    FieldDescription,
    FieldError,
    FieldTitle,
    Skeleton,
} from '@gravitee/graphene-core';
import { useQuery } from '@tanstack/react-query';
import { accessUrl, domainEntrypoint, listDomainEntrypoints } from '../../lib/api/domains';
import type { IdentityProvider } from '../../lib/api/identity-providers';
import { useCurrentDomain } from '../../lib/session/domain';

interface IdentityProviderIntegrationProps {
    readonly provider: IdentityProvider;
}

/** The redirect URI and the custom login page link that an external provider needs. */
export function IdentityProviderIntegration({ provider }: IdentityProviderIntegrationProps) {
    const { domain, ref } = useCurrentDomain();
    const { data: entrypoints, error } = useQuery({ queryKey: ['domain-entrypoints', ref], queryFn: () => listDomainEntrypoints(ref) });
    const entrypoint = entrypoints && domainEntrypoint(entrypoints);
    const redirectUri = entrypoint
        ? `${accessUrl(entrypoint.url, domain.path ?? '/', domain.vhostMode ?? false, domain.vhosts ?? [])}/login/callback`
        : undefined;
    return (
        <Card>
            <CardHeader>
                <CardTitle>Integration</CardTitle>
                <CardDescription>
                    The redirect URI (callback URL, or Assertion Consumer Service URL) must be known by your identity server. Register it in
                    your identity server’s app settings.
                </CardDescription>
            </CardHeader>
            <CardContent className="grid gap-5">
                <Field>
                    <FieldTitle>Redirect URI</FieldTitle>
                    {error ? (
                        <FieldError>Could not load the domain entrypoints.</FieldError>
                    ) : entrypoints ? (
                        redirectUri && <CopyableText className="max-w-full" value={redirectUri} />
                    ) : (
                        <Skeleton className="h-8 w-full" />
                    )}
                </Field>
                <Field>
                    <FieldTitle>Custom login page</FieldTitle>
                    <CopyableText
                        className="max-w-full"
                        value={`<a th:href="\${authorizeUrls.get('${provider.id}')}">SIGN IN WITH OAUTH2 PROVIDER</a>`}
                    />
                    <FieldDescription>
                        If you enabled a custom login form, add this link to its content to offer this provider.
                    </FieldDescription>
                </Field>
            </CardContent>
        </Card>
    );
}
