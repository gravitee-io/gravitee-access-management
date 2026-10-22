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
    Alert,
    AlertDescription,
    Button,
    Card,
    CardContent,
    CardDescription,
    CardHeader,
    CardTitle,
    Checkbox,
    Field,
    FieldDescription,
    FieldError,
    FieldLabel,
    Input,
    Label,
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
    Textarea,
    toast,
} from '@gravitee/graphene-core';
import { CheckIcon, PencilIcon, PlusIcon, TriangleAlertIcon, XIcon } from '@gravitee/graphene-core/icons';
import { useQuery } from '@tanstack/react-query';
import { type ReactNode, useState } from 'react';
import { SettingToggle } from '../../app/components/SettingToggle';
import { TagInput } from '../../app/components/TagInput';
import { listAllScopes, type Scope } from '../../lib/api/scopes';
import { useCurrentDomain } from '../../lib/session/domain';
import {
    AUD_SUB_MAPPING_MAX_LENGTH,
    type CrossAppAccessResourceServer,
    type CrossAppAccessSettings,
    DEFAULT_KEY_MATERIAL_SOURCE,
    DEFAULT_REFRESH_INTERVAL_SECONDS,
    deriveNameFromIssuer,
    isAbsoluteUri,
    ISSUER_MAX_LENGTH,
    isValidSpiffeTrustDomain,
    KEY_MATERIAL_SOURCE_OPTIONS,
    type KeyMaterialSource,
    keyMaterialErrors,
    type TrustDomain,
    type TrustDomainKeyMaterial,
    type UserBindingCriterion,
} from './trust-domain';

type ScopeMapping = { key: string; value: string };
type OutboundScopeMapping = { domainScope: string; externalScope: string };

type Model = {
    name: string;
    description: string;
    issuer: string;
    spiffeTrustDomain: string;
    spiffeEnabled: boolean;
    tokenExchangeEnabled: boolean;
    crossAppAccessEnabled: boolean;
    keySource: KeyMaterialSource;
    jwksUrl: string;
    jwkSetText: string;
    certificate: string;
    refreshIntervalSeconds: number | null;
    allowedAlgorithms: string[];
    scopeMappings: ScopeMapping[];
    userBindingEnabled: boolean;
    userBindingCriteria: UserBindingCriterion[];
    resourceServers: CrossAppAccessResourceServer[];
    /** The resource server row open for an edit, and what is typed in it. */
    editedResourceServer: { index: number; name: string; resource: string } | null;
    /** Angular sends the stored key material until the key material fields change. */
    keyMaterialChanged: boolean;
    audSubMapping: string;
    outboundScopeMappings: OutboundScopeMapping[];
};

/** `TrustDomainFormComponent.ngOnChanges` and `TrustDomainKeyMaterialComponent.ngOnChanges`. */
function toModel(trustDomain: TrustDomain, createMode: boolean): Model {
    const spiffeEnabled = !!trustDomain.spiffeTrustDomain;
    const crossAppAccess = trustDomain.crossAppAccess;
    return {
        name: trustDomain.name,
        description: trustDomain.description ?? '',
        issuer: trustDomain.issuer ?? '',
        spiffeTrustDomain: trustDomain.spiffeTrustDomain ?? '',
        spiffeEnabled,
        // A new trusted domain starts as a token exchange issuer, as in Angular.
        tokenExchangeEnabled: !!trustDomain.tokenExchangeEnabled || (createMode && !spiffeEnabled),
        crossAppAccessEnabled: !!crossAppAccess?.enabled,
        keySource: trustDomain.keyMaterial?.source ?? DEFAULT_KEY_MATERIAL_SOURCE,
        jwksUrl: trustDomain.keyMaterial?.jwksUrl ?? '',
        jwkSetText: trustDomain.keyMaterial?.jwkSet ? JSON.stringify(trustDomain.keyMaterial.jwkSet, null, 2) : '',
        certificate: trustDomain.keyMaterial?.certificate ?? '',
        refreshIntervalSeconds: trustDomain.refreshIntervalSeconds ?? DEFAULT_REFRESH_INTERVAL_SECONDS,
        allowedAlgorithms: [...(trustDomain.allowedAlgorithms ?? [])],
        scopeMappings: Object.entries(trustDomain.scopeMappings ?? {}).map(([key, value]) => ({ key, value })),
        userBindingEnabled: trustDomain.userBindingEnabled ?? false,
        userBindingCriteria: [...(trustDomain.userBindingCriteria ?? [])],
        resourceServers: [...(crossAppAccess?.resourceServers ?? [])],
        editedResourceServer: null,
        keyMaterialChanged: false,
        audSubMapping: crossAppAccess?.audSubMapping ?? '',
        outboundScopeMappings: Object.entries(crossAppAccess?.scopeMappings ?? {}).map(([domainScope, externalScope]) => ({
            domainScope,
            externalScope,
        })),
    };
}

