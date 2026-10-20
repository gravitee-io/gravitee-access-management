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
    FieldLabel,
    Input,
    Label,
    PageFocused,
    RadioGroup,
    RadioGroupItem,
    Textarea,
    cn,
    toast,
} from '@gravitee/graphene-core';
import {
    AppWindowIcon,
    ArrowLeftIcon,
    ArrowRightIcon,
    CheckIcon,
    GlobeIcon,
    LockIcon,
    MonitorIcon,
    ServerIcon,
    type LucideIcon,
} from '@gravitee/graphene-core/icons';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
    type ApplicationDetail,
    type CimdPreview,
    type NewApplicationType,
    createApplication,
    createApplicationFromCimd,
    validateCimd,
} from '../../lib/api/applications';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { useCurrentDomain } from '../../lib/session/domain';
import { CimdReviewStep } from './CimdReviewStep';
import { ClientSecretDialog } from './ClientSecretDialog';

type CreationMode = 'manual' | 'cimd';

interface Draft {
    readonly type?: NewApplicationType;
    readonly mode: CreationMode;
    readonly name: string;
    readonly description: string;
    readonly redirectUri: string;
    readonly clientId: string;
    readonly clientSecret: string;
    readonly cimdUrl: string;
    readonly cimdPreview?: CimdPreview;
    readonly cimdClientName: string;
}

const EMPTY: Draft = {
    mode: 'manual',
    name: '',
    description: '',
    redirectUri: '',
    clientId: '',
    clientSecret: '',
    cimdUrl: '',
    cimdClientName: '',
};

/** The cards of the Angular step 1, in its order. */
const APPLICATION_TYPES: Record<NewApplicationType, { label: string; description: string; examples: string; icon: LucideIcon }> = {
    WEB: { label: 'Web', description: 'Traditional web apps', examples: '.NET, Java', icon: GlobeIcon },
    BROWSER: { label: 'Single-Page App', description: 'JavaScript front-end apps', examples: 'Angular, React, VueJS', icon: AppWindowIcon },
    NATIVE: { label: 'Native', description: 'Mobile, Desktop apps', examples: 'iOS, Android', icon: MonitorIcon },
    SERVICE: {
        label: 'Backend to Backend',
        description: 'Machine-to-Machine apps',
        examples: 'Shell script, daemon, CLI',
        icon: ServerIcon,
    },
    RESOURCE_SERVER: { label: 'Resource Server', description: 'Resource Server apps', examples: 'APIs', icon: LockIcon },
};

function Stepper({ steps, current }: Readonly<{ steps: string[]; current: number }>) {
    return (
        <ol className="flex items-center justify-center gap-2" aria-label="Progress">
            {steps.map((label, index) => (
                <li key={label} className="flex items-center gap-2">
                    <span
                        className={cn(
                            'flex size-6 items-center justify-center rounded-full text-xs font-medium',
                            index <= current ? 'bg-primary text-primary-foreground' : 'border border-border text-muted-foreground',
                        )}
                        aria-current={index === current ? 'step' : undefined}
                    >
                        {index < current ? <CheckIcon className="size-3" aria-hidden /> : index + 1}
                    </span>
                    <span className={cn('text-sm', index === current ? 'font-medium' : 'text-muted-foreground')}>{label}</span>
                    {index < steps.length - 1 ? <span className="h-px w-8 bg-border" aria-hidden /> : null}
                </li>
            ))}
        </ol>
    );
}

/**
 * The Angular `ApplicationCreationComponent`, a linear stepper: the platform, the settings, and for a CIMD import the
 * review of the fetched document. A manual create shows the client secret once before it lands on the application.
 */
