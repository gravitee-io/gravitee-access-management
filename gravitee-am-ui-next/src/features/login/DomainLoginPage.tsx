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
    toast,
} from '@gravitee/graphene-core';
import { TriangleAlertIcon } from '@gravitee/graphene-core/icons';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { PageHeader } from '../../app/components/PageHeader';
import { SaveBar } from '../../app/components/SaveBar';
import { type LoginSettings, patchDomainLoginSettings } from '../../lib/api/domains';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { useCurrentDomain } from '../../lib/session/domain';
import { usePermissions } from '../../lib/session/permissions';
import { SettingToggle } from './SettingToggle';

type Form = { -readonly [K in keyof LoginSettings]: LoginSettings[K] };

const HTTPS_URL = /^https:\/\/.+$/;

/**
 * The Angular `DomainSettingsLoginComponent` with `LoginSettingsComponent` (Settings › Login): the features of the
 * domain's login page. Saved with `DomainService.patchLoginSettings`: the stored settings with the changed fields, and
 * `inherited` forced to false.
 */
export function DomainLoginPage() {
    const { domain, ref, basePath, domainsPath } = useCurrentDomain();
    const queryClient = useQueryClient();
    const canEdit = usePermissions().has('domain_settings_update');
    const readonly = !canEdit;
    const [form, setForm] = useState<Form>(() => ({ ...domain.loginSettings }));
    useBreadcrumbs([
        { label: 'Domains', to: domainsPath },
        { label: domain.name, to: `${basePath}/dashboard` },
        { label: 'Settings', to: `${basePath}/settings/general` },
        { label: 'Login' },
    ]);

    // A save or a refetch gives a new domain object. The form then starts again from it.
    const [syncedDomain, setSyncedDomain] = useState(domain);
    if (syncedDomain !== domain) {
        setSyncedDomain(domain);
        setForm({ ...domain.loginSettings });
    }

    const save = useMutation({
        mutationFn: () => patchDomainLoginSettings(ref, { ...form, inherited: false }),
        onSuccess: updated => {
            queryClient.setQueryData(['domain', ref.organizationId, ref.environmentId, updated.hrid], updated);
            void queryClient.invalidateQueries({ queryKey: ['domains'] });
            toast.success('Login configuration updated');
        },
        onError: error => toast.error(error.message || 'Unable to update the login configuration'),
    });

    const dirty = JSON.stringify(form) !== JSON.stringify({ ...domain.loginSettings });
    const set = (patch: Partial<Form>) => setForm(current => ({ ...current, ...patch }));
    const passwordlessEnabled = form.passwordlessEnabled === true;
    const cbaEnabled = form.certificateBasedAuthEnabled === true;
    const enforcePassword = form.passwordlessEnforcePasswordEnabled === true;
    const maxAge = form.passwordlessEnforcePasswordMaxAge;

    let cbaUrlError: string | undefined;
    if (cbaEnabled && !form.certificateBasedAuthUrl) {
        cbaUrlError = 'Certificate based authentication URL is required when CBA is enabled.';
    } else if (cbaEnabled && !HTTPS_URL.test(form.certificateBasedAuthUrl ?? '')) {
        cbaUrlError = 'Provide a valid HTTPS URL (for example, https://cba.example.com/).';
    }
    const maxAgeError = enforcePassword && (typeof maxAge !== 'number' || maxAge < 0) ? 'Enter a number of seconds, 0 or more.' : undefined;

    return (
        <div className="flex flex-col gap-6">
            <PageHeader title="Login" description="Features available on the domain's login page." />

            <Card>
                <CardHeader>
                    <CardTitle className="text-base">Login</CardTitle>
                </CardHeader>
                <CardContent>
                    <SettingToggle
                        id="login-register"
                        label="User registration"
                        description="Let users create their own account. A link to the registration page appears on the login page."
                        checked={form.registerEnabled === true}
                        disabled={readonly}
                        onChange={registerEnabled => set({ registerEnabled })}
                    />
                    <SettingToggle
                        id="login-forgot"
                        label="Forgot password"
                        description="Self-service password reset, so users who forgot their password can ask for a new one."
                        checked={form.forgotPasswordEnabled === true}
                        disabled={readonly}
                        onChange={forgotPasswordEnabled => set({ forgotPasswordEnabled })}
                    />
                    <SettingToggle
                        id="login-reset-expired"
                        label="Force reset password on expiration"
                        description="After a successful login, users whose password has expired are asked to reset it."
                        checked={form.resetPasswordOnExpiration === true}
                        disabled={readonly}
                        onChange={resetPasswordOnExpiration => set({ resetPasswordOnExpiration })}
                    />
                    <SettingToggle
                        id="login-hide-form"
                        label="Hide login form"
                        description="Show only the external identity providers. With a single provider, the login page is skipped and users go straight to it."
                        checked={form.hideForm === true}
                        disabled={readonly || form.identifierFirstEnabled === true}
                        onChange={hideForm => set({ hideForm })}
                    />
                    <SettingToggle
                        id="login-identifier-first"
                        label="Identifier-first login"
                        description="Ask for the username first, then send the user to an internal provider's password step or to the external provider their domain maps to."
                        checked={form.identifierFirstEnabled === true}
                        disabled={readonly}
                        onChange={identifierFirstEnabled =>
                            set(identifierFirstEnabled ? { identifierFirstEnabled, hideForm: false } : { identifierFirstEnabled })
                        }
                    />
                </CardContent>
            </Card>

            <Card>
                <CardHeader>
                    <CardTitle className="text-base">Passwordless</CardTitle>
                    <CardDescription>Sign-in methods that do not ask for a password.</CardDescription>
                </CardHeader>
                <CardContent>
                    <p className="text-sm font-medium">WebAuthn</p>
                    <SettingToggle
                        id="login-passwordless"
                        label="Enable passwordless"
                        description="Passwordless authentication with WebAuthn / FIDO."
                        checked={passwordlessEnabled}
                        disabled={readonly}
                        onChange={enabled => set({ passwordlessEnabled: enabled })}
                    />
                    <SettingToggle
                        id="login-remember-device"
                        label="Passwordless device recognition"
                        description="Skip the login page and go straight to passwordless sign-in when the user has an enrolled device."
                        checked={form.passwordlessRememberDeviceEnabled === true}
                        disabled={readonly || !passwordlessEnabled}
                        onChange={passwordlessRememberDeviceEnabled => set({ passwordlessRememberDeviceEnabled })}
                    />
                    <SettingToggle
                        id="login-device-naming"
                        label="Passwordless device naming"
                        description="Ask users to name their devices so they can identify them later."
                        checked={form.passwordlessDeviceNamingEnabled === true}
                        disabled={readonly || !passwordlessEnabled}
                        onChange={passwordlessDeviceNamingEnabled => set({ passwordlessDeviceNamingEnabled })}
                    />

                    <p className="mt-2 text-sm font-medium">Certificate-based authentication</p>
                    <SettingToggle
                        id="login-cba"
                        label="Passwordless certificate-based authentication (CBA)"
                        description="Let users enroll and sign in with a digital certificate stored on their device."
                        checked={cbaEnabled}
                        disabled={readonly}
                        onChange={certificateBasedAuthEnabled => set({ certificateBasedAuthEnabled })}
                    >
                        <Alert>
                            <TriangleAlertIcon aria-hidden />
                            <AlertDescription>Install the CBA authenticator plugin on the gateway to use this feature.</AlertDescription>
                        </Alert>
                        <Field data-invalid={!!cbaUrlError}>
                            <FieldLabel htmlFor="login-cba-url">Certificate-based authentication base URL</FieldLabel>
                            <Input
                                id="login-cba-url"
                                type="url"
                                placeholder="https://cba.example.com/"
                                value={form.certificateBasedAuthUrl ?? ''}
                                disabled={readonly || !cbaEnabled}
                                aria-invalid={!!cbaUrlError}
                                onChange={event => set({ certificateBasedAuthUrl: event.target.value })}
                            />
                            {cbaUrlError ? (
                                <FieldError>{cbaUrlError}</FieldError>
                            ) : (
                                <FieldDescription>Base URL for certificate-based logins. Must start with https://.</FieldDescription>
                            )}
                        </Field>
                    </SettingToggle>

                    <p className="mt-2 text-sm font-medium">Magic link</p>
                    <SettingToggle
                        id="login-magic-link"
                        label="Passwordless magic link authentication"
                        description="Let users enroll and sign in with a link sent by email."
                        checked={form.magicLinkAuthEnabled === true}
                        disabled={readonly}
                        onChange={magicLinkAuthEnabled => set({ magicLinkAuthEnabled })}
                    />

                    <p className="mt-2 text-sm font-medium">Password usage</p>
                    <SettingToggle
                        id="login-enforce-password"
                        label="Enforce password usage"
                        description="Periodically make passwordless users sign in with their credentials (password or external provider)."
                        checked={enforcePassword}
                        disabled={readonly || (!passwordlessEnabled && !cbaEnabled)}
                        onChange={passwordlessEnforcePasswordEnabled => set({ passwordlessEnforcePasswordEnabled })}
                    >
                        <Field data-invalid={!!maxAgeError}>
                            <FieldLabel htmlFor="login-max-age">Max passwordless usage (seconds)</FieldLabel>
                            <Input
                                id="login-max-age"
                                type="number"
                                min={0}
                                value={maxAge ?? ''}
                                disabled={readonly || !enforcePassword}
                                aria-invalid={!!maxAgeError}
                                onChange={event =>
                                    set({
                                        passwordlessEnforcePasswordMaxAge:
                                            event.target.value === '' ? undefined : Number(event.target.value),
                                    })
                                }
                            />
                            {maxAgeError ? (
                                <FieldError>{maxAgeError}</FieldError>
                            ) : (
                                <FieldDescription>
                                    Time allowed since the user last signed in with credentials before they must do so again.
                                </FieldDescription>
                            )}
                        </Field>
                    </SettingToggle>
                </CardContent>
            </Card>

            <SaveBar
                canEdit={canEdit}
                dirty={dirty}
                saving={save.isPending}
                invalid={!!cbaUrlError || !!maxAgeError}
                onDiscard={() => setForm({ ...domain.loginSettings })}
                onSave={() => save.mutate()}
            />
        </div>
    );
}
