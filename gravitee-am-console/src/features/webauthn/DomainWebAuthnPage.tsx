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
    CardHeader,
    CardTitle,
    Field,
    FieldDescription,
    FieldError,
    FieldLabel,
    Input,
    Skeleton,
    toast,
} from '@gravitee/graphene-core';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { type FormEvent, useState } from 'react';
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
import { type Certificate, CertificatesCard } from './CertificatesCard';
import { OptionSelect } from './OptionSelect';

const FORM_ID = 'domain-webauthn-form';

type Settings = { -readonly [K in keyof WebAuthnSettings]: WebAuthnSettings[K] };

type FormState = {
    readonly settings: Omit<Settings, 'certificates'>;
    readonly certificates: readonly Certificate[];
};

const USER_VERIFICATIONS = ['required', 'preferred', 'discouraged'];
const AUTHENTICATOR_ATTACHMENTS = ['cross_platform', 'platform'];
const ATTESTATION_PREFERENCES = ['none', 'indirect', 'direct'];

/** The origin and the RP ID default to the access URL of the domain. */
function fromDomain(domain: Domain, baseUrl: string | undefined): FormState {
    const { certificates = {}, ...settings }: Settings = { ...domain.webAuthnSettings };
    if (URL.canParse(settings.origin || baseUrl || '')) {
        const url = new URL(settings.origin || baseUrl || '');
        settings.origin = url.origin;
        settings.relyingPartyId = settings.relyingPartyId || url.hostname;
    }
    return { settings, certificates: Object.entries(certificates).map(([name, value]) => ({ name, value })) };
}

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
        return <ErrorPanel description="Could not load the domain entrypoints." error={entrypoints.error} />;
    }
    if (!entrypoints.data) {
        return <Skeleton className="h-32 w-full" />;
    }
    const entrypoint = domainEntrypoint(entrypoints.data);
    const baseUrl = entrypoint ? accessUrl(entrypoint.url, domain.path ?? '', domain.vhostMode ?? false, domain.vhosts ?? []) : undefined;
    return <WebAuthnForm domain={domain} baseUrl={baseUrl} />;
}

interface WebAuthnFormProps {
    readonly domain: Domain;
    readonly baseUrl: string | undefined;
}

function WebAuthnForm({ domain, baseUrl }: WebAuthnFormProps) {
    const { ref } = useCurrentDomain();
    const queryClient = useQueryClient();
    const canEdit = usePermissions().has('domain_settings_update');
    const [form, setForm] = useState<FormState>(() => fromDomain(domain, baseUrl));
    // A new key drops the certificate row that is being edited.
    const [certificatesKey, setCertificatesKey] = useState(0);

    const reset = () => {
        setForm(fromDomain(domain, baseUrl));
        setCertificatesKey(key => key + 1);
    };

    const [syncedDomain, setSyncedDomain] = useState(domain);
    if (syncedDomain !== domain) {
        setSyncedDomain(domain);
        reset();
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
        onError: error => toast.error(`Could not update the WebAuthn configuration. ${error.message}`),
    });

    const { settings } = form;
    const dirty = JSON.stringify(form) !== JSON.stringify(fromDomain(domain, baseUrl));
    const set = (patch: Partial<Settings>) => setForm(current => ({ ...current, settings: { ...current.settings, ...patch } }));

    const originError = !settings.origin
        ? 'An origin is required.'
        : /^https?:\/\/.+$/.test(settings.origin)
          ? undefined
          : 'The origin must start with http:// or https://.';
    const maxAge = settings.enforceAuthenticatorIntegrityMaxAge;
    const maxAgeError = typeof maxAge === 'number' && maxAge < 0 ? 'Enter a number of seconds, 0 or more.' : undefined;
    const invalid = !!originError || !!maxAgeError;

    const submit = (event: FormEvent) => {
        event.preventDefault();
        if (!invalid) {
            save.mutate();
        }
    };

    return (
        <div className="flex flex-col gap-6">
            <PageHeader title="WebAuthn" description="WebAuthn configuration for the passwordless feature." />

            <form id={FORM_ID} className="flex flex-col gap-6" onSubmit={submit}>
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
                                aria-describedby={originError ? 'webauthn-origin-error' : undefined}
                                onChange={event => set({ origin: event.target.value })}
                            />
                            {originError ? (
                                <FieldError id="webauthn-origin-error">{originError}</FieldError>
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
                        />
                        <Field data-invalid={!!maxAgeError}>
                            <FieldLabel htmlFor="webauthn-integrity-age">Max age of last check (seconds)</FieldLabel>
                            <Input
                                id="webauthn-integrity-age"
                                type="number"
                                min={0}
                                value={maxAge ?? ''}
                                disabled={!canEdit}
                                aria-invalid={!!maxAgeError}
                                aria-describedby={maxAgeError ? 'webauthn-integrity-age-error' : undefined}
                                onChange={event =>
                                    set({
                                        enforceAuthenticatorIntegrityMaxAge:
                                            event.target.value === '' ? undefined : Number(event.target.value),
                                    })
                                }
                            />
                            {maxAgeError ? (
                                <FieldError id="webauthn-integrity-age-error">{maxAgeError}</FieldError>
                            ) : (
                                <FieldDescription>Time allowed since the user&apos;s authenticator was last checked.</FieldDescription>
                            )}
                        </Field>
                    </CardContent>
                </Card>
            </form>

            <CertificatesCard
                key={certificatesKey}
                certificates={form.certificates}
                canEdit={canEdit}
                onChange={certificates => setForm(current => ({ ...current, certificates }))}
            />

            {canEdit && <SaveBar formId={FORM_ID} dirty={dirty} saving={save.isPending} invalid={invalid} onDiscard={reset} />}
        </div>
    );
}
