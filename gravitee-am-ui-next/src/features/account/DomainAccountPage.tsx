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
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
    toast,
} from '@gravitee/graphene-core';
import { PlusIcon, XIcon } from '@gravitee/graphene-core/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { DurationInput } from '../../app/components/DurationInput';
import { PageHeader } from '../../app/components/PageHeader';
import { SaveBar } from '../../app/components/SaveBar';
import { SettingToggle } from '../../app/components/SettingToggle';
import { listBotDetections } from '../../lib/api/bot-detection';
import { type AccountSettings, type Domain, patchDomainAccountSettings } from '../../lib/api/domains';
import { listUserIdentityProviders } from '../../lib/api/identity-providers';
import { type DurationDraft, draftToSeconds, exactDuration } from '../../lib/forms/duration';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { useCurrentDomain } from '../../lib/session/domain';
import { usePermissions } from '../../lib/session/permissions';

type Settings = { -readonly [K in keyof AccountSettings]: AccountSettings[K] };

interface CustomField {
    readonly key: string;
    readonly label: string;
    readonly type: string;
}

type DurationKey = 'loginAttemptsResetTime' | 'accountBlockedDuration' | 'mfaChallengeAttemptsResetTime' | 'rememberMeDuration';

interface FormState {
    readonly settings: Settings;
    /** The four durations, edited as an amount and a unit, and saved in seconds. */
    readonly durations: Readonly<Record<DurationKey, DurationDraft>>;
    readonly fields: readonly CustomField[];
}

// The Angular `AccountSettingsComponent` defaults, applied when a feature is turned on.
const DEFAULT_MAX_ATTEMPTS = 10;
const DEFAULT_LOGIN_ATTEMPTS_RESET = 43_200;
const DEFAULT_ACCOUNT_BLOCKED = 7_200;
const DEFAULT_MFA_RESET = 60;
const DEFAULT_REMEMBER_ME = 1_209_600;
const DEFAULT_MFA_MAX_ATTEMPTS = 3;
const NONE = '__none__';
const EMPTY_DURATION: DurationDraft = { time: '', unit: '' };

const AVAILABLE_FIELDS: readonly CustomField[] = [
    { key: 'email', label: 'Email', type: 'email' },
    { key: 'username', label: 'Username', type: 'text' },
];

/** The Angular `initDateValues`: a duration shows only when one is stored. */
function storedDuration(seconds: number | undefined): DurationDraft {
    return seconds && seconds > 0 ? exactDuration(seconds) : EMPTY_DURATION;
}

/** The current duration, or the default when it is empty, as the Angular `enable…` handlers do. */
function orDefault(duration: DurationDraft, seconds: number): DurationDraft {
    return draftToSeconds(duration) ? duration : exactDuration(seconds);
}

function fromDomain(domain: Domain): FormState {
    const settings: Settings = { ...domain.accountSettings };
    return {
        settings,
        durations: {
            loginAttemptsResetTime: storedDuration(settings.loginAttemptsResetTime),
            accountBlockedDuration: storedDuration(settings.accountBlockedDuration),
            mfaChallengeAttemptsResetTime: storedDuration(settings.mfaChallengeAttemptsResetTime),
            rememberMeDuration: storedDuration(settings.rememberMeDuration),
        },
        fields: settings.resetPasswordCustomForm ? ((settings.resetPasswordCustomFormFields ?? []) as CustomField[]) : [],
    };
}

/** The Angular `AccountSettingsComponent.save` and `DomainSettingsAccountComponent.updateAccountSettings`. */
function toPayload({ settings, durations, fields }: FormState): AccountSettings {
    const payload: Settings = {
        ...settings,
        inherited: false,
        loginAttemptsResetTime: draftToSeconds(durations.loginAttemptsResetTime),
        accountBlockedDuration: draftToSeconds(durations.accountBlockedDuration),
        mfaChallengeAttemptsResetTime: draftToSeconds(durations.mfaChallengeAttemptsResetTime),
        rememberMeDuration: draftToSeconds(durations.rememberMeDuration),
    };
    if (settings.resetPasswordCustomForm) {
        payload.resetPasswordCustomFormFields = [...fields];
    } else {
        delete payload.resetPasswordCustomFormFields;
        delete payload.resetPasswordConfirmIdentity;
    }
    if (!settings.useBotDetection) {
        delete payload.botDetectionPlugin;
    }
    return payload;
}

const durationValid = (duration: DurationDraft) => !!duration.unit && Number(duration.time) >= 1;