export function CreateApplicationPage() {
    const { domain, ref, basePath, domainsPath } = useCurrentDomain();
    const navigate = useNavigate();
    const queryClient = useQueryClient();
    const [step, setStep] = useState(0);
    const [draft, setDraft] = useState<Draft>(EMPTY);
    const [created, setCreated] = useState<ApplicationDetail | null>(null);
    const listPath = `${basePath}/applications`;
    useBreadcrumbs([
        { label: 'Domains', to: domainsPath },
        { label: domain.name, to: basePath },
        { label: 'Applications', to: listPath },
        { label: 'New application' },
    ]);

    const cimdEnabled = !!domain.oidc?.cimdSettings?.enabled;
    const isCimd = cimdEnabled && draft.mode === 'cimd';
    const steps = isCimd ? ['Platform', 'Settings', 'Confirm'] : ['Platform', 'Settings'];
    const needsRedirectUri = draft.type !== 'SERVICE';
    const types = (Object.keys(APPLICATION_TYPES) as NewApplicationType[]).filter(
        type => type !== 'RESOURCE_SERVER' || domain.uma?.enabled,
    );
    const set = (patch: Partial<Draft>) => setDraft(current => ({ ...current, ...patch }));

    const settingsValid = isCimd ? !!draft.cimdUrl : !!draft.name && (!needsRedirectUri || !!draft.redirectUri);
    const resolvedCimdName = draft.cimdPreview?.metadata?.client_name || draft.cimdClientName;

    const finish = (application: ApplicationDetail) => {
        toast.success(`Application ${application.name} created`);
        navigate(`${listPath}/${application.id}`);
    };

    const validate = useMutation({
        mutationFn: () => validateCimd(ref, draft.cimdUrl),
        onSuccess: preview => {
            set({ cimdPreview: preview, cimdClientName: '' });
            setStep(2);
        },
        onError: error => toast.error(error.message || 'Unable to validate CIMD URL'),
    });

    const create = useMutation({
        mutationFn: (): Promise<ApplicationDetail> => {
            const type = draft.type as NewApplicationType;
            if (isCimd) {
                return createApplicationFromCimd(ref, {
                    name: resolvedCimdName,
                    type,
                    description: draft.description || undefined,
                    cimdUrl: draft.cimdUrl,
                    clientName: draft.cimdPreview?.missing?.clientName && draft.cimdClientName ? draft.cimdClientName : undefined,
                });
            }
            return createApplication(ref, {
                name: draft.name,
                type,
                description: draft.description || undefined,
                clientId: draft.clientId || undefined,
                clientSecret: draft.clientSecret || undefined,
                redirectUris: needsRedirectUri && draft.redirectUri ? [draft.redirectUri] : null,
            });
        },
        onSuccess: application => {
            void queryClient.invalidateQueries({ queryKey: ['applications', ref] });
            if (isCimd) {
                finish(application);
            } else {
                setCreated(application);
            }
        },
        onError: error =>
            toast.error(error.message || (isCimd ? 'Unable to create application from CIMD URL' : 'Unable to create the application')),
    });
    const busy = validate.isPending || create.isPending;

    return (
        <PageFocused>
            <div className="space-y-6">
                <div className="space-y-1">
                    <Button variant="ghost" size="sm" className="-ml-2" onClick={() => navigate(listPath)}>
                        <ArrowLeftIcon aria-hidden />
                        Applications
                    </Button>
                    <h1 className="text-2xl font-semibold">Create an application</h1>
                    <p className="text-sm text-muted-foreground">
                        Set up the connection between your software and the authorization server, for authentication and account management.
                    </p>
                </div>

                <Stepper steps={steps} current={step} />

                {step === 0 ? (
                    <div className="grid gap-3 sm:grid-cols-2" role="radiogroup" aria-label="Application type">
                        {types.map(type => {
                            const meta = APPLICATION_TYPES[type];
                            const Icon = meta.icon;
                            const selected = draft.type === type;
                            return (
                                <button
                                    key={type}
                                    type="button"
                                    role="radio"
                                    aria-checked={selected}
                                    onClick={() => set({ type })}
                                    className={cn(
                                        'flex items-start gap-3 rounded-lg border border-border p-4 text-left transition-colors hover:border-primary',
                                        selected && 'border-primary bg-primary/5 ring-2 ring-primary',
                                    )}
                                >
                                    <Icon
                                        className={cn('mt-0.5 size-5', selected ? 'text-primary' : 'text-muted-foreground')}
                                        aria-hidden
                                    />
                                    <span className="flex flex-col gap-0.5">
                                        <span className="font-medium">{meta.label}</span>
                                        <span className="text-sm text-muted-foreground">{meta.description}</span>
                                        <span className="text-xs text-muted-foreground">e.g. {meta.examples}</span>
                                    </span>
                                </button>
                            );
                        })}
                    </div>
                ) : null}

                {step === 1 && draft.type ? (
                    <div className="space-y-4">
                        {cimdEnabled ? (
                            <Card>
                                <CardHeader>
                                    <CardTitle>Import configuration</CardTitle>
                                </CardHeader>
                                <CardContent>
                                    <RadioGroup
                                        value={draft.mode}
                                        onValueChange={mode =>
                                            // Reset the other mode's fields so a stale preview is never submitted.
                                            set(
                                                mode === 'cimd'
                                                    ? { mode: 'cimd', name: '', redirectUri: '', clientId: '', clientSecret: '' }
                                                    : { mode: 'manual', cimdUrl: '', cimdPreview: undefined, cimdClientName: '' },
                                            )
                                        }
                                    >
                                        <div className="flex items-start gap-3">
                                            <RadioGroupItem id="mode-manual" value="manual" />
                                            <Label htmlFor="mode-manual" className="flex flex-col items-start gap-0.5 font-normal">
                                                <span className="font-medium">Manual</span>
                                                <span className="text-sm text-muted-foreground">
                                                    Enter the application&apos;s details yourself.
                                                </span>
                                            </Label>
                                        </div>
                                        <div className="flex items-start gap-3">
                                            <RadioGroupItem id="mode-cimd" value="cimd" />
                                            <Label htmlFor="mode-cimd" className="flex flex-col items-start gap-0.5 font-normal">
                                                <span className="font-medium">CIMD</span>
                                                <span className="text-sm text-muted-foreground">
                                                    Bootstrap from a Client Identity Metadata Document URL.
                                                </span>
                                            </Label>
                                        </div>
                                    </RadioGroup>
                                </CardContent>
                            </Card>
                        ) : null}

                        {isCimd ? (
                            <Card>
                                <CardHeader>
                                    <CardTitle>CIMD URL</CardTitle>
                                </CardHeader>
                                <CardContent>
                                    <Field>
                                        <FieldLabel htmlFor="cimd-url">Document URL</FieldLabel>
                                        <Input
                                            id="cimd-url"
                                            type="url"
                                            value={draft.cimdUrl}
                                            placeholder="https://example.com/.well-known/oauth-client"
                                            onChange={event =>
                                                set({ cimdUrl: event.target.value, cimdPreview: undefined, cimdClientName: '' })
                                            }
                                        />
                                        <FieldDescription>
                                            This URL becomes the application&apos;s client_id. The name is taken from the document&apos;s{' '}
                                            <code>client_name</code> if present.
                                        </FieldDescription>
                                    </Field>
                                </CardContent>
                            </Card>
                        ) : (
                            <>
                                <Card>
                                    <CardHeader>
                                        <CardTitle>Application settings</CardTitle>
                                        <CardDescription>
                                            {APPLICATION_TYPES[draft.type].label} application in {domain.name}.
                                        </CardDescription>
                                    </CardHeader>
                                    <CardContent className="grid gap-5">
                                        <Field>
                                            <FieldLabel htmlFor="app-name">Name</FieldLabel>
                                            <Input
                                                id="app-name"
                                                placeholder="Enter the application name"
                                                value={draft.name}
                                                onChange={event => set({ name: event.target.value })}
                                            />
                                        </Field>
                                        <Field>
                                            <FieldLabel htmlFor="app-description">Description</FieldLabel>
                                            <Textarea
                                                id="app-description"
                                                rows={3}
                                                placeholder="Enter the application description"
                                                value={draft.description}
                                                onChange={event => set({ description: event.target.value })}
                                            />
                                        </Field>
                                        {needsRedirectUri ? (
                                            <Field>
                                                <FieldLabel htmlFor="app-redirect">Redirect URI</FieldLabel>
                                                <Input
                                                    id="app-redirect"
                                                    type="url"
                                                    placeholder="https://myapp.example.com/callback"
                                                    value={draft.redirectUri}
                                                    onChange={event => set({ redirectUri: event.target.value })}
                                                />
                                                <FieldDescription>
                                                    Where users are sent after logging in.{' '}
                                                    {domain.oidc?.clientRegistrationSettings?.allowRedirectUriParamsExpressionLanguage
                                                        ? 'Supports expression language and dynamic parameters.'
                                                        : 'Expression language and dynamic parameters are turned off for this domain.'}
                                                </FieldDescription>
                                            </Field>
                                        ) : null}
                                    </CardContent>
                                </Card>
                                <Card>
                                    <CardHeader>
                                        <CardTitle>OAuth 2.0 settings</CardTitle>
                                        <CardDescription>Optional. Generated for you when left empty.</CardDescription>
                                    </CardHeader>
                                    <CardContent className="grid gap-5 sm:grid-cols-2">
                                        <Field>
                                            <FieldLabel htmlFor="app-client-id">Client ID</FieldLabel>
                                            <Input
                                                id="app-client-id"
                                                value={draft.clientId}
                                                onChange={event => set({ clientId: event.target.value })}
                                            />
                                        </Field>
                                        <Field>
                                            <FieldLabel htmlFor="app-client-secret">Client secret</FieldLabel>
                                            <Input
                                                id="app-client-secret"
                                                value={draft.clientSecret}
                                                onChange={event => set({ clientSecret: event.target.value })}
                                            />
                                        </Field>
                                    </CardContent>
                                </Card>
                            </>
                        )}
                    </div>
                ) : null}

                {step === 2 && draft.cimdPreview ? (
                    <CimdReviewStep
                        preview={draft.cimdPreview}
                        clientName={draft.cimdClientName}
                        onClientNameChange={cimdClientName => set({ cimdClientName })}
                    />
                ) : null}

                <div className="flex items-center justify-between">
                    <Button
                        variant="outline"
                        onClick={() => (step === 0 ? navigate(listPath) : setStep(current => current - 1))}
                        disabled={busy}
                    >
                        <ArrowLeftIcon aria-hidden />
                        {step === 0 ? 'Cancel' : 'Back'}
                    </Button>
                    {step === 0 ? (
                        <Button onClick={() => setStep(1)} disabled={!draft.type}>
                            Next
                            <ArrowRightIcon aria-hidden />
                        </Button>
                    ) : step === 1 && isCimd ? (
                        <Button onClick={() => validate.mutate()} disabled={!settingsValid || busy}>
                            {validate.isPending ? 'Validating…' : 'Next'}
                            <ArrowRightIcon aria-hidden />
                        </Button>
                    ) : step === 1 ? (
                        <Button onClick={() => create.mutate()} disabled={!settingsValid || busy}>
                            {create.isPending ? 'Creating…' : 'Create application'}
                        </Button>
                    ) : (
                        <Button onClick={() => create.mutate()} disabled={!resolvedCimdName || busy}>
                            {create.isPending ? 'Creating…' : 'Confirm and create'}
                        </Button>
                    )}
                </div>
            </div>

            <ClientSecretDialog
                secret={created ? (created.settings?.oauth?.clientSecret ?? '') : null}
                onDone={() => created && finish(created)}
            />
        </PageFocused>
    );
}
