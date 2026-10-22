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
    Button,
    Card,
    CardContent,
    CardDescription,
    CardHeader,
    CardTitle,
    Field,
    FieldDescription,
    FieldError,
    FieldLabel,
    Input,
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
    Skeleton,
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
    toast,
} from '@gravitee/graphene-core';
import { CheckIcon, PencilIcon, PlusIcon, Trash2Icon, XIcon } from '@gravitee/graphene-core/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { ErrorPanel } from '../../app/components/ErrorPanel';
import { PageHeader } from '../../app/components/PageHeader';
import { SaveBar } from '../../app/components/SaveBar';
import { SettingToggle } from '../../app/components/SettingToggle';
import {
    type Domain,
    type WebAuthnSettings,
    accessUrl,
    domainEntrypoint,
    listDomainEntrypoints,
    patchDomainWebAuthnSettings,
} from '../../lib/api/domains';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { useCurrentDomain } from '../../lib/session/domain';
import { usePermissions } from '../../lib/session/permissions';

type Settings = { -readonly [K in keyof WebAuthnSettings]: WebAuthnSettings[K] };

interface Certificate {
    readonly name: string;
    readonly value: string;
}

interface FormState {
    readonly settings: Omit<Settings, 'certificates'>;
    readonly certificates: readonly Certificate[];
}

const UNSPECIFIED = 'unspecified';
const USER_VERIFICATIONS = ['required', 'preferred', 'discouraged'];
const AUTHENTICATOR_ATTACHMENTS = ['cross_platform', 'platform'];
const ATTESTATION_PREFERENCES = ['none', 'indirect', 'direct'];
const ATTESTATION_NAMES = ['none', 'u2f', 'packed', 'android-key', 'android-safetynet', 'tpm', 'apple', 'mds'];

/** The Angular `ngOnInit`: the origin and the RP ID default to the domain's access URL. */
function fromDomain(domain: Domain, baseUrl: string | undefined): FormState {
    const { certificates = {}, ...settings }: Settings = { ...domain.webAuthnSettings };
    try {
        const url = new URL(settings.origin || baseUrl || '');
        settings.origin = url.origin;
        settings.relyingPartyId = settings.relyingPartyId || url.hostname;
    } catch {
        // No stored origin and no entrypoint: the field shows the origin as missing.
    }
    return { settings, certificates: Object.entries(certificates).map(([name, value]) => ({ name, value })) };
}

function OptionSelect({
    id,
    label,
    hint,
    value,
    options,
    disabled,
    onChange,
}: Readonly<{
    id: string;
    label: string;
    hint: string;
    value: string | undefined;
    options: string[];
    disabled: boolean;
    onChange: (value: string | undefined) => void;
}>) {
    return (
        <Field>
            <FieldLabel htmlFor={id}>{label}</FieldLabel>
            <Select
                value={value ?? UNSPECIFIED}
                disabled={disabled}
                // The Angular "unspecified" option has no value, so the key is left out of the saved settings.
                onValueChange={next => onChange(next === UNSPECIFIED ? undefined : (next ?? undefined))}
            >
                <SelectTrigger id={id} className="w-full">
                    <SelectValue />
                </SelectTrigger>
                <SelectContent>
                    {[UNSPECIFIED, ...options].map(option => (
                        <SelectItem key={option} value={option}>
                            {option}
                        </SelectItem>
                    ))}
                </SelectContent>
            </Select>
            <FieldDescription>{hint}</FieldDescription>
        </Field>
    );
}

/**
 * The Angular `DomainSettingsWebAuthnComponent` (Settings › WebAuthn): the relying party and ceremony options for
 * passwordless sign-in, plus the attestation root certificates. Saved with `DomainService.patchWebAuthnSettings`.
 */
export function DomainWebAuthnPage() {
    const { domain, ref, basePath, domainsPath } = useCurrentDomain();
    const entrypoints = useQuery({ queryKey: ['domain-entrypoints', ref], queryFn: () => listDomainEntrypoints(ref) });
    useBreadcrumbs([
        { label: 'Domains', to: domainsPath },
        { label: domain.name, to: `${basePath}/dashboard` },
        { label: 'Settings', to: `${basePath}/settings/general` },
        { label: 'WebAuthn' },
    ]);

    if (entrypoints.error) {
        return <ErrorPanel description="Could not load the domain's entrypoints." error={entrypoints.error} />;
    }
    if (!entrypoints.data) {
        return <Skeleton className="h-32 w-full" />;
    }
    const entrypoint = domainEntrypoint(entrypoints.data);
    const baseUrl = entrypoint ? accessUrl(entrypoint.url, domain.path ?? '', domain.vhostMode ?? false, domain.vhosts ?? []) : undefined;
    return <WebAuthnForm domain={domain} baseUrl={baseUrl} />;
}