/** The Angular `formIsValid`, one message per failing field. */
function validate({ settings, durations, fields }: FormState) {
    const errors: Partial<Record<DurationKey | 'maxLoginAttempts' | 'mfaChallengeMaxAttempts' | 'fields', string>> = {};
    if (settings.loginAttemptsDetectionEnabled) {
        if (!settings.maxLoginAttempts || settings.maxLoginAttempts < 1) {
            errors.maxLoginAttempts = 'Enter at least 1 attempt.';
        }
        if (!durationValid(durations.loginAttemptsResetTime)) {
            errors.loginAttemptsResetTime = 'Enter a duration of 1 or more.';
        }
        if (!durationValid(durations.accountBlockedDuration)) {
            errors.accountBlockedDuration = 'Enter a duration of 1 or more.';
        }
    }
    if (settings.mfaChallengeAttemptsDetectionEnabled) {
        if (!settings.mfaChallengeMaxAttempts || settings.mfaChallengeMaxAttempts < 1) {
            errors.mfaChallengeMaxAttempts = 'Enter at least 1 attempt.';
        }
        if (!durationValid(durations.mfaChallengeAttemptsResetTime)) {
            errors.mfaChallengeAttemptsResetTime = 'Enter a duration of 1 or more.';
        }
    }
    if (settings.rememberMe && !durationValid(durations.rememberMeDuration)) {
        errors.rememberMeDuration = 'Enter a duration of 1 or more.';
    }
    if (settings.resetPasswordCustomForm && fields.length === 0) {
        errors.fields = 'Add at least one field to the custom form.';
    }
    return errors;
}

const numberOrUndefined = (raw: string) => (raw === '' ? undefined : Number(raw));

/**
 * The Angular `DomainSettingsAccountComponent` with `AccountSettingsComponent` (Settings › User Accounts): brute force
 * detection for logins and MFA challenges, remember me, registration and reset password behaviour, and bot detection.
 * Saved with `DomainService.patchAccountSettings`, always with `inherited: false`.
 */
