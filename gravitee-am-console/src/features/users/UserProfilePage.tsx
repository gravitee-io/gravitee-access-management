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
    toast,
} from '@gravitee/graphene-core';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { type FormEvent, type ReactNode, useState } from 'react';
import { ConfirmDialog } from '../../app/components/ConfirmDialog';
import { SaveBar } from '../../app/components/SaveBar';
import { SettingToggle } from '../../app/components/SettingToggle';
import { isEmailRequired, updateUser, updateUsername, type User } from '../../lib/api/users';
import { useCurrentDomain } from '../../lib/session/domain';
import { usePermissions } from '../../lib/session/permissions';
import { ApplicationPicker, type PickedApplication } from './ApplicationPicker';
import { AttributesEditor } from './AttributesEditor';
import { useUser, userQueryKey } from './UserLayout';
import { type Attribute, EMAIL_PATTERN, additionalInformationOf, attributesOf, displayNameOf, isAccountLocked } from './user-helpers';

const FORM_ID = 'user-profile-form';
const USERNAME_PATTERN = /^[^\s].*[^\s]$/;

type FormState = {
    readonly firstName: string;
    readonly lastName: string;
    readonly email: string;
    readonly application: PickedApplication | null;
    /** The `client` sent back: the stored value until the picker changes it, then the picked id or null. */
    readonly client: string | null | undefined;
    readonly forceResetPassword: boolean;
    readonly attributes: Attribute[];
};

function fromUser(user: User): FormState {
    const app = user.applicationEntity;
    return {
        firstName: user.firstName ?? '',
        lastName: user.lastName ?? '',
        email: user.email ?? '',
        application: app?.id ? { id: app.id, name: app.name ?? app.id } : null,
        client: user.client,
        forceResetPassword: Boolean(user.forceResetPassword),
        attributes: attributesOf(user.additionalInformation),
    };
}

function formatDate(value: number | null | undefined): string {
    return value ? new Date(value).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' }) : '';
}

