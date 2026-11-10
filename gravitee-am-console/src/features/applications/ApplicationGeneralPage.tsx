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
    CopyableText,
    Field,
    FieldError,
    FieldLabel,
    Input,
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
    Textarea,
    toast,
} from '@gravitee/graphene-core';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { type FormEvent, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ConfirmDialog } from '../../app/components/ConfirmDialog';
import { PageHeader } from '../../app/components/PageHeader';
import { SaveBar } from '../../app/components/SaveBar';
import { SettingToggle } from '../../app/components/SettingToggle';
import { ValueListInput } from '../../app/components/ValueListInput';
import {
    type Application,
    type ApplicationType,
    deleteApplication,
    patchApplication,
    updateApplicationType,
} from '../../lib/api/applications';
import { useCurrentDomain } from '../../lib/session/domain';
import { usePermissions } from '../../lib/session/permissions';
import { applicationQueryKey, useApplication } from './ApplicationContext';
import { APPLICATION_TYPE_LABELS } from './application-types';

const FORM_ID = 'application-general-form';

type FormState = {
    readonly name: string;
    readonly description: string;
    readonly redirectUris: readonly string[];
    readonly postLogoutRedirectUris: readonly string[];
    readonly requestUris: readonly string[];
    readonly singleSignOut: boolean;
    readonly silentReAuthentication: boolean;
    readonly skipConsent: boolean;
    readonly template: boolean;
};

function fromApplication(application: Application): FormState {
    const oauth = application.settings?.oauth;
    return {
        name: application.name,
        description: application.description ?? '',
        redirectUris: oauth?.redirectUris ?? [],
        postLogoutRedirectUris: oauth?.postLogoutRedirectUris ?? [],
        requestUris: oauth?.requestUris ?? [],
        singleSignOut: oauth?.singleSignOut ?? false,
        silentReAuthentication: oauth?.silentReAuthentication ?? false,
        skipConsent: application.settings?.advanced?.skipConsent ?? false,
        template: application.template ?? false,
    };
}

type UriList = 'redirectUris' | 'postLogoutRedirectUris' | 'requestUris';

type Pending =
    | { readonly kind: 'remove-uri'; readonly list: UriList; readonly uri: string }
    | { readonly kind: 'change-type'; readonly type: ApplicationType }
    | { readonly kind: 'delete' };

const REMOVE_URI_TEXTS: Record<UriList, { readonly title: string; readonly description: string }> = {
    redirectUris: { title: 'Remove redirect URI', description: 'Are you sure you want to remove this redirect URI ?' },
    postLogoutRedirectUris: { title: 'Remove logout redirect URI', description: 'Are you sure you want to remove this redirect URI ?' },
    requestUris: { title: 'Remove request URI', description: 'Are you sure you want to remove this request URI ?' },
};

function confirmTexts(pending: Pending) {
    if (pending.kind === 'remove-uri') {
        return { ...REMOVE_URI_TEXTS[pending.list], confirmLabel: 'Remove' };
    }
    if (pending.kind === 'change-type') {
        return {
            title: 'Change application type',
            description: 'Are you sure you want to change the type of the application ?',
            confirmLabel: 'Change type',
        };
    }
    return { title: 'Delete Application', description: 'Are you sure you want to delete this application ?', confirmLabel: 'Delete' };
}

const CHANGEABLE_TYPES: readonly ApplicationType[] = ['web', 'browser', 'native', 'service', 'resource_server'];

