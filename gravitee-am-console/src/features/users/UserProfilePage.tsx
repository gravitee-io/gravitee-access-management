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
import { Button, Card, CardContent, Field, FieldDescription, FieldError, FieldLabel, Input, toast } from '@gravitee/graphene-core';
import { FingerprintIcon, TagIcon, UserIcon } from '@gravitee/graphene-core/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { type FormEvent, useState } from 'react';
import { ConfirmDialog } from '../../app/components/ConfirmDialog';
import { SaveBar } from '../../app/components/SaveBar';
import { SettingToggle } from '../../app/components/SettingToggle';
import { isEmailRequired, updateUser, updateUsername, type User } from '../../lib/api/users';
import { useCurrentDomain } from '../../lib/session/domain';
import { usePermissions } from '../../lib/session/permissions';
import { ApplicationPicker, type PickedApplication } from './ApplicationPicker';
import { AttributesEditor } from './AttributesEditor';
import { IconCardHeader } from './IconCardHeader';
import { UserActivityCard } from './UserActivityCard';
import { useUser, userQueryKey } from './UserLayout';
import { type Attribute, EMAIL_PATTERN, additionalInformationOf, attributesOf, displayNameOf } from './user-helpers';

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
    // An action in the layout reloads the user: keep what the admin typed and take only the untouched fields.
    if (syncedUser !== user) {
        setSyncedUser(user);
        if (JSON.stringify(form) === JSON.stringify(fromUser(syncedUser))) {
            setForm(fromUser(user));
        }
        if (username === syncedUser.username) {
            setUsername(user.username);
        }
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
        username === '' ? 'Enter username' : !USERNAME_PATTERN.test(username) ? 'Username cannot start or end with whitespace' : undefined;

    const showUsernameError = username !== user.username && Boolean(usernameError);
    const usernameDescription =
        user.internal && canEdit
            ? 'Changing the username also unlocks the account.'
            : user.internal
              ? 'The username the account signs in with.'
              : 'Managed by the external identity provider the user signs in with.';

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
    return (
        <div className="grid gap-6 lg:grid-cols-3">
            <div className="flex flex-col gap-6 lg:col-span-2">
                <Card>
                    <IconCardHeader icon={<FingerprintIcon />} title="Username" description={usernameDescription} />
                    <CardContent>
                        <div className="flex items-start gap-2">
                            <Field className="flex-1" data-invalid={showUsernameError || undefined}>
                                <Input
                                    aria-label="Username"
                                    value={username}
                                    disabled={!user.internal || !canEdit}
                                    aria-invalid={showUsernameError}
                                    aria-describedby={showUsernameError ? 'user-profile-username-error' : undefined}
                                    onChange={event => setUsername(event.target.value)}
                                />
                                {showUsernameError && <FieldError id="user-profile-username-error">{usernameError}</FieldError>}
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
                        <IconCardHeader icon={<UserIcon />} title="Details" description="Basic details about the user." />
                        <CardContent className="grid gap-5">
                            {(showFirstName || showLastName) && (
                                <div className="grid gap-5 sm:grid-cols-2">
                                    {showFirstName && (
                                        <TextField
                                            id="user-profile-first-name"
                                            label="First name"
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
                                        The application the user registered through.
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
                            <IconCardHeader
                                icon={<TagIcon />}
                                title="Additional information"
                                description="Custom information about the user, available in their profile."
                            />
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

            <UserActivityCard user={user} />

            <ConfirmDialog
                open={confirmUsername}
                title="Update username"
                description={`The username changes from ${user.username} to ${username}. The user signs in with the new one from now on.`}
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
    readonly value: string;
    readonly error?: string;
    readonly disabled: boolean;
    readonly onChange: (value: string) => void;
}

function TextField({ id, label, value, error, disabled, onChange }: TextFieldProps) {
    const errorId = `${id}-error`;
    return (
        <Field data-invalid={error ? true : undefined}>
            <FieldLabel htmlFor={id}>{label}</FieldLabel>
            <Input
                id={id}
                value={value}
                disabled={disabled}
                aria-invalid={Boolean(error)}
                aria-describedby={error ? errorId : undefined}
                onChange={event => onChange(event.target.value)}
            />
            {error && <FieldError id={errorId}>{error}</FieldError>}
        </Field>
    );
}
