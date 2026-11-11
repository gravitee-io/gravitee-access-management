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
    Field,
    FieldDescription,
    FieldLabel,
    InputGroup,
    InputGroupAddon,
    InputGroupButton,
    InputGroupInput,
    Skeleton,
    toast,
} from '@gravitee/graphene-core';
import { CopyIcon } from '@gravitee/graphene-core/icons';
import { ErrorPanel } from '../../app/components/ErrorPanel';
import { PageHeader } from '../../app/components/PageHeader';
import { copyText } from '../../lib/clipboard';
import { useCurrentDomain } from '../../lib/session/domain';
import { useEndpointBaseUrl } from './endpoint-base-url';
import { type Endpoint, endpointGroups } from './endpoint-groups';

function EndpointField({ endpoint, url }: { readonly endpoint: Endpoint; readonly url: string }) {
    const copy = () =>
        copyText(url)
            .then(() => toast.success('Copied to clipboard'))
            .catch(() => toast.error('Could not copy to the clipboard.'));
    const id = `endpoint-${endpoint.key}`;
    return (
        <Field>
            <FieldLabel htmlFor={id}>{endpoint.label}</FieldLabel>
            <InputGroup>
                <InputGroupInput id={id} readOnly value={url} className="font-mono text-sm" />
                <InputGroupAddon align="inline-end">
                    <InputGroupButton size="icon-xs" aria-label={`Copy ${endpoint.label}`} onClick={() => void copy()}>
                        <CopyIcon aria-hidden />
                    </InputGroupButton>
                </InputGroupAddon>
            </InputGroup>
            <FieldDescription>{endpoint.description}</FieldDescription>
        </Field>
    );
}

export function ApplicationEndpointsPage() {
    const { domain } = useCurrentDomain();
    const { data: baseUrl, error, isPending } = useEndpointBaseUrl();

    return (
        <div className="flex flex-col gap-6">
            <PageHeader title="Endpoints" description="The endpoints this application uses to talk to the authorization server." />
            {error ? (
                <ErrorPanel description="The domain's entrypoints could not be retrieved." error={error} />
            ) : isPending ? (
                <Skeleton className="h-96 w-full" />
            ) : (
                endpointGroups(domain).map(group => (
                    <Card key={group.title}>
                        <CardHeader>
                            <CardTitle role="heading" aria-level={2}>
                                {group.title}
                            </CardTitle>
                            <CardDescription>{group.description}</CardDescription>
                        </CardHeader>
                        <CardContent className="grid gap-5">
                            {group.endpoints.map(endpoint => (
                                <EndpointField key={endpoint.key} endpoint={endpoint} url={baseUrl + endpoint.path} />
                            ))}
                        </CardContent>
                    </Card>
                ))
            )}
        </div>
    );
}