/** `TrustDomainKeyMaterialComponent.parseJwkSet`. */
function parseJwkSet(text: string): { jwkSet?: TrustDomainKeyMaterial['jwkSet']; error?: string } {
    const trimmed = text.trim();
    if (!trimmed) {
        return {};
    }
    try {
        const parsed = JSON.parse(trimmed) as { keys?: unknown };
        return Array.isArray(parsed?.keys)
            ? { jwkSet: parsed as TrustDomainKeyMaterial['jwkSet'] }
            : { error: 'The JWK set must be an object with a "keys" array.' };
    } catch {
        return { error: 'The JWK set must be valid JSON.' };
    }
}

function keyMaterialOf(model: Model): TrustDomainKeyMaterial {
    switch (model.keySource) {
        case 'JWK_SET':
            return { source: 'JWK_SET', jwkSet: parseJwkSet(model.jwkSetText).jwkSet };
        case 'PEM':
            return { source: 'PEM', certificate: model.certificate.trim() };
        default:
            return { source: 'JWKS_URL', jwksUrl: model.jwksUrl.trim() };
    }
}

const isCrossAppAccessOnly = (model: Model) => model.crossAppAccessEnabled && !model.tokenExchangeEnabled && !model.spiffeEnabled;

/** `TrustDomainFormComponent.applyResourceServerEdit`: writes the open row back, keeping its id. */
function withResourceServerEdit(model: Model): Model {
    const edited = model.editedResourceServer;
    if (!edited || !edited.name.trim() || !edited.resource.trim()) {
        return model;
    }
    return {
        ...model,
        resourceServers: model.resourceServers.map((server, index) =>
            index === edited.index ? { ...server, name: edited.name.trim(), resource: edited.resource.trim() } : server,
        ),
        editedResourceServer: null,
    };
}

/** `TrustDomainFormComponent.getValidationErrors`. */
function validationErrors(model: Model): string[] {
    const errors: string[] = [];
    const oidc = model.tokenExchangeEnabled || model.crossAppAccessEnabled;
    if (!model.name.trim()) {
        errors.push('Name is required.');
    }
    if (!model.tokenExchangeEnabled && !model.crossAppAccessEnabled && !model.spiffeEnabled) {
        errors.push('Pick at least one usage: Token exchange, Cross App Access, SPIFFE, or several of them.');
    }
    if (model.spiffeEnabled) {
        const spiffeTrustDomain = model.spiffeTrustDomain.trim().toLowerCase();
        if (!spiffeTrustDomain) {
            errors.push('SPIFFE trust domain is required.');
        } else if (!isValidSpiffeTrustDomain(spiffeTrustDomain)) {
            errors.push('SPIFFE trust domain must be a DNS-style label: lowercase letters, digits, "." or "-".');
        }
    }
    if (oidc) {
        const issuer = model.issuer.trim();
        if (!issuer) {
            errors.push('Issuer URL is required.');
        } else if (issuer.length > ISSUER_MAX_LENGTH) {
            errors.push(`The issuer URL must be at most ${ISSUER_MAX_LENGTH} characters.`);
        } else if (model.crossAppAccessEnabled && !isAbsoluteUri(issuer)) {
            errors.push(`Issuer URL must be an absolute URI when Cross App Access is enabled: ${issuer}`);
        }
    }
    if (!isCrossAppAccessOnly(model)) {
        const jwkSetError = model.keySource === 'JWK_SET' ? parseJwkSet(model.jwkSetText).error : undefined;
        errors.push(...(jwkSetError ? [jwkSetError] : keyMaterialErrors(keyMaterialOf(model))));
        if (model.keySource === 'JWKS_URL' && !((model.refreshIntervalSeconds ?? 0) > 0)) {
            errors.push('Refresh interval must be a positive number of seconds.');
        }
    }
    if (model.tokenExchangeEnabled && model.userBindingEnabled && model.userBindingCriteria.length === 0) {
        errors.push('At least one user binding criterion (attribute and expression) is required when user binding is enabled.');
    }
    if (model.crossAppAccessEnabled) {
        if (model.editedResourceServer) {
            errors.push('Finish editing the resource server: its name and resource URI are both required.');
        }
        const servers = model.resourceServers;
        if (servers.length === 0) {
            errors.push('At least one resource server is required when Cross App Access is enabled.');
        }
        servers
            .filter(server => !isAbsoluteUri(server.resource))
            .forEach(server => errors.push(`Resource server resource must be an absolute URI: ${server.resource}`));
        const resources = servers.map(server => server.resource);
        resources
            .filter((resource, index) => resources.indexOf(resource) !== index)
            .forEach(resource => errors.push(`Resource server resource ${resource} is used more than once.`));
        if (model.audSubMapping.trim().length > AUD_SUB_MAPPING_MAX_LENGTH) {
            errors.push(`The aud_sub expression must be at most ${AUD_SUB_MAPPING_MAX_LENGTH} characters.`);
        }
    }
    return errors;
}