export function DomainAccountPage() {
    const { domain, ref, basePath, domainsPath } = useCurrentDomain();
    const queryClient = useQueryClient();
    // Angular makes the form read-only without domain_settings_create and without domain_settings_update.
    const canEdit = usePermissions().hasAny('domain_settings_create', 'domain_settings_update');
    const providers = useQuery({ queryKey: ['user-identity-providers', ref], queryFn: () => listUserIdentityProviders(ref) });
    const botDetections = useQuery({ queryKey: ['bot-detections', ref], queryFn: () => listBotDetections(ref) });
    const [form, setForm] = useState<FormState>(() => fromDomain(domain));
    const [newField, setNewField] = useState<CustomField | undefined>();
    useBreadcrumbs([
        { label: 'Domains', to: domainsPath },
        { label: domain.name, to: `${basePath}/dashboard` },
        { label: 'Settings', to: `${basePath}/settings/general` },
        { label: 'User Accounts' },
    ]);

    // A save or a refetch gives a new domain object. The form then starts again from it.
    const [syncedDomain, setSyncedDomain] = useState(domain);
    if (syncedDomain !== domain) {
        setSyncedDomain(domain);
        setForm(fromDomain(domain));
    }

    const save = useMutation({
        mutationFn: () => patchDomainAccountSettings(ref, toPayload(form)),
        onSuccess: updated => {
            queryClient.setQueryData(['domain', ref.organizationId, ref.environmentId, updated.hrid], updated);
            void queryClient.invalidateQueries({ queryKey: ['domains'] });
            toast.success('User Accounts Settings updated');
        },
        onError: error => toast.error(error.message || 'Unable to update the user accounts settings'),
    });

    const { settings, durations, fields } = form;
    const dirty = JSON.stringify(form) !== JSON.stringify(fromDomain(domain));
    const errors = validate(form);
    const set = (patch: Partial<Settings>, durationPatch: Partial<FormState['durations']> = {}) =>
        setForm(current => ({
            ...current,
            settings: { ...current.settings, ...patch },
            durations: { ...current.durations, ...durationPatch },
        }));
    const setDuration = (key: DurationKey) => (value: DurationDraft) => set({}, { [key]: value });
    const setFields = (next: readonly CustomField[]) => setForm(current => ({ ...current, fields: next }));
    const hasBotDetections = (botDetections.data?.length ?? 0) > 0;
    const fieldError = (key: keyof typeof errors) => (errors[key] ? <FieldError>{errors[key]}</FieldError> : null);

    return (
        <div className="flex flex-col gap-6">
            <PageHeader title="User Accounts" description="Security and settings for user accounts." />

            <Card>
                <CardHeader>
                    <CardTitle className="text-base">Login</CardTitle>
                </CardHeader>
                <CardContent>
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
                    >
                        {settings.loginAttemptsDetectionEnabled ? (
                            <div className="grid gap-5">
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
                                        onChange={event => set({ maxLoginAttempts: numberOrUndefined(event.target.value) })}
                                    />
                                    {fieldError('maxLoginAttempts') ?? (
                                        <FieldDescription>Failures after which the account is blocked.</FieldDescription>
                                    )}
                                </Field>
                                <Field data-invalid={!!errors.loginAttemptsResetTime}>
                                    <FieldLabel htmlFor="account-reset-time">Login attempts reset time</FieldLabel>
                                    <DurationInput
                                        id="account-reset-time"
                                        label="Login attempts reset time"
                                        value={durations.loginAttemptsResetTime}
                                        disabled={!canEdit}
                                        invalid={!!errors.loginAttemptsResetTime}
                                        onChange={setDuration('loginAttemptsResetTime')}
                                    />
                                    {fieldError('loginAttemptsResetTime') ?? (
                                        <FieldDescription>
                                            Time after which failed attempts are forgotten, if the maximum was not reached.
                                        </FieldDescription>
                                    )}
                                </Field>
                                <Field data-invalid={!!errors.accountBlockedDuration}>
                                    <FieldLabel htmlFor="account-blocked">Account blocked duration</FieldLabel>
                                    <DurationInput
                                        id="account-blocked"
                                        label="Account blocked duration"
                                        value={durations.accountBlockedDuration}
                                        disabled={!canEdit}
                                        invalid={!!errors.accountBlockedDuration}
                                        onChange={setDuration('accountBlockedDuration')}
                                    />
                                    {fieldError('accountBlockedDuration') ?? (
                                        <FieldDescription>How long the user stays blocked.</FieldDescription>
                                    )}
                                </Field>
                            </div>
                        ) : null}
                    </SettingToggle>
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
                    >
                        {settings.rememberMe ? (
                            <Field data-invalid={!!errors.rememberMeDuration}>
                                <FieldLabel htmlFor="account-remember-duration">Session duration</FieldLabel>
                                <DurationInput
                                    id="account-remember-duration"
                                    label="Session duration"
                                    value={durations.rememberMeDuration}
                                    disabled={!canEdit}
                                    invalid={!!errors.rememberMeDuration}
                                    onChange={setDuration('rememberMeDuration')}
                                />
                                {fieldError('rememberMeDuration') ?? <FieldDescription>How long users stay signed in.</FieldDescription>}
                            </Field>
                        ) : null}
                    </SettingToggle>
                </CardContent>
            </Card>

            <Card>
                <CardHeader>
                    <CardTitle className="text-base">MFA</CardTitle>
                </CardHeader>
                <CardContent>
                    <SettingToggle
                        id="account-mfa-brute-force"
                        label="MFA brute force detection"
                        description="Limit how many MFA challenges a user can attempt."
                        checked={settings.mfaChallengeAttemptsDetectionEnabled === true}
                        disabled={!canEdit}
                        onChange={mfaChallengeAttemptsDetectionEnabled =>
                            set(
                                { mfaChallengeAttemptsDetectionEnabled, mfaChallengeMaxAttempts: DEFAULT_MFA_MAX_ATTEMPTS },
                                { mfaChallengeAttemptsResetTime: orDefault(durations.mfaChallengeAttemptsResetTime, DEFAULT_MFA_RESET) },
                            )
                        }
                    >
                        {settings.mfaChallengeAttemptsDetectionEnabled ? (
                            <div className="grid gap-5">
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
                                        onChange={event => set({ mfaChallengeMaxAttempts: numberOrUndefined(event.target.value) })}
                                    />
                                    {fieldError('mfaChallengeMaxAttempts') ?? (
                                        <FieldDescription>Attempts after which the user must wait for the reset time.</FieldDescription>
                                    )}
                                </Field>
                                <Field data-invalid={!!errors.mfaChallengeAttemptsResetTime}>
                                    <FieldLabel htmlFor="account-mfa-reset">MFA attempts reset time</FieldLabel>
                                    <DurationInput
                                        id="account-mfa-reset"
                                        label="MFA attempts reset time"
                                        value={durations.mfaChallengeAttemptsResetTime}
                                        disabled={!canEdit}
                                        invalid={!!errors.mfaChallengeAttemptsResetTime}
                                        onChange={setDuration('mfaChallengeAttemptsResetTime')}
                                    />
                                    {fieldError('mfaChallengeAttemptsResetTime') ?? (
                                        <FieldDescription>Time after which challenge attempts are reset.</FieldDescription>
                                    )}
                                </Field>
                            </div>
                        ) : null}
                    </SettingToggle>
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
                <CardContent className="flex flex-col gap-2">
                    <Field>
                        <FieldLabel htmlFor="account-registration-idp">Identity provider</FieldLabel>
                        <Select
                            value={settings.defaultIdentityProviderForRegistration ?? NONE}
                            disabled={!canEdit || !providers.data}
                            // The Angular "None" option has no value, so the key is left out of the saved settings.
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
                    <div>
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
                    </div>
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
                <CardContent className="flex flex-col gap-2">
                    <div>
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
                    </div>
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
                    <div>
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
                        >
                            {settings.resetPasswordCustomForm ? (
                                <div className="flex flex-col gap-4">
                                    {canEdit ? (
                                        <div className="flex items-end gap-2">
                                            <Field className="w-48">
                                                <FieldLabel htmlFor="account-field-key">Field key</FieldLabel>
                                                <Select
                                                    value={newField?.key ?? ''}
                                                    onValueChange={key => setNewField(AVAILABLE_FIELDS.find(field => field.key === key))}
                                                >
                                                    <SelectTrigger id="account-field-key" className="w-full">
                                                        <SelectValue placeholder="Select a field" />
                                                    </SelectTrigger>
                                                    <SelectContent>
                                                        {AVAILABLE_FIELDS.map(field => (
                                                            <SelectItem
                                                                key={field.key}
                                                                value={field.key}
                                                                disabled={fields.some(selected => selected.key === field.key)}
                                                            >
                                                                {field.label}
                                                            </SelectItem>
                                                        ))}
                                                    </SelectContent>
                                                </Select>
                                            </Field>
                                            <Field className="flex-1">
                                                <FieldLabel htmlFor="account-field-label">Field label</FieldLabel>
                                                <Input
                                                    id="account-field-label"
                                                    value={newField?.label ?? ''}
                                                    disabled={!newField}
                                                    onChange={event => newField && setNewField({ ...newField, label: event.target.value })}
                                                />
                                            </Field>
                                            <Button
                                                variant="outline"
                                                disabled={!newField?.key || !newField.label}
                                                onClick={() => {
                                                    if (newField) {
                                                        setFields([...fields, newField]);
                                                        setNewField(undefined);
                                                    }
                                                }}
                                            >
                                                <PlusIcon aria-hidden />
                                                Add
                                            </Button>
                                        </div>
                                    ) : null}
                                    {fields.length === 0 ? (
                                        <p className="text-sm text-destructive">{errors.fields}</p>
                                    ) : (
                                        <Table>
                                            <TableHeader>
                                                <TableRow>
                                                    <TableHead>Key</TableHead>
                                                    <TableHead>Label</TableHead>
                                                    <TableHead>
                                                        <span className="sr-only">Actions</span>
                                                    </TableHead>
                                                </TableRow>
                                            </TableHeader>
                                            <TableBody>
                                                {fields.map(field => (
                                                    <TableRow key={field.key}>
                                                        <TableCell className="font-mono text-sm">{field.key}</TableCell>
                                                        <TableCell>{field.label}</TableCell>
                                                        <TableCell className="text-right">
                                                            {canEdit ? (
                                                                <Button
                                                                    variant="ghost"
                                                                    size="icon-xs"
                                                                    aria-label={`Remove field ${field.key}`}
                                                                    onClick={() =>
                                                                        setFields(fields.filter(selected => selected.key !== field.key))
                                                                    }
                                                                >
                                                                    <XIcon aria-hidden />
                                                                </Button>
                                                            ) : null}
                                                        </TableCell>
                                                    </TableRow>
                                                ))}
                                            </TableBody>
                                        </Table>
                                    )}
                                    <SettingToggle
                                        id="account-confirm-identity"
                                        label="Allow confirm user identity"
                                        description="Search users by email on the forgot password form, and fall back to the custom form when several users match."
                                        checked={settings.resetPasswordConfirmIdentity === true}
                                        disabled={!canEdit}
                                        onChange={resetPasswordConfirmIdentity => set({ resetPasswordConfirmIdentity })}
                                    />
                                </div>
                            ) : null}
                        </SettingToggle>
                    </div>
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
                <CardContent>
                    <SettingToggle
                        id="account-bot-detection"
                        label="Protect pages against bots"
                        description="Enable bot protection on the sign-in, sign-up and forgot password pages."
                        checked={settings.useBotDetection === true}
                        disabled={!canEdit || !hasBotDetections}
                        onChange={useBotDetection => set({ useBotDetection })}
                    >
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
                    </SettingToggle>
                </CardContent>
            </Card>

            <SaveBar
                canEdit={canEdit}
                dirty={dirty}
                saving={save.isPending}
                invalid={Object.keys(errors).length > 0}
                onDiscard={() => setForm(fromDomain(domain))}
                onSave={() => save.mutate()}
            />
        </div>
    );
}