function WebAuthnForm({ domain, baseUrl }: Readonly<{ domain: Domain; baseUrl: string | undefined }>) {
    const { ref } = useCurrentDomain();
    const queryClient = useQueryClient();
    const canEdit = usePermissions().has('domain_settings_update');
    const [form, setForm] = useState<FormState>(() => fromDomain(domain, baseUrl));
    const [attestation, setAttestation] = useState<Certificate>({ name: '', value: '' });
    const [editing, setEditing] = useState<{ readonly index: number } & Certificate>();

    // A save or a refetch gives a new domain object. The form then starts again from it.
    const [syncedDomain, setSyncedDomain] = useState(domain);
    if (syncedDomain !== domain) {
        setSyncedDomain(domain);
        setForm(fromDomain(domain, baseUrl));
        setEditing(undefined);
    }

    const save = useMutation({
        mutationFn: () =>
            patchDomainWebAuthnSettings(ref, {
                ...form.settings,
                certificates: Object.fromEntries(form.certificates.map(({ name, value }) => [name, value])),
            }),
        onSuccess: updated => {
            queryClient.setQueryData(['domain', ref.organizationId, ref.environmentId, updated.hrid], updated);
            void queryClient.invalidateQueries({ queryKey: ['domains'] });
            toast.success('WebAuthn configuration updated');
        },
        onError: error => toast.error(error.message || 'Unable to update the WebAuthn configuration'),
    });

    const { settings, certificates } = form;
    const dirty = JSON.stringify(form) !== JSON.stringify(fromDomain(domain, baseUrl));
    const set = (patch: Partial<Settings>) => setForm(current => ({ ...current, settings: { ...current.settings, ...patch } }));
    const setCertificates = (next: readonly Certificate[]) => setForm(current => ({ ...current, certificates: next }));
    const exists = (name: string) => certificates.some(certificate => certificate.name === name);

    const originError = !settings.origin
        ? 'An origin is required.'
        : /^https?:\/\/.+$/.test(settings.origin)
          ? undefined
          : 'The origin must start with http:// or https://.';
    const maxAge = settings.enforceAuthenticatorIntegrityMaxAge;
    const maxAgeError = typeof maxAge === 'number' && maxAge < 0 ? 'Enter a number of seconds, 0 or more.' : undefined;

    const addCertificate = () => {
        if (exists(attestation.name)) {
            toast.error(`Error : metadata "${attestation.name}" already exists`);
            return;
        }
        setCertificates([...certificates, attestation]);
        setAttestation({ name: '', value: '' });
    };

    const updateCertificate = () => {
        if (!editing?.name || !editing.value) {
            return;
        }
        if (editing.name !== certificates[editing.index].name && exists(editing.name)) {
            toast.error(`Error : attestation "${editing.name}" already exists`);
            return;
        }
        setCertificates(
            certificates.map((certificate, i) => (i === editing.index ? { name: editing.name, value: editing.value } : certificate)),
        );
        setEditing(undefined);
    };

    return (
        <div className="flex flex-col gap-6">
            <PageHeader
                title="WebAuthn"
                description="How the domain registers and checks passwordless authenticators (WebAuthn / FIDO2)."
            />

            <Card>
                <CardHeader>
                    <CardTitle className="text-base">Relying party</CardTitle>
                </CardHeader>
                <CardContent className="grid gap-5">
                    <Field data-invalid={!!originError}>
                        <FieldLabel htmlFor="webauthn-origin">Origin</FieldLabel>
                        <Input
                            id="webauthn-origin"
                            value={settings.origin ?? ''}
                            disabled={!canEdit}
                            aria-invalid={!!originError}
                            onChange={event => set({ origin: event.target.value })}
                        />
                        {originError ? (
                            <FieldError>{originError}</FieldError>
                        ) : (
                            <FieldDescription>
                                Must match window.location.origin as the browser evaluates it during registration and authentication.
                            </FieldDescription>
                        )}
                    </Field>
                    <Field>
                        <FieldLabel htmlFor="webauthn-rp-id">Relying party ID</FieldLabel>
                        <Input
                            id="webauthn-rp-id"
                            value={settings.relyingPartyId ?? ''}
                            disabled={!canEdit}
                            onChange={event => set({ relyingPartyId: event.target.value })}
                        />
                        <FieldDescription>Optional. Defaults to the effective domain of the caller&apos;s origin.</FieldDescription>
                    </Field>
                    <Field>
                        <FieldLabel htmlFor="webauthn-rp-name">Relying party name</FieldLabel>
                        <Input
                            id="webauthn-rp-name"
                            value={settings.relyingPartyName ?? ''}
                            disabled={!canEdit}
                            onChange={event => set({ relyingPartyName: event.target.value })}
                        />
                        <FieldDescription>Optional. Name shown to users by their authenticator.</FieldDescription>
                    </Field>
                </CardContent>
            </Card>

            <Card>
                <CardHeader>
                    <CardTitle className="text-base">Ceremonies</CardTitle>
                </CardHeader>
                <CardContent className="grid gap-5">
                    <OptionSelect
                        id="webauthn-uv"
                        label="User verification"
                        hint="Required fails the operation unless the authenticator verified the user (PIN, biometrics). Preferred asks for it without failing. Discouraged skips it."
                        value={settings.userVerification}
                        options={USER_VERIFICATIONS}
                        disabled={!canEdit}
                        onChange={userVerification => set({ userVerification })}
                    />
                    <OptionSelect
                        id="webauthn-attachment"
                        label="Authenticator attachment"
                        hint="Unspecified lets the browser offer both the device and a security key; platform offers only the device, cross_platform only a security key."
                        value={settings.authenticatorAttachment}
                        options={AUTHENTICATOR_ATTACHMENTS}
                        disabled={!canEdit}
                        onChange={authenticatorAttachment => set({ authenticatorAttachment })}
                    />
                    <OptionSelect
                        id="webauthn-attestation"
                        label="Attestation conveyance preference"
                        hint="None: not interested in attestation. Indirect: prefers a verifiable attestation statement. Direct: wants the statement as the authenticator produced it."
                        value={settings.attestationConveyancePreference}
                        options={ATTESTATION_PREFERENCES}
                        disabled={!canEdit}
                        onChange={attestationConveyancePreference => set({ attestationConveyancePreference })}
                    />
                    <div>
                        <SettingToggle
                            id="webauthn-resident-key"
                            label="Require resident key"
                            description="The authenticator must create a client-side discoverable credential."
                            checked={settings.requireResidentKey === true}
                            disabled={!canEdit}
                            onChange={requireResidentKey => set({ requireResidentKey })}
                        />
                        <SettingToggle
                            id="webauthn-force-registration"
                            label="Force registration"
                            description="Accept the registration instead of failing the ceremony."
                            checked={settings.forceRegistration === true}
                            disabled={!canEdit}
                            onChange={forceRegistration => set({ forceRegistration })}
                        />
                        <SettingToggle
                            id="webauthn-integrity"
                            label="Force authenticator integrity"
                            description="Check the authenticator's statement against its certificate chain during authentication."
                            checked={settings.enforceAuthenticatorIntegrity === true}
                            disabled={!canEdit}
                            onChange={enforceAuthenticatorIntegrity => set({ enforceAuthenticatorIntegrity })}
                        >
                            <Field data-invalid={!!maxAgeError}>
                                <FieldLabel htmlFor="webauthn-integrity-age">Max age of last check (seconds)</FieldLabel>
                                <Input
                                    id="webauthn-integrity-age"
                                    type="number"
                                    min={0}
                                    value={maxAge ?? ''}
                                    disabled={!canEdit}
                                    aria-invalid={!!maxAgeError}
                                    onChange={event =>
                                        set({
                                            enforceAuthenticatorIntegrityMaxAge:
                                                event.target.value === '' ? undefined : Number(event.target.value),
                                        })
                                    }
                                />
                                {maxAgeError ? (
                                    <FieldError>{maxAgeError}</FieldError>
                                ) : (
                                    <FieldDescription>Time allowed since the user&apos;s authenticator was last checked.</FieldDescription>
                                )}
                            </Field>
                        </SettingToggle>
                    </div>
                </CardContent>
            </Card>

            <Card>
                <CardHeader>
                    <CardTitle className="text-base">Certificates</CardTitle>
                    <CardDescription>
                        Replace the default root certificates for an attestation type, to use a more recent one or a different cipher.
                    </CardDescription>
                </CardHeader>
                <CardContent className="flex flex-col gap-4">
                    {canEdit ? (
                        <div className="flex items-end gap-2">
                            <Field className="w-48">
                                <FieldLabel htmlFor="webauthn-cert-type">Type</FieldLabel>
                                <Select
                                    value={attestation.name}
                                    onValueChange={name => setAttestation(current => ({ ...current, name: name ?? '' }))}
                                >
                                    <SelectTrigger id="webauthn-cert-type" className="w-full">
                                        <SelectValue placeholder="Attestation" />
                                    </SelectTrigger>
                                    <SelectContent>
                                        {ATTESTATION_NAMES.map(name => (
                                            <SelectItem key={name} value={name}>
                                                {name}
                                            </SelectItem>
                                        ))}
                                    </SelectContent>
                                </Select>
                            </Field>
                            <Field className="flex-1">
                                <FieldLabel htmlFor="webauthn-cert-value">Certificate</FieldLabel>
                                <Input
                                    id="webauthn-cert-value"
                                    placeholder="PEM body, without the BEGIN / END lines"
                                    value={attestation.value}
                                    onChange={event => setAttestation(current => ({ ...current, value: event.target.value }))}
                                />
                            </Field>
                            <Button variant="outline" disabled={!attestation.name || !attestation.value} onClick={addCertificate}>
                                <PlusIcon aria-hidden />
                                Add
                            </Button>
                        </div>
                    ) : null}
                    {certificates.length === 0 ? (
                        <p className="text-sm text-muted-foreground">No certificates. The default root certificates are used.</p>
                    ) : (
                        <Table>
                            <TableHeader>
                                <TableRow>
                                    <TableHead>Attestation</TableHead>
                                    <TableHead>Certificate</TableHead>
                                    <TableHead>
                                        <span className="sr-only">Actions</span>
                                    </TableHead>
                                </TableRow>
                            </TableHeader>
                            <TableBody>
                                {certificates.map(({ name, value }, index) =>
                                    editing?.index === index ? (
                                        <TableRow key={name}>
                                            <TableCell>
                                                <Input
                                                    aria-label="Edited attestation name"
                                                    value={editing.name}
                                                    onChange={event => setEditing({ ...editing, name: event.target.value })}
                                                    onKeyDown={event => event.key === 'Enter' && updateCertificate()}
                                                />
                                            </TableCell>
                                            <TableCell>
                                                <Input
                                                    aria-label="Edited certificate"
                                                    value={editing.value}
                                                    onChange={event => setEditing({ ...editing, value: event.target.value })}
                                                    onKeyDown={event => event.key === 'Enter' && updateCertificate()}
                                                />
                                            </TableCell>
                                            <TableCell className="text-right whitespace-nowrap">
                                                <Button
                                                    variant="ghost"
                                                    size="icon-xs"
                                                    aria-label={`Apply the ${name} certificate changes`}
                                                    disabled={!editing.name || !editing.value}
                                                    onClick={updateCertificate}
                                                >
                                                    <CheckIcon aria-hidden />
                                                </Button>
                                                <Button
                                                    variant="ghost"
                                                    size="icon-xs"
                                                    aria-label={`Cancel the ${name} certificate changes`}
                                                    onClick={() => setEditing(undefined)}
                                                >
                                                    <XIcon aria-hidden />
                                                </Button>
                                            </TableCell>
                                        </TableRow>
                                    ) : (
                                        <TableRow key={name}>
                                            <TableCell className="font-medium">{name}</TableCell>
                                            <TableCell>
                                                <span className="block max-w-md truncate font-mono text-xs" title={value}>
                                                    {value}
                                                </span>
                                            </TableCell>
                                            <TableCell className="text-right whitespace-nowrap">
                                                {canEdit ? (
                                                    <>
                                                        <Button
                                                            variant="ghost"
                                                            size="icon-xs"
                                                            aria-label={`Edit ${name} certificate`}
                                                            onClick={() => setEditing({ index, name, value })}
                                                        >
                                                            <PencilIcon aria-hidden />
                                                        </Button>
                                                        <Button
                                                            variant="ghost"
                                                            size="icon-xs"
                                                            aria-label={`Remove ${name} certificate`}
                                                            onClick={() => {
                                                                setCertificates(certificates.filter((_, i) => i !== index));
                                                                setEditing(undefined);
                                                            }}
                                                        >
                                                            <Trash2Icon aria-hidden />
                                                        </Button>
                                                    </>
                                                ) : null}
                                            </TableCell>
                                        </TableRow>
                                    ),
                                )}
                            </TableBody>
                        </Table>
                    )}
                </CardContent>
            </Card>

            <SaveBar
                canEdit={canEdit}
                dirty={dirty}
                saving={save.isPending}
                invalid={!!originError || !!maxAgeError}
                onDiscard={() => {
                    setForm(fromDomain(domain, baseUrl));
                    setEditing(undefined);
                }}
                onSave={() => save.mutate()}
            />
        </div>
    );
}