/** `TrustDomainFormComponent.payload`. */
function toPayload(model: Model, stored: TrustDomain): TrustDomain {
    const description = model.description.trim();
    const userBindingEnabled = model.tokenExchangeEnabled && model.userBindingEnabled;
    const scopeMappings = Object.fromEntries(model.scopeMappings.map(row => [row.key, row.value]));
    let crossAppAccess: CrossAppAccessSettings | undefined;
    if (!model.crossAppAccessEnabled) {
        // Switching the usage off keeps the stored block, so turning it back on restores it.
        crossAppAccess = stored.crossAppAccess ? { ...stored.crossAppAccess, enabled: false } : undefined;
    } else {
        const outbound = Object.fromEntries(model.outboundScopeMappings.map(row => [row.domainScope, row.externalScope]));
        crossAppAccess = {
            enabled: true,
            resourceServers: model.resourceServers.length ? model.resourceServers : undefined,
            audSubMapping: model.audSubMapping.trim() || undefined,
            scopeMappings: Object.keys(outbound).length ? outbound : undefined,
        };
    }
    const oidc = model.tokenExchangeEnabled || model.crossAppAccessEnabled;
    return {
        name: model.name.trim(),
        description: description || undefined,
        spiffeTrustDomain: model.spiffeEnabled ? model.spiffeTrustDomain.trim().toLowerCase() : undefined,
        issuer: oidc ? model.issuer.trim() : '',
        tokenExchangeEnabled: model.tokenExchangeEnabled,
        keyMaterial: model.keyMaterialChanged ? keyMaterialOf(model) : stored.keyMaterial,
        refreshIntervalSeconds: model.refreshIntervalSeconds ?? undefined,
        allowedAlgorithms: model.spiffeEnabled ? model.allowedAlgorithms : [],
        scopeMappings: model.tokenExchangeEnabled && Object.keys(scopeMappings).length ? scopeMappings : undefined,
        userBindingEnabled,
        userBindingCriteria: userBindingEnabled && model.userBindingCriteria.length ? model.userBindingCriteria : undefined,
        crossAppAccess,
    };
}

function SubHeading({ title, hint }: Readonly<{ title: string; hint?: ReactNode }>) {
    return (
        <div className="flex flex-col gap-0.5">
            <p className="text-sm font-medium">{title}</p>
            {hint ? <p className="text-sm text-muted-foreground">{hint}</p> : null}
        </div>
    );
}

type StagingRow = { name: string; cells: [ReactNode, ReactNode] };

/** The Angular staging row: two inputs and an Add button above the table of the rows added so far. */
function StagingTable({
    editable,
    headers,
    addLabel,
    rows,
    inputs,
    canAdd,
    onAdd,
    onRemove,
    rowActions,
    emptyText,
}: Readonly<{
    editable: boolean;
    headers: [string, string];
    addLabel: string;
    rows: StagingRow[];
    inputs: ReactNode;
    canAdd: boolean;
    onAdd: () => void;
    onRemove: (index: number) => void;
    /** Replaces the Remove button of a row. */
    rowActions?: (index: number) => ReactNode;
    emptyText: string;
}>) {
    return (
        <div className="flex flex-col gap-3">
            {editable ? (
                <div className="flex items-end gap-2">
                    {inputs}
                    <Button variant="outline" aria-label={addLabel} disabled={!canAdd} onClick={onAdd}>
                        <PlusIcon aria-hidden />
                        Add
                    </Button>
                </div>
            ) : null}
            {rows.length === 0 ? (
                <p className="text-sm text-muted-foreground">{emptyText}</p>
            ) : (
                <Table>
                    <TableHeader>
                        <TableRow>
                            <TableHead>{headers[0]}</TableHead>
                            <TableHead>{headers[1]}</TableHead>
                            <TableHead>
                                <span className="sr-only">Actions</span>
                            </TableHead>
                        </TableRow>
                    </TableHeader>
                    <TableBody>
                        {rows.map((row, index) => (
                            <TableRow key={index}>
                                <TableCell>{row.cells[0]}</TableCell>
                                <TableCell>{row.cells[1]}</TableCell>
                                <TableCell className="text-right">
                                    {editable
                                        ? (rowActions?.(index) ?? (
                                              <Button
                                                  variant="ghost"
                                                  size="icon-xs"
                                                  aria-label={`Remove ${row.name}`}
                                                  onClick={() => onRemove(index)}
                                              >
                                                  <XIcon aria-hidden />
                                              </Button>
                                          ))
                                        : null}
                                </TableCell>
                            </TableRow>
                        ))}
                    </TableBody>
                </Table>
            )}
        </div>
    );
}

/** A domain scope picker: a select when the domain has scopes, free text otherwise, as Angular. */
function ScopeInput({
    id,
    scopes,
    value,
    onChange,
}: Readonly<{ id: string; scopes: Scope[]; value: string; onChange: (value: string) => void }>) {
    return (
        <Field className="flex-1">
            <FieldLabel htmlFor={id}>Domain scope</FieldLabel>
            {scopes.length > 0 ? (
                <Select value={value} onValueChange={next => onChange(next ?? '')}>
                    <SelectTrigger id={id} className="w-full">
                        <SelectValue placeholder="Select a scope" />
                    </SelectTrigger>
                    <SelectContent>
                        {scopes.map(scope => (
                            <SelectItem key={scope.key} value={scope.key}>
                                {scope.name || scope.key}
                            </SelectItem>
                        ))}
                    </SelectContent>
                </Select>
            ) : (
                <Input id={id} placeholder="Domain scope" value={value} onChange={event => onChange(event.target.value)} />
            )}
        </Field>
    );
}

