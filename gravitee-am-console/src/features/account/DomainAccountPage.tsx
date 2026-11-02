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
    FieldError,
    FieldLabel,
    Input,
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
    toast,
} from '@gravitee/graphene-core';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { type FormEvent, type ReactNode, useState } from 'react';
import { DurationInput } from '../../app/components/DurationInput';
import { PageHeader } from '../../app/components/PageHeader';
import { SaveBar } from '../../app/components/SaveBar';
import { SettingToggle } from '../../app/components/SettingToggle';
import { listBotDetections } from '../../lib/api/bot-detection';
import { patchDomainAccountSettings } from '../../lib/api/domains';
import { listUserIdentityProviders } from '../../lib/api/identity-providers';
import type { DurationDraft } from '../../lib/forms/duration';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { useCurrentDomain } from '../../lib/session/domain';
import { usePermissions } from '../../lib/session/permissions';
import { CustomFormFields } from './CustomFormFields';
import {
    DEFAULT_ACCOUNT_BLOCKED,
    DEFAULT_LOGIN_ATTEMPTS_RESET,
    DEFAULT_MAX_ATTEMPTS,
    DEFAULT_MFA_MAX_ATTEMPTS,
    DEFAULT_MFA_RESET,
    DEFAULT_REMEMBER_ME,
    type DurationKey,
    type FormErrors,
    type FormState,
    type Settings,
    fromAccountSettings,
    orDefault,
    toPayload,
    validate,
} from './account-settings';

const FORM_ID = 'domain-account-form';
// The "None" option leaves the key out of the saved settings.
const NONE = '__none__';

const numberOrUndefined = (raw: string) => (raw === '' ? undefined : Number(raw));

const errorId = (key: keyof FormErrors) => `account-${key}-error`;

interface HintProps {
    readonly errors: FormErrors;
    readonly name: keyof FormErrors;
    readonly children: ReactNode;
}

function Hint({ errors, name, children }: HintProps) {
    return errors[name] ? <FieldError id={errorId(name)}>{errors[name]}</FieldError> : <FieldDescription>{children}</FieldDescription>;
}