/** The Angular `UserProfileComponent`. The account actions are in the layout header. */
export function UserProfilePage() {
    const { user } = useUser();
    const { ref } = useCurrentDomain();
    const queryClient = useQueryClient();
    const canEdit = usePermissions().has('domain_user_update');
    const emailRequired = useQuery({ queryKey: ['email-required'], queryFn: isEmailRequired, staleTime: Infinity });
    const [form, setForm] = useState<FormState>(() => fromUser(user));
    const [username, setUsername] = useState(user.username);
    const [confirmUsername, setConfirmUsername] = useState(false);

    const [syncedUser, setSyncedUser] = useState(user);
    if (syncedUser !== user) {
        setSyncedUser(user);
        setForm(fromUser(user));
        setUsername(user.username);
    }

    const set = (patch: Partial<FormState>) => setForm(current => ({ ...current, ...patch }));
    const showFirstName = Boolean(user.firstName || user.internal);
    const showLastName = Boolean(user.lastName);
    const showEmail = Boolean(user.email || user.internal);
    const showForceReset = canEdit && Boolean(user.internal) && user.referenceType === 'domain';
    const errors = {
        firstName: showFirstName && form.firstName === '' ? 'Enter first name' : undefined,
        lastName: showLastName && form.lastName === '' ? 'Enter last name' : undefined,
        email:
            showEmail && (emailRequired.data ?? true) && form.email === ''
                ? 'Enter email'
                : form.email !== '' && !EMAIL_PATTERN.test(form.email)
                  ? 'Not a valid email'
                  : undefined,
    };
    const invalid = Object.values(errors).some(Boolean);
    const dirty = JSON.stringify(form) !== JSON.stringify(fromUser(user));
    const usernameError =
        username === '' ? 'Enter Username' : !USERNAME_PATTERN.test(username) ? 'Username cannot start or end with whitespace' : undefined;

    const save = useMutation({
        mutationFn: () =>
            updateUser(ref, user.id, {
                firstName: showFirstName ? form.firstName : user.firstName,
                lastName: showLastName ? form.lastName : user.lastName,
                displayName: displayNameOf(showFirstName ? form.firstName : '', showLastName ? form.lastName : '', user.username),
                email: showEmail ? form.email : user.email,
                enabled: user.enabled,
                client: form.client,
                additionalInformation: additionalInformationOf(form.attributes, user.additionalInformation),
                forceResetPassword: form.forceResetPassword,
            }),
        // The PUT answers without the application and identity provider names that GET adds.
        onSuccess: async () => {
            void queryClient.invalidateQueries({ queryKey: ['users', ref] });
            await queryClient.invalidateQueries({ queryKey: userQueryKey(ref, user.id) });
            toast.success('User updated');
        },
        onError: error => toast.error(`Could not update the user. ${error.message}`),
    });
    const rename = useMutation({
        mutationFn: () => updateUsername(ref, user.id, username),
        onSuccess: async () => {
            setConfirmUsername(false);
            void queryClient.invalidateQueries({ queryKey: ['users', ref] });
            await queryClient.invalidateQueries({ queryKey: userQueryKey(ref, user.id) });
            toast.success('Username updated');
        },
        onError: error => {
            setConfirmUsername(false);
            toast.error(`Could not update the username. ${error.message}`);
        },
    });

    const submit = (event: FormEvent) => {
        event.preventDefault();
        if (!invalid) {
            save.mutate();
        }
    };
    const locked = isAccountLocked(user);
    const activity: { label: string; value: ReactNode }[] = [
        { label: 'Logins count', value: String(user.loginsCount ?? 0) },
        { label: 'Last login', value: user.loggedAt ? formatDate(user.loggedAt) : 'Never logged in' },
        { label: 'Created at', value: formatDate(user.createdAt) },
        {
            label: 'Password last reset at',
            value: user.lastPasswordReset ? formatDate(user.lastPasswordReset) : 'Password has not been reset',
        },
        { label: 'Last application', value: user.applicationEntity?.name ?? '' },
        { label: 'Last identity provider', value: user.lastIdentityUsed ?? '' },
        { label: 'Source', value: user.source ?? '' },
        ...(locked ? [{ label: 'Account blocked', value: `Until: ${formatDate(user.accountLockedUntil)}` }] : []),
    ];

    return (
        <div className="grid gap-6 lg:grid-cols-3">
            <div className="flex flex-col gap-6 lg:col-span-2">
                <Card>
                    <CardHeader>
                        <CardTitle>Username</CardTitle>
                        <CardDescription>The username of the user.</CardDescription>
                    </CardHeader>
                    <CardContent>
                        <div className="flex items-start gap-2">
                            <Field className="flex-1" data-invalid={(username !== user.username && usernameError) || undefined}>
                                <Input
                                    aria-label="Username"
                                    value={username}
                                    disabled={!user.internal || !canEdit}
                                    aria-invalid={Boolean(username !== user.username && usernameError)}
                                    onChange={event => setUsername(event.target.value)}
                                />
                                {username !== user.username && usernameError ? (
                                    <FieldError>{usernameError}</FieldError>
                                ) : (
                                    <FieldDescription>The name of the username.</FieldDescription>
                                )}
                            </Field>
                            {user.internal && canEdit && (
                                <Button
                                    variant="outline"
                                    type="button"
                                    disabled={Boolean(usernameError) || username === user.username}
                                    onClick={() => setConfirmUsername(true)}
                                >
                                    Update username
                                </Button>
                            )}
                        </div>
                    </CardContent>
                </Card>

                <form id={FORM_ID} className="flex flex-col gap-6" onSubmit={submit} noValidate>
                    <Card>
                        <CardHeader>
                            <CardTitle>Details</CardTitle>
                            <CardDescription>Basic details about the user.</CardDescription>
                        </CardHeader>
                        <CardContent className="grid gap-5">
                            {(showFirstName || showLastName) && (
                                <div className="grid gap-5 sm:grid-cols-2">
                                    {showFirstName && (
                                        <TextField
                                            id="user-profile-first-name"
                                            label="First name"
                                            hint="The first name of the User."
                                            value={form.firstName}
                                            error={errors.firstName}
                                            disabled={!canEdit}
                                            onChange={firstName => set({ firstName })}
                                        />
                                    )}
                                    {showLastName && (
                                        <TextField
                                            id="user-profile-last-name"
                                            label="Last name"
                                            hint="The last name of the User."
                                            value={form.lastName}
                                            error={errors.lastName}
                                            disabled={!canEdit}
                                            onChange={lastName => set({ lastName })}
                                        />
                                    )}
                                </div>
                            )}
                            {showEmail && (
                                <TextField
                                    id="user-profile-email"
                                    label="Email"
                                    hint="Email address for the User."
                                    value={form.email}
                                    error={errors.email}
                                    disabled={!canEdit}
                                    onChange={email => set({ email })}
                                />
                            )}
                            {canEdit && (
                                <Field>
                                    <FieldLabel htmlFor="user-profile-application">Application</FieldLabel>
                                    <ApplicationPicker
                                        id="user-profile-application"
                                        value={form.application}
                                        onChange={application => set({ application, client: application?.id ?? null })}
                                        describedBy="user-profile-application-hint"
                                    />
                                    <FieldDescription id="user-profile-application-hint">
                                        Assign user to an application. Useful if you want to apply application guidelines (email, templates)
                                        to your user.
                                    </FieldDescription>
                                </Field>
                            )}
                        </CardContent>
                    </Card>

                    {showForceReset && (
                        <Card>
                            <CardContent>
                                <SettingToggle
                                    id="user-profile-force-reset"
                                    label="Force password reset"
                                    description="Ask this user to reset their password at next sign in."
                                    checked={form.forceResetPassword}
                                    onChange={forceResetPassword => set({ forceResetPassword })}
                                />
                            </CardContent>
                        </Card>
                    )}

                    {(canEdit || form.attributes.length > 0) && (
                        <Card>
                            <CardHeader>
                                <CardTitle>Additional information</CardTitle>
                                <CardDescription>
                                    Custom information about the user. These details are available in the user&apos;s profile.
                                </CardDescription>
                            </CardHeader>
                            <CardContent>
                                <AttributesEditor
                                    attributes={form.attributes}
                                    editable={canEdit}
                                    onChange={attributes => set({ attributes })}
                                />
                            </CardContent>
                        </Card>
                    )}
                </form>
                {canEdit && (
                    <SaveBar
                        formId={FORM_ID}
                        dirty={dirty}
                        saving={save.isPending}
                        invalid={invalid}
                        onDiscard={() => setForm(fromUser(user))}
                    />
                )}
            </div>

            <Card className="self-start">
                <CardHeader>
                    <CardTitle>User activities</CardTitle>
                </CardHeader>
                <CardContent>
                    <dl className="grid gap-4">
                        {activity.map(item => (
                            <div key={item.label} className="min-w-0">
                                <dt className="text-xs text-muted-foreground">{item.label}</dt>
                                <dd className="truncate text-sm">{item.value || '—'}</dd>
                            </div>
                        ))}
                    </dl>
                </CardContent>
            </Card>

            <ConfirmDialog
                open={confirmUsername}
                title="Update username"
                description="Are you sure you want to update this username?"
                confirmLabel="Update"
                pending={rename.isPending}
                onOpenChange={setConfirmUsername}
                onConfirm={() => rename.mutate()}
            />
        </div>
    );
}

interface TextFieldProps {
    readonly id: string;
    readonly label: string;
    readonly hint: string;
    readonly value: string;
    readonly error?: string;
    readonly disabled: boolean;
    readonly onChange: (value: string) => void;
}

function TextField({ id, label, hint, value, error, disabled, onChange }: TextFieldProps) {
    return (
        <Field data-invalid={error ? true : undefined}>
            <FieldLabel htmlFor={id}>{label}</FieldLabel>
            <Input
                id={id}
                value={value}
                disabled={disabled}
                aria-invalid={Boolean(error)}
                onChange={event => onChange(event.target.value)}
            />
            {error ? <FieldError>{error}</FieldError> : <FieldDescription>{hint}</FieldDescription>}
        </Field>
    );
}
