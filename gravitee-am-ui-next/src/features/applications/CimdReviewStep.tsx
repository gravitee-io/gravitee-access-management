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
    Badge,
    Button,
    Card,
    CardContent,
    CardDescription,
    CardHeader,
    CardTitle,
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle,
    Field,
    FieldDescription,
    FieldLabel,
    Input,
} from '@gravitee/graphene-core';
import { Code2Icon } from '@gravitee/graphene-core/icons';
import { type ReactNode, useState } from 'react';
import type { CimdPreview } from '../../lib/api/applications';

interface CimdReviewStepProps {
    readonly preview: CimdPreview;
    readonly clientName: string;
    readonly onClientNameChange: (name: string) => void;
}

const list = (values: readonly string[] | undefined): ReactNode =>
    values?.length ? (
        <span className="flex flex-wrap gap-1">
            {values.map(value => (
                <Badge key={value} variant="outline" className="font-mono">
                    {value}
                </Badge>
            ))}
        </span>
    ) : (
        '—'
    );

const link = (href: string): ReactNode => (
    <a href={href} target="_blank" rel="noopener noreferrer" className="font-mono hover:underline">
        {href}
    </a>
);

const joined = (values: (string | false | undefined)[]): string => values.filter(Boolean).join(', ');

/** The Angular `step3-cimd-confirm`: the metadata the Management API parsed from the document, before the create. */
export function CimdReviewStep({ preview, clientName, onClientNameChange }: CimdReviewStepProps) {
    const [jsonOpen, setJsonOpen] = useState(false);
    const [copied, setCopied] = useState(false);
    const meta = preview.metadata ?? {};
    const json = JSON.stringify(meta, null, 2);
    const keys = (meta.jwks?.keys ?? []).filter(key => key && typeof key === 'object');
    const mtls = joined([
        meta.tls_client_auth_subject_dn && `DN: ${meta.tls_client_auth_subject_dn}`,
        meta.tls_client_auth_san_dns && `SAN dns: ${meta.tls_client_auth_san_dns}`,
        meta.tls_client_auth_san_uri && `SAN uri: ${meta.tls_client_auth_san_uri}`,
        meta.tls_client_auth_san_ip && `SAN ip: ${meta.tls_client_auth_san_ip}`,
        meta.tls_client_auth_san_email && `SAN email: ${meta.tls_client_auth_san_email}`,
        meta.tls_client_certificate_bound_access_tokens && 'Certificate-bound access tokens',
    ]);
    const ciba = joined([
        meta.backchannel_token_delivery_mode && `delivery mode: ${meta.backchannel_token_delivery_mode}`,
        meta.backchannel_authentication_request_signing_alg && `request signing: ${meta.backchannel_authentication_request_signing_alg}`,
        meta.backchannel_client_notification_endpoint && `notification endpoint: ${meta.backchannel_client_notification_endpoint}`,
        typeof meta.backchannel_user_code_parameter === 'boolean' && `user_code parameter: ${meta.backchannel_user_code_parameter}`,
    ]);
    // The rows of the Angular template, in its order. A row with an undefined value is hidden.
    const rows: [string, ReactNode][] = [
        ['Redirect URIs', list(meta.redirect_uris)],
        ['Grant types', list(meta.grant_types)],
        ['Response types', list(meta.response_types)],
        ['Scopes', list(meta.scope?.split(/\s+/).filter(Boolean))],
        [
            'Token endpoint auth method',
            <span key="auth" className="font-mono">
                {meta.token_endpoint_auth_method || 'none'}
            </span>,
        ],
        ['Logo URI', meta.logo_uri && link(meta.logo_uri)],
        ['JWKS URI', meta.jwks_uri && link(meta.jwks_uri)],
        [
            'JWKS (inline)',
            preview.hasInlineJwks &&
                (keys.length
                    ? list(
                          keys.map(key =>
                              joined([
                                  key.kid && `kid: ${key.kid}`,
                                  key.kty && `kty: ${key.kty}`,
                                  key.alg && `alg: ${key.alg}`,
                                  key.use && `use: ${key.use}`,
                              ]),
                          ),
                      )
                    : 'Inline keys provided in document'),
        ],
        ['Application type', meta.application_type],
        ['Post-logout redirect URIs', meta.post_logout_redirect_uris?.length ? list(meta.post_logout_redirect_uris) : undefined],
        ['Contacts', meta.contacts?.length ? list(meta.contacts) : undefined],
        ['Client URI', meta.client_uri && link(meta.client_uri)],
        ['Policy URI', meta.policy_uri && link(meta.policy_uri)],
        ['Terms of service URI', meta.tos_uri && link(meta.tos_uri)],
        ['Request URIs', meta.request_uris?.length ? list(meta.request_uris) : undefined],
        [
            'Software',
            (meta.software_id || meta.software_version) &&
                `${meta.software_id || '—'}${meta.software_version ? ` @ ${meta.software_version}` : ''}`,
        ],
        ['Software statement', meta.software_statement && <span className="font-mono break-all">{meta.software_statement}</span>],
        ['Subject type', meta.subject_type],
        ['Sector identifier URI', meta.sector_identifier_uri && link(meta.sector_identifier_uri)],
        ['ID token signing alg', meta.id_token_signed_response_alg],
        ['mTLS', mtls || undefined],
        ['CIBA backchannel', ciba || undefined],
    ];

    return (
        <Card>
            <CardHeader className="flex flex-row items-start justify-between gap-4">
                <div className="space-y-1.5">
                    <CardTitle>Review CIMD metadata</CardTitle>
                    <CardDescription style={{ overflowWrap: 'anywhere' }}>
                        Fields below were parsed from the CIMD document at {preview.url}.
                    </CardDescription>
                </div>
                {Object.keys(meta).length > 0 && (
                    <Button variant="outline" size="sm" onClick={() => setJsonOpen(true)}>
                        <Code2Icon aria-hidden />
                        View metadata JSON
                    </Button>
                )}
            </CardHeader>
            <CardContent className="grid gap-5">
                <dl className="grid gap-3 text-sm sm:grid-cols-2">
                    <div>
                        <dt className="text-muted-foreground">Client ID</dt>
                        <dd className="font-mono" style={{ overflowWrap: 'anywhere' }}>
                            {preview.url}
                        </dd>
                    </div>
                    {!preview.missing?.clientName && (
                        <div>
                            <dt className="text-muted-foreground">Client name</dt>
                            <dd className="font-medium">{meta.client_name}</dd>
                        </div>
                    )}
                    {rows
                        .filter(([, value]) => value !== undefined && value !== false && value !== '')
                        .map(([label, value]) => (
                            <div key={label}>
                                <dt className="text-muted-foreground">{label}</dt>
                                <dd style={{ overflowWrap: 'anywhere' }}>{value}</dd>
                            </div>
                        ))}
                </dl>
                {preview.missing?.clientName && (
                    <Field>
                        <FieldLabel htmlFor="cimd-client-name">Application name</FieldLabel>
                        <Input
                            id="cimd-client-name"
                            placeholder="Enter a name for the application"
                            value={clientName}
                            onChange={event => onClientNameChange(event.target.value)}
                        />
                        <FieldDescription>The document has no client_name, so give the application one.</FieldDescription>
                    </Field>
                )}
            </CardContent>

            <Dialog open={jsonOpen} onOpenChange={setJsonOpen}>
                <DialogContent className="sm:max-w-3xl">
                    <DialogHeader>
                        <DialogTitle>Metadata document</DialogTitle>
                        <DialogDescription style={{ overflowWrap: 'anywhere' }}>Source: {link(preview.url)}</DialogDescription>
                    </DialogHeader>
                    <pre className="max-h-[60vh] overflow-auto rounded-md bg-muted p-3 font-mono text-xs">{json}</pre>
                    <DialogFooter>
                        <Button
                            variant="outline"
                            onClick={() =>
                                void navigator.clipboard?.writeText(json).then(
                                    () => {
                                        setCopied(true);
                                        setTimeout(() => setCopied(false), 1500);
                                    },
                                    () => undefined,
                                )
                            }
                        >
                            {copied ? 'Copied!' : 'Copy JSON'}
                        </Button>
                        <Button onClick={() => setJsonOpen(false)}>Close</Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>
        </Card>
    );
}