export function DomainAccountPage() {
    const { domain, ref, basePath, domainsPath } = useCurrentDomain();
    const queryClient = useQueryClient();
    const canEdit = usePermissions().hasAny('domain_settings_create', 'domain_settings_update');
    const providers = useQuery({ queryKey: ['user-identity-providers', ref], queryFn: () => listUserIdentityProviders(ref) });
    const botDetections = useQuery({ queryKey: ['bot-detections', ref], queryFn: () => listBotDetections(ref) });
    const [form, setForm] = useState<FormState>(() => fromAccountSettings(domain.accountSettings));
    useBreadcrumbs([
        { label: 'Domains', to: domainsPath },
        { label: domain.name, to: `${basePath}/dashboard` },
        { label: 'Settings', to: `${basePath}/settings/general` },
        { label: 'User Accounts' },
    ]);

    const [syncedDomain, setSyncedDomain] = useState(domain);
    if (syncedDomain !== domain) {
        setSyncedDomain(domain);
        setForm(fromAccountSettings(domain.accountSettings));
    }

    const save = useMutation({
        mutationFn: () => patchDomainAccountSettings(ref, toPayload(form)),
        onSuccess: updated => {
            queryClient.setQueryData(['domain', ref.organizationId, ref.environmentId, updated.hrid], updated);
            void queryClient.invalidateQueries({ queryKey: ['domains'] });
            toast.success('User Accounts Settings updated');
        },
        onError: error => toast.error(`Could not update the user accounts settings. ${error.message}`),
    });

    const { settings, durations, fields } = form;
    const dirty = JSON.stringify(form) !== JSON.stringify(fromAccountSettings(domain.accountSettings));
    const errors = validate(form);
    const invalid = Object.keys(errors).length > 0;
    const set = (patch: Partial<Settings>, durationPatch: Partial<FormState['durations']> = {}) =>
        setForm(current => ({
            ...current,
            settings: { ...current.settings, ...patch },
            durations: { ...current.durations, ...durationPatch },
        }));
    const setDuration = (key: DurationKey) => (value: DurationDraft) => set({}, { [key]: value });
    const describedBy = (key: keyof FormErrors) => (errors[key] ? errorId(key) : undefined);
    const hasBotDetections = (botDetections.data?.length ?? 0) > 0;
    const submit = (event: FormEvent) => {
        event.preventDefault();
        if (!invalid) {
            save.mutate();
        }
    };

    return (
        <div className="flex flex-col gap-6">
            <PageHeader title="User Accounts" description="Security and settings for user accounts." />

            <form id={FORM_ID} className="flex flex-col gap-6" onSubmit={submit}>
                <Card>
                    <CardHeader>
                        <CardTitle className="text-base">Login</CardTitle>
                    </CardHeader>
                    <CardContent className="grid gap-5">
                        <SettingToggle
                            id="account-brute-force"
                            label="Brute force detection"
                            description="Block an account after too many failed login attempts."
                            checked={settings.loginAttemptsDetectionEnabled === true}
                            disabled={!canEdit}
                            onChange={loginAttemptsDetectionEnabled =>
                                set(
                                    { loginAttemptsDetectionEnabled, maxLoginAttempts: settings.maxLoginAttempts || DEFAULT_MAX_ATTEMPTS },
                                    {
                                        loginAttemptsResetTime: orDefault(durations.loginAttemptsResetTime, DEFAULT_LOGIN_ATTEMPTS_RESET),
                                        accountBlockedDuration: orDefault(durations.accountBlockedDuration, DEFAULT_ACCOUNT_BLOCKED),
                                    },
                                )
                            }
                        />
                        {settings.loginAttemptsDetectionEnabled && (
                            <>
                                <Field data-invalid={!!errors.maxLoginAttempts}>
                                    <FieldLabel htmlFor="account-max-attempts">Max login attempts</FieldLabel>
                                    <Input
                                        id="account-max-attempts"
                                        type="number"
                                        min={1}
                                        className="w-48"
                                        value={settings.maxLoginAttempts ?? ''}
                                        disabled={!canEdit}
                                        aria-invalid={!!errors.maxLoginAttempts}
                                        aria-describedby={describedBy('maxLoginAttempts')}
                                        onChange={event => set({ maxLoginAttempts: numberOrUndefined(event.target.value) })}
                                    />
                                    <Hint errors={errors} name="maxLoginAttempts">
                                        Failures after which the account is blocked.
                                    </Hint>
                                </Field>
                                <Field data-invalid={!!errors.loginAttemptsResetTime}>
                                    <FieldLabel htmlFor="account-reset-time">Login attempts reset time</FieldLabel>
                                    <DurationInput
                                        id="account-reset-time"
                                        label="Login attempts reset time"
                                        value={durations.loginAttemptsResetTime}
                                        disabled={!canEdit}
                                        invalid={!!errors.loginAttemptsResetTime}
                                        describedBy={describedBy('loginAttemptsResetTime')}
                                        onChange={setDuration('loginAttemptsResetTime')}
                                    />
                                    <Hint errors={errors} name="loginAttemptsResetTime">
                                        Time after which failed attempts are forgotten, if the maximum was not reached.
                                    </Hint>
                                </Field>
                                <Field data-invalid={!!errors.accountBlockedDuration}>
                                    <FieldLabel htmlFor="account-blocked">Account blocked duration</FieldLabel>
                                    <DurationInput
                                        id="account-blocked"
                                        label="Account blocked duration"
                                        value={durations.accountBlockedDuration}
                                        disabled={!canEdit}
                                        invalid={!!errors.accountBlockedDuration}
                                        describedBy={describedBy('accountBlockedDuration')}
                                        onChange={setDuration('accountBlockedDuration')}
                                    />
                                    <Hint errors={errors} name="accountBlockedDuration">
                                        How long the user stays blocked.
                                    </Hint>
                                </Field>
                            </>
                        )}
                        <SettingToggle
                            id="account-recover-email"
                            label="Send account recovery email"
                            description="Email the user when their account has been blocked."
                            checked={settings.sendRecoverAccountEmail === true}
                            disabled={!canEdit}
                            onChange={sendRecoverAccountEmail => set({ sendRecoverAccountEmail })}
                        />
                        <SettingToggle
                            id="account-remember-me"
                            label="Remember me"
                            description="Let users stay signed in for a set duration."
                            checked={settings.rememberMe === true}
                            disabled={!canEdit}
                            onChange={rememberMe =>
                                set({ rememberMe }, { rememberMeDuration: orDefault(durations.rememberMeDuration, DEFAULT_REMEMBER_ME) })
                            }
                        />
                        {settings.rememberMe && (
                            <Field data-invalid={!!errors.rememberMeDuration}>
                                <FieldLabel htmlFor="account-remember-duration">Session duration</FieldLabel>
                                <DurationInput
                                    id="account-remember-duration"
                                    label="Session duration"
                                    value={durations.rememberMeDuration}
                                    disabled={!canEdit}
                                    invalid={!!errors.rememberMeDuration}
                                    describedBy={describedBy('rememberMeDuration')}
                                    onChange={setDuration('rememberMeDuration')}
                                />
                                <Hint errors={errors} name="rememberMeDuration">
                                    How long users stay signed in.
                                </Hint>
                            </Field>
                        )}
                    </CardContent>
                </Card>

                <Card>
                    <CardHeader>
                        <CardTitle className="text-base">MFA</CardTitle>
                    </CardHeader>
                    <CardContent className="grid gap-5">
                        <SettingToggle
                            id="account-mfa-brute-force"
                            label="MFA brute force detection"
                            description="Limit how many MFA challenges a user can attempt."
                            checked={settings.mfaChallengeAttemptsDetectionEnabled === true}
                            disabled={!canEdit}
                            onChange={mfaChallengeAttemptsDetectionEnabled =>
                                set(
                                    { mfaChallengeAttemptsDetectionEnabled, mfaChallengeMaxAttempts: DEFAULT_MFA_MAX_ATTEMPTS },
                                    {
                                        mfaChallengeAttemptsResetTime: orDefault(
                                            durations.mfaChallengeAttemptsResetTime,
                                            DEFAULT_MFA_RESET,
                                        ),
                                    },
                                )
                            }
                        />
                        {settings.mfaChallengeAttemptsDetectionEnabled && (
                            <>
                                <Field data-invalid={!!errors.mfaChallengeMaxAttempts}>
                                    <FieldLabel htmlFor="account-mfa-max">Max challenge attempts</FieldLabel>
                                    <Input
                                        id="account-mfa-max"
                                        type="number"
                                        min={1}
                                        className="w-48"
                                        value={settings.mfaChallengeMaxAttempts ?? ''}
                                        disabled={!canEdit}
                                        aria-invalid={!!errors.mfaChallengeMaxAttempts}
                                        aria-describedby={describedBy('mfaChallengeMaxAttempts')}
                                        onChange={event => set({ mfaChallengeMaxAttempts: numberOrUndefined(event.target.value) })}
                                    />
                                    <Hint errors={errors} name="mfaChallengeMaxAttempts">
                                        Attempts after which the user must wait for the reset time.
                                    </Hint>
                                </Field>
                                <Field data-invalid={!!errors.mfaChallengeAttemptsResetTime}>
                                    <FieldLabel htmlFor="account-mfa-reset">MFA attempts reset time</FieldLabel>
                                    <DurationInput
                                        id="account-mfa-reset"
                                        label="MFA attempts reset time"
                                        value={durations.mfaChallengeAttemptsResetTime}
                                        disabled={!canEdit}
                                        invalid={!!errors.mfaChallengeAttemptsResetTime}
                                        describedBy={describedBy('mfaChallengeAttemptsResetTime')}
                                        onChange={setDuration('mfaChallengeAttemptsResetTime')}
                                    />
                                    <Hint errors={errors} name="mfaChallengeAttemptsResetTime">
                                        Time after which challenge attempts are reset.
                                    </Hint>
                                </Field>
                            </>
                        )}
                        <SettingToggle
                            id="account-mfa-alert"
                            label="Send alert email"
                            description="Email the user when the maximum number of challenge attempts is reached."
                            checked={settings.mfaChallengeSendVerifyAlertEmail === true}
                            disabled={!canEdit}
                            onChange={mfaChallengeSendVerifyAlertEmail => set({ mfaChallengeSendVerifyAlertEmail })}
                        />
                    </CardContent>
                </Card>

                <Card>
                    <CardHeader>
                        <CardTitle className="text-base">User registration</CardTitle>
                    </CardHeader>
                    <CardContent className="grid gap-5">
                        <Field>
                            <FieldLabel htmlFor="account-registration-idp">Identity provider</FieldLabel>
                            <Select
                                value={settings.defaultIdentityProviderForRegistration ?? NONE}
                                disabled={!canEdit || !providers.data}
                                onValueChange={id => set({ defaultIdentityProviderForRegistration: id === NONE || !id ? undefined : id })}
                            >
                                <SelectTrigger id="account-registration-idp" className="w-full">
                                    <SelectValue />
                                </SelectTrigger>
                                <SelectContent>
                                    <SelectItem value={NONE}>None</SelectItem>
                                    {(providers.data ?? []).map(provider => (
                                        <SelectItem key={provider.id} value={provider.id}>
                                            {provider.name}
                                        </SelectItem>
                                    ))}
                                </SelectContent>
                            </Select>
                            <FieldDescription>
                                {providers.error
                                    ? 'Could not load the identity providers.'
                                    : 'Where registered users are stored, instead of the default identity provider.'}
                            </FieldDescription>
                        </Field>
                        <SettingToggle
                            id="account-dynamic-registration"
                            label="Dynamic user registration"
                            description="Pre-registration through the Management API's POST /users returns the new user and the registration endpoint to finish the account. No email is sent."
                            checked={settings.dynamicUserRegistration === true}
                            disabled={!canEdit}
                            onChange={dynamicUserRegistration => set({ dynamicUserRegistration })}
                        />
                        <SettingToggle
                            id="account-complete-registration"
                            label="Complete user registration (reset password)"
                            description="Finalize the account of a user who resets their password before completing registration, instead of showing an error."
                            checked={settings.completeRegistrationWhenResetPassword === true}
                            disabled={!canEdit}
                            onChange={completeRegistrationWhenResetPassword => set({ completeRegistrationWhenResetPassword })}
                        />
                        <SettingToggle
                            id="account-verify-email"
                            label="Account verification via email"
                            description="Once a user has registered, send them an email with an account verification link."
                            checked={settings.sendVerifyRegistrationAccountEmail === true}
                            disabled={!canEdit}
                            onChange={sendVerifyRegistrationAccountEmail => set({ sendVerifyRegistrationAccountEmail })}
                        />
                        <SettingToggle
                            id="account-auto-login-registration"
                            label="Auto login after user registration"
                            description="Sign the user in as soon as registration completes."
                            checked={settings.autoLoginAfterRegistration === true}
                            disabled={!canEdit}
                            onChange={autoLoginAfterRegistration => set({ autoLoginAfterRegistration })}
                        />
                        <Field>
                            <FieldLabel htmlFor="account-registration-redirect">Registration redirect URI</FieldLabel>
                            <Input
                                id="account-registration-redirect"
                                placeholder="https://www.acme.example/welcome"
                                value={settings.redirectUriAfterRegistration ?? ''}
                                disabled={!canEdit}
                                onChange={event => set({ redirectUriAfterRegistration: event.target.value })}
                            />
                            <FieldDescription>
                                Where users go once registered. By default, the registration confirmation page is shown.
                            </FieldDescription>
                        </Field>
                    </CardContent>
                </Card>

                <Card>
                    <CardHeader>
                        <CardTitle className="text-base">Reset password</CardTitle>
                    </CardHeader>
                    <CardContent className="grid gap-5">
                        <SettingToggle
                            id="account-delete-devices"
                            label="Delete passwordless devices"
                            description="Remove the user's passwordless devices once they reset their password."
                            checked={settings.deletePasswordlessDevicesAfterResetPassword === true}
                            disabled={!canEdit}
                            onChange={deletePasswordlessDevicesAfterResetPassword => set({ deletePasswordlessDevicesAfterResetPassword })}
                        />
                        <SettingToggle
                            id="account-auto-login-reset"
                            label="Auto login after reset password"
                            description="Sign the user in as soon as the password is reset."
                            checked={settings.autoLoginAfterResetPassword === true}
                            disabled={!canEdit}
                            onChange={autoLoginAfterResetPassword => set({ autoLoginAfterResetPassword })}
                        />
                        <Field>
                            <FieldLabel htmlFor="account-reset-redirect">Reset password redirect URI</FieldLabel>
                            <Input
                                id="account-reset-redirect"
                                placeholder="https://www.acme.example/account"
                                value={settings.redirectUriAfterResetPassword ?? ''}
                                disabled={!canEdit}
                                onChange={event => set({ redirectUriAfterResetPassword: event.target.value })}
                            />
                            <FieldDescription>
                                Where users go after resetting their password. By default, the reset password page is shown.
                            </FieldDescription>
                        </Field>
                        <SettingToggle
                            id="account-invalidate-tokens"
                            label="Invalidate tokens"
                            description="Revoke the user's tokens after a password reset."
                            checked={settings.resetPasswordInvalidateTokens === true}
                            disabled={!canEdit}
                            onChange={resetPasswordInvalidateTokens => set({ resetPasswordInvalidateTokens })}
                        />
                        <SettingToggle
                            id="account-custom-form"
                            label="Forgot password form with custom fields"
                            description="Choose the fields users fill in on the forgot password form."
                            checked={settings.resetPasswordCustomForm === true}
                            disabled={!canEdit}
                            onChange={resetPasswordCustomForm => set({ resetPasswordCustomForm })}
                        />
                        {settings.resetPasswordCustomForm && (
                            <>
                                <CustomFormFields
                                    fields={fields}
                                    editable={canEdit}
                                    error={errors.fields}
                                    onChange={next => setForm(current => ({ ...current, fields: next }))}
                                />
                                <SettingToggle
                                    id="account-confirm-identity"
                                    label="Allow confirm user identity"
                                    description="Search users by email on the forgot password form, and fall back to the custom form when several users match."
                                    checked={settings.resetPasswordConfirmIdentity === true}
                                    disabled={!canEdit}
                                    onChange={resetPasswordConfirmIdentity => set({ resetPasswordConfirmIdentity })}
                                />
                            </>
                        )}
                    </CardContent>
                </Card>

                <Card>
                    <CardHeader>
                        <CardTitle className="text-base">Bot detection</CardTitle>
                        <CardDescription>
                            {botDetections.error
                                ? 'Could not load the bot detection plugins.'
                                : hasBotDetections || botDetections.isPending
                                  ? 'Protect the sign-in, sign-up and forgot password pages against bots.'
                                  : 'Add a bot detection plugin to the domain (Security › Bot Detection) to protect pages against bots.'}
                        </CardDescription>
                    </CardHeader>
                    <CardContent className="grid gap-5">
                        <SettingToggle
                            id="account-bot-detection"
                            label="Protect pages against bots"
                            description="Enable bot protection on the sign-in, sign-up and forgot password pages."
                            checked={settings.useBotDetection === true}
                            disabled={!canEdit || !hasBotDetections}
                            onChange={useBotDetection => set({ useBotDetection })}
                        />
                        <Field>
                            <FieldLabel htmlFor="account-bot-plugin">Bot detection plugin</FieldLabel>
                            <Select
                                value={settings.botDetectionPlugin ?? ''}
                                disabled={!canEdit || !hasBotDetections}
                                onValueChange={botDetectionPlugin => set({ botDetectionPlugin: botDetectionPlugin ?? undefined })}
                            >
                                <SelectTrigger id="account-bot-plugin" className="w-full">
                                    <SelectValue placeholder="Select a plugin" />
                                </SelectTrigger>
                                <SelectContent>
                                    {(botDetections.data ?? []).map(botDetection => (
                                        <SelectItem key={botDetection.id} value={botDetection.id}>
                                            {botDetection.name}
                                        </SelectItem>
                                    ))}
                                </SelectContent>
                            </Select>
                            <FieldDescription>
                                The plugin used. Custom page templates need the snippets shown on the plugin&apos;s configuration form.
                            </FieldDescription>
                        </Field>
                    </CardContent>
                </Card>
            </form>

            {canEdit && (
                <SaveBar
                    formId={FORM_ID}
                    dirty={dirty}
                    saving={save.isPending}
                    invalid={invalid}
                    onDiscard={() => setForm(fromAccountSettings(domain.accountSettings))}
                />
            )}
        </div>
    );
}