/**
 * The Angular `TrustDomainFormComponent` and `TrustDomainKeyMaterialComponent`, shared by the create and detail pages:
 * the usages (token exchange, Cross App Access, SPIFFE), the key material, and the settings of each usage. Every rule
 * Angular checks is listed above the submit button and blocks it. The page remounts the form to reset it.
 */
export function TrustDomainForm({
    trustDomain,
    createMode,
    editMode,
    submitLabel,
    cancel,
    onSubmit,
}: Readonly<{
    trustDomain: TrustDomain;
    createMode: boolean;
    editMode: boolean;
    submitLabel: string;
    cancel?: ReactNode;
    /** Resolves once saved. Rejects after the page reported the failure, so the edits stay. */
    onSubmit: (payload: TrustDomain) => Promise<unknown>;
}>) {
    const { ref } = useCurrentDomain();
    const [model, setModel] = useState<Model>(() => toModel(trustDomain, createMode));
    const [nameEdited, setNameEdited] = useState(!createMode);
    const [changed, setChanged] = useState(false);
    const [submitting, setSubmitting] = useState(false);
    const [scopeStaging, setScopeStaging] = useState<ScopeMapping>({ key: '', value: '' });
    const [bindingStaging, setBindingStaging] = useState<UserBindingCriterion>({ attribute: '', expression: '' });
    const [serverStaging, setServerStaging] = useState({ name: '', resource: '' });
    const [outboundStaging, setOutboundStaging] = useState<OutboundScopeMapping>({ domainScope: '', externalScope: '' });

    const oidc = model.tokenExchangeEnabled || model.crossAppAccessEnabled;
    const scopes = useQuery({ queryKey: ['all-scopes', ref], queryFn: () => listAllScopes(ref), enabled: oidc });
    const domainScopes = scopes.data ?? [];
    const scopeLabel = (key: string) => {
        const scope = domainScopes.find(other => other.key === key);
        return scope ? scope.name || scope.key : key;
    };

    const errors = validationErrors(model);
    const jwkSetError = model.keySource === 'JWK_SET' ? parseJwkSet(model.jwkSetText).error : undefined;
    const set = (patch: Partial<Model>) => {
        setModel(current => ({ ...current, ...patch }));
        setChanged(true);
    };
    const readonly = !editMode;
    const edited = model.editedResourceServer;
    const applyServerEdit = () => set(withResourceServerEdit(model));

    const submit = async () => {
        if (errors.length > 0) {
            return;
        }
        setSubmitting(true);
        try {
            await onSubmit(toPayload(model, trustDomain));
            setChanged(false);
        } catch {
            // The page showed the error. The edits stay, so the user can fix them and try again.
        } finally {
            setSubmitting(false);
        }
    };

    const availableInbound = domainScopes.filter(scope => !model.scopeMappings.some(row => row.value === scope.key));
    const availableOutbound = domainScopes.filter(scope => !model.outboundScopeMappings.some(row => row.domainScope === scope.key));

    return (
        <div className="flex flex-col gap-6">
            <Card>
                <CardHeader>
                    <CardTitle className="text-base">Trusted domain</CardTitle>
                </CardHeader>
                <CardContent className="grid gap-5">
                    <Field>
                        <FieldLabel htmlFor="td-name">Name</FieldLabel>
                        <Input
                            id="td-name"
                            placeholder="e.g. Acme Corp"
                            value={model.name}
                            disabled={readonly}
                            onChange={event => {
                                setNameEdited(true);
                                set({ name: event.target.value });
                            }}
                        />
                        <FieldDescription>
                            How this trusted domain is labelled in the console. Unique within this security domain.
                        </FieldDescription>
                    </Field>
                    <Field>
                        <FieldLabel htmlFor="td-description">Description</FieldLabel>
                        <Input
                            id="td-description"
                            value={model.description}
                            disabled={readonly}
                            onChange={event => set({ description: event.target.value })}
                        />
                    </Field>
                </CardContent>
            </Card>

            <Card>
                <CardHeader>
                    <CardTitle className="text-base">Usage</CardTitle>
                    <CardDescription>
                        What this trusted domain is used for. Pick as many as apply: one authority can serve several from the same key
                        material.
                    </CardDescription>
                </CardHeader>
                <CardContent className="flex flex-col gap-4">
                    {(
                        [
                            [
                                'tokenExchangeEnabled',
                                'Token exchange',
                                'Validates external subject and actor tokens during an RFC 8693 token exchange.',
                            ],
                            [
                                'crossAppAccessEnabled',
                                'Cross App Access',
                                'Mints an ID-JAG towards the resource servers this authority exposes.',
                            ],
                        ] as const
                    ).map(([key, label, hint]) => (
                        <div key={key} className="flex items-start gap-2">
                            <Checkbox
                                id={`td-${key}`}
                                checked={model[key]}
                                disabled={readonly}
                                onCheckedChange={checked => set({ [key]: checked === true })}
                            />
                            <div className="flex flex-col gap-0.5">
                                <Label htmlFor={`td-${key}`}>{label}</Label>
                                <p className="text-sm text-muted-foreground">{hint}</p>
                            </div>
                        </div>
                    ))}
                    {oidc ? (
                        <Field>
                            <FieldLabel htmlFor="td-issuer">Issuer URL</FieldLabel>
                            <Input
                                id="td-issuer"
                                placeholder="https://sso.acme.com"
                                value={model.issuer}
                                disabled={readonly}
                                onChange={event => {
                                    const issuer = event.target.value;
                                    set(nameEdited ? { issuer } : { issuer, name: deriveNameFromIssuer(issuer) });
                                }}
                            />
                            <FieldDescription>
                                The issuer identifier of this authority&apos;s authorization server: the expected &quot;iss&quot; of an
                                external JWT, and the &quot;aud&quot; of an ID-JAG minted towards it. Unique within this security domain.
                            </FieldDescription>
                        </Field>
                    ) : null}
                    <div className="flex items-start gap-2">
                        <Checkbox
                            id="td-spiffe"
                            checked={model.spiffeEnabled}
                            disabled={readonly}
                            onCheckedChange={checked => set({ spiffeEnabled: checked === true })}
                        />
                        <div className="flex flex-col gap-0.5">
                            <Label htmlFor="td-spiffe">SPIFFE</Label>
                            <p className="text-sm text-muted-foreground">
                                Validates JWT-SVIDs presented by workloads as client assertions.
                            </p>
                        </div>
                    </div>
                    {model.spiffeEnabled ? (
                        <Field>
                            <FieldLabel htmlFor="td-spiffe-domain">SPIFFE trust domain</FieldLabel>
                            <Input
                                id="td-spiffe-domain"
                                placeholder="e.g. prod.example or am.local"
                                value={model.spiffeTrustDomain}
                                disabled={readonly}
                                onChange={event => set({ spiffeTrustDomain: event.target.value })}
                            />
                            <FieldDescription>
                                Matched against the &quot;sub&quot; of a JWT-SVID, without the &quot;spiffe://&quot; scheme. Lowercased on
                                save.
                            </FieldDescription>
                        </Field>
                    ) : null}
                </CardContent>
            </Card>

            {!isCrossAppAccessOnly(model) ? (
                <Card>
                    <CardHeader>
                        <CardTitle className="text-base">Key material</CardTitle>
                    </CardHeader>
                    <CardContent className="grid gap-5">
                        <Field>
                            <FieldLabel htmlFor="td-key-source">Key source</FieldLabel>
                            <Select
                                value={model.keySource}
                                disabled={readonly}
                                onValueChange={keySource => set({ keySource: keySource as KeyMaterialSource, keyMaterialChanged: true })}
                            >
                                <SelectTrigger id="td-key-source" className="w-full">
                                    <SelectValue />
                                </SelectTrigger>
                                <SelectContent>
                                    {KEY_MATERIAL_SOURCE_OPTIONS.map(option => (
                                        <SelectItem key={option.value} value={option.value}>
                                            {option.label}
                                        </SelectItem>
                                    ))}
                                </SelectContent>
                            </Select>
                            <FieldDescription>Where this trusted domain&apos;s signing keys come from.</FieldDescription>
                        </Field>
                        {model.keySource === 'JWKS_URL' ? (
                            <>
                                <Field>
                                    <FieldLabel htmlFor="td-jwks-url">JWKS URL</FieldLabel>
                                    <Input
                                        id="td-jwks-url"
                                        type="url"
                                        placeholder="https://issuer.example/.well-known/jwks.json"
                                        value={model.jwksUrl}
                                        disabled={readonly}
                                        onChange={event => set({ jwksUrl: event.target.value, keyMaterialChanged: true })}
                                    />
                                    <FieldDescription>
                                        Endpoint serving the JWK set. Fetch limits and SSRF protection are set under Key retrieval.
                                    </FieldDescription>
                                </Field>
                                <Field>
                                    <FieldLabel htmlFor="td-refresh">Refresh interval (seconds)</FieldLabel>
                                    <Input
                                        id="td-refresh"
                                        type="number"
                                        min={1}
                                        className="w-48"
                                        value={model.refreshIntervalSeconds ?? ''}
                                        disabled={readonly}
                                        onChange={event =>
                                            set({ refreshIntervalSeconds: event.target.value === '' ? null : Number(event.target.value) })
                                        }
                                    />
                                    <FieldDescription>How often AM polls the JWKS endpoint.</FieldDescription>
                                </Field>
                            </>
                        ) : model.keySource === 'JWK_SET' ? (
                            <Field data-invalid={!!jwkSetError}>
                                <FieldLabel htmlFor="td-jwk-set">JWK set</FieldLabel>
                                <Textarea
                                    id="td-jwk-set"
                                    rows={6}
                                    className="font-mono text-xs"
                                    placeholder='{ "keys": [ ... ] }'
                                    value={model.jwkSetText}
                                    disabled={readonly}
                                    onChange={event => set({ jwkSetText: event.target.value, keyMaterialChanged: true })}
                                />
                                {jwkSetError ? (
                                    <FieldError>{jwkSetError}</FieldError>
                                ) : (
                                    <FieldDescription>Inline JWK set, supplied directly instead of fetched.</FieldDescription>
                                )}
                            </Field>
                        ) : (
                            <Field>
                                <FieldLabel htmlFor="td-pem">PEM certificate</FieldLabel>
                                <Textarea
                                    id="td-pem"
                                    rows={6}
                                    className="font-mono text-xs"
                                    placeholder={'-----BEGIN CERTIFICATE-----\n...\n-----END CERTIFICATE-----'}
                                    value={model.certificate}
                                    disabled={readonly}
                                    onChange={event => set({ certificate: event.target.value, keyMaterialChanged: true })}
                                />
                                <FieldDescription>PEM-encoded X.509 certificate holding the signing key.</FieldDescription>
                            </Field>
                        )}
                    </CardContent>
                </Card>
            ) : null}

            {model.spiffeEnabled ? (
                <Card>
                    <CardHeader>
                        <CardTitle className="text-base">Signing algorithms</CardTitle>
                        <CardDescription>
                            Override the domain default from the SPIFFE settings. Leave empty to inherit. none and HMAC variants are always
                            rejected.
                        </CardDescription>
                    </CardHeader>
                    <CardContent>
                        <Field>
                            <FieldLabel htmlFor="td-algorithm">Add algorithm</FieldLabel>
                            <TagInput
                                id="td-algorithm"
                                values={model.allowedAlgorithms}
                                placeholder="e.g. RS256"
                                disabled={readonly}
                                onChange={allowedAlgorithms => set({ allowedAlgorithms })}
                                onDuplicate={algorithm => toast.error(`Algorithm ${algorithm} is already listed`)}
                            />
                            {model.allowedAlgorithms.length === 0 ? (
                                <FieldDescription>No override: the domain default applies.</FieldDescription>
                            ) : null}
                        </Field>
                    </CardContent>
                </Card>
            ) : null}

            {model.tokenExchangeEnabled ? (
                <Card>
                    <CardHeader>
                        <CardTitle className="text-base">Token exchange</CardTitle>
                        <CardDescription>What AM accepts from this issuer during an RFC 8693 token exchange.</CardDescription>
                    </CardHeader>
                    <CardContent className="flex flex-col gap-6">
                        <div className="flex flex-col gap-3">
                            <SubHeading
                                title="Scope mappings"
                                hint="Map external scopes from this issuer to domain scopes. Unmapped scopes are dropped."
                            />
                            <StagingTable
                                editable={editMode}
                                headers={['External scope', 'Domain scope']}
                                addLabel="Add scope mapping"
                                rows={model.scopeMappings.map(row => ({
                                    name: row.key,
                                    cells: [
                                        <span key="key" className="font-mono text-sm">
                                            {row.key}
                                        </span>,
                                        scopeLabel(row.value),
                                    ],
                                }))}
                                inputs={
                                    <>
                                        <Field className="flex-1">
                                            <FieldLabel htmlFor="td-ext-scope">External scope</FieldLabel>
                                            <Input
                                                id="td-ext-scope"
                                                placeholder="External scope name"
                                                value={scopeStaging.key}
                                                onChange={event => setScopeStaging(staging => ({ ...staging, key: event.target.value }))}
                                            />
                                        </Field>
                                        <ScopeInput
                                            id="td-dom-scope"
                                            scopes={availableInbound}
                                            value={scopeStaging.value}
                                            onChange={value => setScopeStaging(staging => ({ ...staging, value }))}
                                        />
                                    </>
                                }
                                canAdd={
                                    !!scopeStaging.key.trim() &&
                                    !!scopeStaging.value.trim() &&
                                    !model.scopeMappings.some(
                                        row => row.key === scopeStaging.key.trim() && row.value === scopeStaging.value.trim(),
                                    )
                                }
                                onAdd={() => {
                                    set({
                                        scopeMappings: [
                                            ...model.scopeMappings,
                                            { key: scopeStaging.key.trim(), value: scopeStaging.value.trim() },
                                        ],
                                    });
                                    setScopeStaging({ key: '', value: '' });
                                }}
                                onRemove={index => set({ scopeMappings: model.scopeMappings.filter((_, other) => other !== index) })}
                                emptyText="No scope mappings."
                            />
                        </div>
                        <SettingToggle
                            id="td-user-binding"
                            label="Enable user binding"
                            description="The minted token carries the domain user's roles, groups and profile data instead of a synthetic user."
                            checked={model.userBindingEnabled}
                            disabled={readonly}
                            onChange={userBindingEnabled => set({ userBindingEnabled })}
                        >
                            {model.userBindingEnabled ? (
                                <div className="flex flex-col gap-3">
                                    <SubHeading
                                        title="User binding mappings"
                                        hint="Map user attributes to JWT claims to look the user up. Use claim names (email) or EL expressions ({#token['email']})."
                                    />
                                    <StagingTable
                                        editable={editMode}
                                        headers={['User attribute', 'Claim / expression']}
                                        addLabel="Add user binding mapping"
                                        rows={model.userBindingCriteria.map(criterion => ({
                                            name: criterion.attribute,
                                            cells: [
                                                <span key="attribute" className="font-mono text-sm">
                                                    {criterion.attribute}
                                                </span>,
                                                <span key="expression" className="font-mono text-sm">
                                                    {criterion.expression}
                                                </span>,
                                            ],
                                        }))}
                                        inputs={
                                            <>
                                                <Field className="flex-1">
                                                    <FieldLabel htmlFor="td-binding-attribute">User attribute</FieldLabel>
                                                    <Input
                                                        id="td-binding-attribute"
                                                        placeholder="e.g. userName, emails.value"
                                                        value={bindingStaging.attribute}
                                                        onChange={event =>
                                                            setBindingStaging(staging => ({ ...staging, attribute: event.target.value }))
                                                        }
                                                    />
                                                </Field>
                                                <Field className="flex-1">
                                                    <FieldLabel htmlFor="td-binding-expression">Claim / expression</FieldLabel>
                                                    <Input
                                                        id="td-binding-expression"
                                                        placeholder="email or {#token['email']}"
                                                        value={bindingStaging.expression}
                                                        onChange={event =>
                                                            setBindingStaging(staging => ({ ...staging, expression: event.target.value }))
                                                        }
                                                    />
                                                </Field>
                                            </>
                                        }
                                        canAdd={!!bindingStaging.attribute.trim() && !!bindingStaging.expression.trim()}
                                        onAdd={() => {
                                            const attribute = bindingStaging.attribute.trim();
                                            const expression = bindingStaging.expression.trim();
                                            if (
                                                !model.userBindingCriteria.some(
                                                    other => other.attribute === attribute && other.expression === expression,
                                                )
                                            ) {
                                                set({ userBindingCriteria: [...model.userBindingCriteria, { attribute, expression }] });
                                            }
                                            setBindingStaging({ attribute: '', expression: '' });
                                        }}
                                        onRemove={index =>
                                            set({ userBindingCriteria: model.userBindingCriteria.filter((_, other) => other !== index) })
                                        }
                                        emptyText="No user binding mappings."
                                    />
                                </div>
                            ) : null}
                        </SettingToggle>
                    </CardContent>
                </Card>
            ) : null}

            {model.crossAppAccessEnabled ? (
                <Card>
                    <CardHeader>
                        <CardTitle className="text-base">Cross App Access</CardTitle>
                        <CardDescription>
                            What AM issues towards this authority: an ID-JAG for the resource servers it exposes, presented to the
                            authorization server the issuer URL names.
                        </CardDescription>
                    </CardHeader>
                    <CardContent className="flex flex-col gap-6">
                        <div className="flex flex-col gap-3">
                            <SubHeading
                                title="Resource servers"
                                hint="The protected resources behind that authorization server, which AM may mint an ID-JAG towards."
                            />
                            <StagingTable
                                editable={editMode}
                                headers={['Name', 'Resource URI']}
                                addLabel="Add resource server"
                                rows={model.resourceServers.map((server, index) => ({
                                    name: server.name,
                                    cells:
                                        edited?.index === index
                                            ? [
                                                  <Input
                                                      key="name"
                                                      aria-label="Edited resource server name"
                                                      placeholder="Name"
                                                      value={edited.name}
                                                      onChange={event =>
                                                          set({ editedResourceServer: { ...edited, name: event.target.value } })
                                                      }
                                                      onKeyDown={event => serverEditKey(event.key)}
                                                  />,
                                                  <Input
                                                      key="resource"
                                                      aria-label="Edited resource URI"
                                                      placeholder="Resource URI"
                                                      value={edited.resource}
                                                      onChange={event =>
                                                          set({ editedResourceServer: { ...edited, resource: event.target.value } })
                                                      }
                                                      onKeyDown={event => serverEditKey(event.key)}
                                                  />,
                                              ]
                                            : [
                                                  server.name,
                                                  <span key="resource" className="font-mono text-sm">
                                                      {server.resource}
                                                  </span>,
                                              ],
                                }))}
                                rowActions={index =>
                                    edited?.index === index ? (
                                        <div className="flex justify-end gap-1">
                                            <Button
                                                variant="ghost"
                                                size="icon-xs"
                                                aria-label="Apply"
                                                disabled={!edited.name.trim() || !edited.resource.trim()}
                                                onClick={applyServerEdit}
                                            >
                                                <CheckIcon aria-hidden />
                                            </Button>
                                            <Button
                                                variant="ghost"
                                                size="icon-xs"
                                                aria-label="Cancel"
                                                onClick={() => set({ editedResourceServer: null })}
                                            >
                                                <XIcon aria-hidden />
                                            </Button>
                                        </div>
                                    ) : (
                                        <div className="flex justify-end gap-1">
                                            <Button
                                                variant="ghost"
                                                size="icon-xs"
                                                aria-label={`Edit ${model.resourceServers[index].name}`}
                                                onClick={() => set({ editedResourceServer: { index, ...model.resourceServers[index] } })}
                                            >
                                                <PencilIcon aria-hidden />
                                            </Button>
                                            <Button
                                                variant="ghost"
                                                size="icon-xs"
                                                aria-label={`Remove ${model.resourceServers[index].name}`}
                                                onClick={() => removeServer(index)}
                                            >
                                                <XIcon aria-hidden />
                                            </Button>
                                        </div>
                                    )
                                }
                                inputs={
                                    <>
                                        <Field className="flex-1">
                                            <FieldLabel htmlFor="td-server-name">Name</FieldLabel>
                                            <Input
                                                id="td-server-name"
                                                placeholder="e.g. Acme Calendar"
                                                value={serverStaging.name}
                                                onChange={event => setServerStaging(staging => ({ ...staging, name: event.target.value }))}
                                            />
                                        </Field>
                                        <Field className="flex-1">
                                            <FieldLabel htmlFor="td-server-resource">Resource URI</FieldLabel>
                                            <Input
                                                id="td-server-resource"
                                                placeholder="https://calendar.acme.com"
                                                value={serverStaging.resource}
                                                onChange={event =>
                                                    setServerStaging(staging => ({ ...staging, resource: event.target.value }))
                                                }
                                            />
                                        </Field>
                                    </>
                                }
                                canAdd={!!serverStaging.name.trim() && !!serverStaging.resource.trim()}
                                onAdd={() => {
                                    set({
                                        resourceServers: [
                                            ...model.resourceServers,
                                            { name: serverStaging.name.trim(), resource: serverStaging.resource.trim() },
                                        ],
                                    });
                                    setServerStaging({ name: '', resource: '' });
                                }}
                                onRemove={removeServer}
                                emptyText="No resource servers."
                            />
                        </div>
                        <Field>
                            <FieldLabel htmlFor="td-aud-sub">aud_sub expression</FieldLabel>
                            <Input
                                id="td-aud-sub"
                                className="font-mono"
                                placeholder="{#context.attributes['user'].email}"
                                value={model.audSubMapping}
                                disabled={readonly}
                                onChange={event => set({ audSubMapping: event.target.value })}
                            />
                            <FieldDescription>
                                Produces the &quot;aud_sub&quot; claim for every resource server of this authority, with the user profile at
                                context.attributes[&apos;user&apos;]. The claim is omitted when the expression yields nothing, and issuance
                                is refused when it fails.
                            </FieldDescription>
                        </Field>
                        <div className="flex flex-col gap-3">
                            <SubHeading
                                title="Scope mappings"
                                hint="Map domain scopes to the names this authority knows them by. A requested domain scope with no mapping is refused."
                            />
                            <StagingTable
                                editable={editMode}
                                headers={['Domain scope', 'External scope']}
                                addLabel="Add outbound scope mapping"
                                rows={model.outboundScopeMappings.map(row => ({
                                    name: row.domainScope,
                                    cells: [
                                        scopeLabel(row.domainScope),
                                        <span key="external" className="font-mono text-sm">
                                            {row.externalScope}
                                        </span>,
                                    ],
                                }))}
                                inputs={
                                    <>
                                        <ScopeInput
                                            id="td-out-dom-scope"
                                            scopes={availableOutbound}
                                            value={outboundStaging.domainScope}
                                            onChange={domainScope => setOutboundStaging(staging => ({ ...staging, domainScope }))}
                                        />
                                        <Field className="flex-1">
                                            <FieldLabel htmlFor="td-out-ext-scope">External scope</FieldLabel>
                                            <Input
                                                id="td-out-ext-scope"
                                                placeholder="Name on the far side"
                                                value={outboundStaging.externalScope}
                                                onChange={event =>
                                                    setOutboundStaging(staging => ({ ...staging, externalScope: event.target.value }))
                                                }
                                            />
                                        </Field>
                                    </>
                                }
                                canAdd={
                                    !!outboundStaging.domainScope.trim() &&
                                    !!outboundStaging.externalScope.trim() &&
                                    !model.outboundScopeMappings.some(row => row.domainScope === outboundStaging.domainScope.trim())
                                }
                                onAdd={() => {
                                    set({
                                        outboundScopeMappings: [
                                            ...model.outboundScopeMappings,
                                            {
                                                domainScope: outboundStaging.domainScope.trim(),
                                                externalScope: outboundStaging.externalScope.trim(),
                                            },
                                        ],
                                    });
                                    setOutboundStaging({ domainScope: '', externalScope: '' });
                                }}
                                onRemove={index =>
                                    set({ outboundScopeMappings: model.outboundScopeMappings.filter((_, other) => other !== index) })
                                }
                                emptyText="No scope mappings."
                            />
                        </div>
                    </CardContent>
                </Card>
            ) : null}

            {editMode && errors.length > 0 && (changed || !createMode) ? (
                <Alert>
                    <TriangleAlertIcon aria-hidden />
                    <AlertDescription>
                        <ul className="flex flex-col gap-1">
                            {errors.map((error, index) => (
                                <li key={index}>{error}</li>
                            ))}
                        </ul>
                    </AlertDescription>
                </Alert>
            ) : null}

            {editMode ? (
                <div className="flex items-center justify-end gap-2">
                    {cancel}
                    <Button disabled={!changed || errors.length > 0 || submitting} onClick={() => void submit()}>
                        {submitLabel}
                    </Button>
                </div>
            ) : null}
        </div>
    );

    function serverEditKey(key: string) {
        if (key === 'Enter') {
            applyServerEdit();
        } else if (key === 'Escape') {
            set({ editedResourceServer: null });
        }
    }

    /** `TrustDomainFormComponent.removeResourceServer`: an open row below the removed one moves up by one. */
    function removeServer(index: number) {
        const open = model.editedResourceServer;
        set({
            resourceServers: model.resourceServers.filter((_, other) => other !== index),
            editedResourceServer: !open || open.index === index ? null : open.index > index ? { ...open, index: open.index - 1 } : open,
        });
    }
}