export function ApplicationGeneralPage() {
    const { application, applicationPath, listPath } = useApplication();
    const { domain, ref } = useCurrentDomain();
    const navigate = useNavigate();
    const queryClient = useQueryClient();
    const { has } = usePermissions();
    const canEdit = has('application_settings_update');
    const canDelete = has('application_settings_delete');
    const [form, setForm] = useState<FormState>(() => fromApplication(application));
    const [nameTouched, setNameTouched] = useState(false);
    // The dialog reads the last action, so its text stays the same while it closes.
    const [pending, setPending] = useState<Pending>({ kind: 'delete' });
    const [confirmOpen, setConfirmOpen] = useState(false);

    const [syncedApplication, setSyncedApplication] = useState(application);
    if (syncedApplication !== application) {
        setSyncedApplication(application);
        setForm(fromApplication(application));
        setNameTouched(false);
    }

    const queryKey = applicationQueryKey(ref, application.id);
    const save = useMutation({
        mutationFn: () =>
            patchApplication(ref, application.id, {
                name: form.name,
                description: form.description,
                settings: {
                    oauth: {
                        redirectUris: [...form.redirectUris],
                        requestUris: [...form.requestUris],
                        postLogoutRedirectUris: [...form.postLogoutRedirectUris],
                        singleSignOut: form.singleSignOut,
                        silentReAuthentication: form.silentReAuthentication,
                    },
                    advanced: { skipConsent: form.skipConsent },
                },
                template: form.template,
            }),
        onSuccess: updated => {
            queryClient.setQueryData(queryKey, updated);
            void queryClient.invalidateQueries({ queryKey: ['applications'] });
            toast.success('Application updated');
        },
        onError: error => toast.error(`Could not update the application. ${error.message}`),
    });
    const changeType = useMutation({
        mutationFn: (type: ApplicationType) => updateApplicationType(ref, application.id, type.toUpperCase() as Uppercase<ApplicationType>),
        onSuccess: updated => {
            queryClient.setQueryData(queryKey, updated);
            void queryClient.invalidateQueries({ queryKey: ['applications'] });
            toast.success('Application type changed');
            navigate(applicationPath);
        },
        onError: error => toast.error(`Could not change the application type. ${error.message}`),
    });
    const remove = useMutation({
        mutationFn: () => deleteApplication(ref, application.id),
        onSuccess: () => {
            void queryClient.invalidateQueries({ queryKey: ['applications'] });
            toast.success('Application deleted');
            navigate(listPath);
            queryClient.removeQueries({ queryKey, exact: true });
        },
        onError: error => toast.error(`Could not delete the application. ${error.message}`),
    });

    const dirty = JSON.stringify(form) !== JSON.stringify(fromApplication(application));
    const nameMissing = form.name.trim() === '';
    const set = (patch: Partial<FormState>) => setForm(current => ({ ...current, ...patch }));
    const ask = (action: Pending) => {
        setPending(action);
        setConfirmOpen(true);
    };
    const confirm = () => {
        setConfirmOpen(false);
        if (pending.kind === 'remove-uri') {
            set({ [pending.list]: form[pending.list].filter(uri => uri !== pending.uri) });
        } else if (pending.kind === 'change-type') {
            changeType.mutate(pending.type);
        } else {
            remove.mutate();
        }
    };
    const submit = (event: FormEvent) => {
        event.preventDefault();
        if (!nameMissing) {
            save.mutate();
        }
    };

    const userFacing = application.type !== 'service';
    const cimd = domain.oidc?.cimdSettings;
    const isCimdTemplate = !!cimd?.enabled && cimd.templateId === application.id;
    const types = CHANGEABLE_TYPES.filter(type => type !== 'resource_server' || domain.uma?.enabled);
    const texts = confirmTexts(pending);
    const uriList = (list: UriList, label: string, duplicateNoun: string, placeholder: string, emptyText: string) => (
        <ValueListInput
            label={label}
            values={form[list]}
            editable={canEdit}
            placeholder={placeholder}
            emptyText={emptyText}
            onChange={values => set({ [list]: values })}
            onDuplicate={uri => toast.error(`Error : ${duplicateNoun} "${uri}" already exists`)}
            onRemoveRequest={uri => ask({ kind: 'remove-uri', list, uri })}
        />
    );

    return (
        <div className="flex flex-col gap-6">
            <PageHeader
                title="General"
                description="How this application interacts with the authorization server to request tokens and access protected resources."
            />

            <Card>
                <CardHeader>
                    <CardTitle className="text-base">Details</CardTitle>
                </CardHeader>
                <CardContent className="grid gap-5">
                    <Field data-invalid={nameTouched && nameMissing}>
                        <FieldLabel htmlFor="application-name">Name</FieldLabel>
                        <Input
                            id="application-name"
                            value={form.name}
                            disabled={!canEdit}
                            aria-invalid={nameTouched && nameMissing}
                            aria-describedby={nameTouched && nameMissing ? 'application-name-error' : undefined}
                            onChange={event => set({ name: event.target.value })}
                            onBlur={() => setNameTouched(true)}
                        />
                        {nameTouched && nameMissing && <FieldError id="application-name-error">A name is required.</FieldError>}
                    </Field>
                    <Field role="group" aria-labelledby="application-domain">
                        <p id="application-domain" className="text-sm font-medium">
                            Domain
                        </p>
                        <div>
                            <CopyableText className="max-w-full" value={domain.path ?? ''} />
                        </div>
                    </Field>
                    <Field>
                        <FieldLabel htmlFor="application-description">Description</FieldLabel>
                        <Textarea
                            id="application-description"
                            rows={3}
                            value={form.description}
                            disabled={!canEdit}
                            onChange={event => set({ description: event.target.value })}
                        />
                    </Field>
                </CardContent>
            </Card>

            <form id={FORM_ID} className="flex flex-col gap-6" onSubmit={submit}>
                {userFacing && (
                    <>
                        <Card>
                            <CardHeader>
                                <CardTitle className="text-base">Redirect URIs</CardTitle>
                                <CardDescription>
                                    Where users may be sent after logging in. Restricting them prevents redirects to unsafe websites.{' '}
                                    {domain.oidc?.clientRegistrationSettings?.allowRedirectUriParamsExpressionLanguage
                                        ? 'Supports expression language and dynamic parameters.'
                                        : 'Expression language and dynamic parameters are turned off.'}
                                </CardDescription>
                            </CardHeader>
                            <CardContent>
                                {uriList(
                                    'redirectUris',
                                    'redirect URI',
                                    'redirect URI',
                                    'https://myapp.example.com/callback',
                                    'There are no redirect URIs.',
                                )}
                            </CardContent>
                        </Card>
                        <Card>
                            <CardHeader>
                                <CardTitle className="text-base">Post-logout redirect URIs</CardTitle>
                                <CardDescription>Where users may be sent after signing out.</CardDescription>
                            </CardHeader>
                            <CardContent>
                                {uriList(
                                    'postLogoutRedirectUris',
                                    'post-logout redirect URI',
                                    'redirect URI',
                                    'https://myapp.example.com/',
                                    'There are no redirect URIs.',
                                )}
                            </CardContent>
                        </Card>
                        <Card>
                            <CardHeader>
                                <CardTitle className="text-base">Request URIs</CardTitle>
                                <CardDescription>
                                    URLs the application may pass as request_uri. Restricting them prevents server-side request forgery.
                                </CardDescription>
                            </CardHeader>
                            <CardContent>
                                {uriList(
                                    'requestUris',
                                    'request URI',
                                    'request URI',
                                    'https://myapp.example.com/request.jwt',
                                    'There are no request URIs.',
                                )}
                            </CardContent>
                        </Card>
                    </>
                )}

                <Card>
                    <CardHeader>
                        <CardTitle className="text-base">Behavior</CardTitle>
                    </CardHeader>
                    <CardContent className="grid gap-5">
                        {userFacing && (
                            <>
                                <SettingToggle
                                    id="application-single-sign-out"
                                    label="Single sign-out"
                                    description="On logout, also call the external identity provider's end-session endpoint, when it has one."
                                    checked={form.singleSignOut}
                                    disabled={!canEdit}
                                    onChange={singleSignOut => set({ singleSignOut })}
                                />
                                <SettingToggle
                                    id="application-silent-reauthentication"
                                    label="Silent re-authentication"
                                    description="Allow id_token_hint and prompt=none, so new tokens can be requested without user interaction."
                                    checked={form.silentReAuthentication}
                                    disabled={!canEdit}
                                    onChange={silentReAuthentication => set({ silentReAuthentication })}
                                />
                                <SettingToggle
                                    id="application-skip-consent"
                                    label="Skip user consent"
                                    description="Generally used for internal, trusted applications."
                                    checked={form.skipConsent}
                                    disabled={!canEdit}
                                    onChange={skipConsent => set({ skipConsent })}
                                />
                            </>
                        )}
                        <SettingToggle
                            id="application-template"
                            label="Use as registration template"
                            description={
                                isCimdTemplate
                                    ? 'This application is the CIMD template of the domain. Change the CIMD settings before changing its template mode.'
                                    : 'Serve as the base template for Dynamic Client Registration and CIMD ephemeral registrations.'
                            }
                            checked={form.template}
                            disabled={!canEdit || isCimdTemplate}
                            onChange={template => set({ template })}
                        />
                    </CardContent>
                </Card>
            </form>

            {canEdit && (
                <SaveBar
                    formId={FORM_ID}
                    dirty={dirty}
                    saving={save.isPending}
                    invalid={nameMissing}
                    onDiscard={() => {
                        setForm(fromApplication(application));
                        setNameTouched(false);
                    }}
                />
            )}

            {canDelete && (
                <Card>
                    <CardHeader>
                        <CardTitle className="text-base">Danger zone</CardTitle>
                    </CardHeader>
                    <CardContent className="flex flex-col divide-y divide-border">
                        {canEdit && (
                            <div className="flex flex-wrap items-center justify-between gap-4 pb-4">
                                <div className="flex flex-1 flex-col gap-0.5">
                                    <p className="text-sm font-medium">Change application type</p>
                                    <p className="text-sm text-muted-foreground">Some default values and options will be updated.</p>
                                </div>
                                <Select
                                    value={application.type}
                                    disabled={changeType.isPending}
                                    onValueChange={type => {
                                        const next = CHANGEABLE_TYPES.find(candidate => candidate === type);
                                        if (next && next !== application.type) {
                                            ask({ kind: 'change-type', type: next });
                                        }
                                    }}
                                >
                                    <SelectTrigger className="w-56" aria-label="Application type">
                                        <SelectValue />
                                    </SelectTrigger>
                                    <SelectContent>
                                        {types.map(type => (
                                            <SelectItem key={type} value={type}>
                                                {APPLICATION_TYPE_LABELS[type]}
                                            </SelectItem>
                                        ))}
                                    </SelectContent>
                                </Select>
                            </div>
                        )}
                        <div className="flex flex-wrap items-center justify-between gap-4 pt-4 first:pt-0">
                            <div className="flex flex-1 flex-col gap-0.5">
                                <p className="text-sm font-medium">Delete application</p>
                                <p className="text-sm text-muted-foreground">
                                    It will no longer be usable for authentication or to request tokens.
                                </p>
                            </div>
                            <Button variant="destructive" disabled={remove.isPending} onClick={() => ask({ kind: 'delete' })}>
                                Delete
                            </Button>
                        </div>
                    </CardContent>
                </Card>
            )}

            <ConfirmDialog
                open={confirmOpen}
                title={texts.title}
                description={texts.description}
                confirmLabel={texts.confirmLabel}
                destructive={pending.kind === 'delete'}
                onOpenChange={setConfirmOpen}
                onConfirm={confirm}
            />
        </div>
    );
}
