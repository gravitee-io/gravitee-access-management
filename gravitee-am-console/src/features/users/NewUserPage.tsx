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
    Checkbox,
    Field,
    FieldDescription,
    FieldError,
    FieldLabel,
    Input,
    Label,
    PasswordInput,
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
    toast,
} from '@gravitee/graphene-core';
import { ArrowLeftIcon } from '@gravitee/graphene-core/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { type FormEvent, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { PageHeader } from '../../app/components/PageHeader';
import { SettingToggle } from '../../app/components/SettingToggle';
import { listUserIdentityProviders } from '../../lib/api/identity-providers';
import { getActivePasswordPolicy } from '../../lib/api/password-policies';
import { createUser, isEmailRequired, type NewUser } from '../../lib/api/users';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { useCurrentDomain } from '../../lib/session/domain';
import { ApplicationPicker, type PickedApplication } from './ApplicationPicker';
import { AttributesEditor } from './AttributesEditor';
import { PasswordRuleList, usePasswordCheck } from './PasswordPolicyCheck';
import { type Attribute, EMAIL_PATTERN, additionalInformationOf } from './user-helpers';

const NONE = '__none__';

export function NewUserPage() {
    const { domain, ref, basePath, domainsPath } = useCurrentDomain();
    const navigate = useNavigate();
    const queryClient = useQueryClient();
    const listPath = `${basePath}/settings/users`;
    useBreadcrumbs([
        { label: 'Domains', to: domainsPath },
        { label: domain.name, to: `${basePath}/dashboard` },
        { label: 'Settings', to: `${basePath}/settings/general` },
        { label: 'Users', to: listPath },
        { label: 'New user' },
    ]);

    const [firstName, setFirstName] = useState('');
    const [lastName, setLastName] = useState('');
    const [email, setEmail] = useState('');
    const [username, setUsername] = useState('');
    const [useEmailAsUsername, setUseEmailAsUsername] = useState(false);
    const [preRegistration, setPreRegistration] = useState(false);
    const [password, setPassword] = useState('');
    const [application, setApplication] = useState<PickedApplication | null>(null);
    const [source, setSource] = useState(NONE);
    const [forceResetPassword, setForceResetPassword] = useState(false);
    const [attributes, setAttributes] = useState<Attribute[]>([]);

    const emailRequired = useQuery({ queryKey: ['email-required'], queryFn: isEmailRequired, staleTime: Infinity });
    // The picker keeps its None option when the providers fail: the user is stored in the default provider.
    const providers = useQuery({ queryKey: ['user-identity-providers', ref], queryFn: () => listUserIdentityProviders(ref) });
    const identityProviderId = source === NONE ? undefined : source;
    const policy = useQuery({
        queryKey: ['password-policy', ref, identityProviderId],
        queryFn: () => getActivePasswordPolicy(ref, identityProviderId),
    });
    const passwordCheck = usePasswordCheck(policy.data, password, { firstName, lastName, email });

    const effectiveUsername = useEmailAsUsername ? email : username;
    const emailMissing = (emailRequired.data ?? true) && email === '';
    const emailInvalid = email !== '' && !EMAIL_PATTERN.test(email);
    const valid =
        firstName !== '' &&
        lastName !== '' &&
        !emailMissing &&
        !emailInvalid &&
        effectiveUsername !== '' &&
        (preRegistration || (password !== '' && passwordCheck.valid));

    const create = useMutation({
        mutationFn: () => {
            const additionalInformation = additionalInformationOf(attributes);
            const user: NewUser = {
                firstName,
                lastName,
                email: email || undefined,
                username: effectiveUsername,
                password: preRegistration ? undefined : password,
                client: application?.id,
                source: identityProviderId,
                forceResetPassword,
                preRegistration,
                ...(Object.keys(additionalInformation).length > 0 && { additionalInformation }),
            };
            return createUser(ref, user);
        },
        onSuccess: user => {
            void queryClient.invalidateQueries({ queryKey: ['users', ref] });
            toast.success(`User ${user.username} created`);
            navigate(`${listPath}/${user.id}`);
        },
        onError: error => toast.error(`Could not create the user. ${error.message}`),
    });

    const submit = (event: FormEvent) => {
        event.preventDefault();
        if (valid) {
            create.mutate();
        }
    };

    return (
        <form className="flex flex-col gap-6" onSubmit={submit} noValidate>
            <div className="space-y-1">
                <Button asChild variant="ghost" size="sm" className="-ml-2">
                    <Link to={listPath}>
                        <ArrowLeftIcon aria-hidden />
                        Users
                    </Link>
                </Button>
                <PageHeader
                    title="Create a user"
                    description="The user will be able to sign in to the applications registered in this domain."
                />
            </div>

            <Card>
                <CardHeader>
                    <CardTitle>Profile</CardTitle>
                </CardHeader>
                <CardContent className="grid gap-5">
                    <div className="grid gap-5 sm:grid-cols-2">
                        <Field>
                            <FieldLabel htmlFor="user-first-name">First name</FieldLabel>
                            <Input id="user-first-name" value={firstName} onChange={event => setFirstName(event.target.value)} required />
                            <FieldDescription>The first name of the User.</FieldDescription>
                        </Field>
                        <Field>
                            <FieldLabel htmlFor="user-last-name">Last name</FieldLabel>
                            <Input id="user-last-name" value={lastName} onChange={event => setLastName(event.target.value)} required />
                            <FieldDescription>The last name of the User.</FieldDescription>
                        </Field>
                    </div>
                    <Field data-invalid={emailInvalid || undefined}>
                        <FieldLabel htmlFor="user-email">Email</FieldLabel>
                        <Input
                            id="user-email"
                            type="email"
                            value={email}
                            aria-invalid={emailInvalid}
                            aria-describedby="user-email-message"
                            required={emailRequired.data ?? true}
                            onChange={event => {
                                setEmail(event.target.value);
                                if (event.target.value === '') {
                                    setUseEmailAsUsername(false);
                                }
                            }}
                        />
                        {emailInvalid ? (
                            <FieldError id="user-email-message">Not a valid email</FieldError>
                        ) : (
                            <FieldDescription id="user-email-message">Email address for the User.</FieldDescription>
                        )}
                    </Field>
                    <Field>
                        <FieldLabel htmlFor="user-username">Username</FieldLabel>
                        <Input
                            id="user-username"
                            value={effectiveUsername}
                            onChange={event => setUsername(event.target.value)}
                            disabled={useEmailAsUsername}
                            required
                        />
                        <FieldDescription>Username for the User.</FieldDescription>
                        <div className="flex items-center gap-2">
                            <Checkbox
                                id="user-email-as-username"
                                checked={useEmailAsUsername}
                                disabled={email === '' || emailInvalid}
                                onCheckedChange={checked => {
                                    setUseEmailAsUsername(checked === true);
                                    setUsername('');
                                }}
                            />
                            <Label htmlFor="user-email-as-username" className="font-normal">
                                Use email as username
                            </Label>
                        </div>
                    </Field>
                </CardContent>
            </Card>

            <Card>
                <CardHeader>
                    <CardTitle>Credentials</CardTitle>
                </CardHeader>
                <CardContent className="grid gap-5">
                    <SettingToggle
                        id="user-pre-registration"
                        label="Enable pre-registration"
                        description="The user will receive an email to complete their profile and activate their account."
                        checked={preRegistration}
                        onChange={setPreRegistration}
                    />
                    {!preRegistration && (
                        <Field>
                            <FieldLabel htmlFor="user-password">Password</FieldLabel>
                            <PasswordInput
                                id="user-password"
                                value={password}
                                autoComplete="new-password"
                                onChange={event => setPassword(event.target.value)}
                                required
                            />
                            <FieldDescription>Password for the User.</FieldDescription>
                            <PasswordRuleList results={passwordCheck.results} />
                        </Field>
                    )}
                    <SettingToggle
                        id="user-force-reset"
                        label="Force password reset"
                        description="Ask this user to reset their password at first sign in."
                        checked={forceResetPassword}
                        onChange={setForceResetPassword}
                    />
                </CardContent>
            </Card>

            <Card>
                <CardHeader>
                    <CardTitle>Registration</CardTitle>
                </CardHeader>
                <CardContent className="grid gap-5">
                    <Field>
                        <FieldLabel htmlFor="user-application">Application</FieldLabel>
                        <ApplicationPicker
                            id="user-application"
                            value={application}
                            onChange={setApplication}
                            describedBy="user-application-hint"
                        />
                        <FieldDescription id="user-application-hint">
                            Assign user to an application. Useful if you want to apply application guidelines (email, templates) to your
                            user.
                        </FieldDescription>
                    </Field>
                    <Field>
                        <FieldLabel htmlFor="user-source">Identity provider</FieldLabel>
                        <Select value={source} onValueChange={setSource}>
                            <SelectTrigger id="user-source">
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
                            Assign user to an identity provider. The user will be stored in the selected identity provider instead of the
                            default one.
                        </FieldDescription>
                    </Field>
                </CardContent>
            </Card>

            <Card>
                <CardHeader>
                    <CardTitle>Additional information</CardTitle>
                    <CardDescription>
                        Custom information about the user. These details will be available in the user&apos;s profile.
                    </CardDescription>
                </CardHeader>
                <CardContent>
                    <AttributesEditor attributes={attributes} editable onChange={setAttributes} />
                </CardContent>
            </Card>

            <div className="flex items-center justify-end gap-2">
                <Button variant="outline" type="button" onClick={() => navigate(listPath)}>
                    Cancel
                </Button>
                <Button type="submit" disabled={!valid || create.isPending}>
                    {create.isPending ? 'Creating…' : 'Create user'}
                </Button>
            </div>
        </form>
    );
}
