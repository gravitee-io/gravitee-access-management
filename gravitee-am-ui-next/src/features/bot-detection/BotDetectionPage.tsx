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
    AlertTitle,
    Badge,
    Button,
    Card,
    CardContent,
    CardDescription,
    CardHeader,
    CardTitle,
    composeResolvers,
    CopyableText,
    Field,
    FieldDescription,
    FieldError,
    FieldLabel,
    Input,
    JsonSchemaForm,
    jsonSchemaResolver,
    PageFocused,
    Skeleton,
    toast,
} from '@gravitee/graphene-core';
import { ArrowLeftIcon, CopyIcon } from '@gravitee/graphene-core/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { type Control, type Resolver, type ResolverResult, useForm } from 'react-hook-form';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ConfirmDialog } from '../../app/components/ConfirmDialog';
import { ErrorPanel } from '../../app/components/ErrorPanel';
import { NotFoundPanel } from '../../app/components/NotFoundPanel';
import { SaveBar } from '../../app/components/SaveBar';
import {
    type BotDetection,
    deleteBotDetection,
    getBotDetection,
    getBotDetectionSchema,
    listBotDetectionPlugins,
    updateBotDetection,
} from '../../lib/api/bot-detection';
import { isNotFound } from '../../lib/api/http';
import { type AdaptedSchema, adaptAmSchema, withSchemaDefaults } from '../../lib/forms/am-schema';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { useCurrentDomain } from '../../lib/session/domain';
import { LicenseGuard } from '../../lib/session/license';
import { Can, usePermissions } from '../../lib/session/permissions';
import { botDetectionTypeName, callServiceSnippet, configurationSchema, importScriptSnippet, RECAPTCHA_V3 } from './bot-detection-types';

// A type alias, not an interface: Graphene's resolvers constrain the values to `Record<string, unknown>`.
type BotDetectionValues = {
    name: string;
    config: Record<string, unknown>;
};

const nameResolver: Resolver<BotDetectionValues> = async (values): Promise<ResolverResult<BotDetectionValues>> =>
    values.name.trim() ? { values, errors: {} } : { values: {}, errors: { name: { type: 'required', message: 'Name is required' } } };

function parseConfiguration(botDetection: BotDetection): Record<string, unknown> {
    return JSON.parse(botDetection.configuration || '{}') as Record<string, unknown>;
}

/** The Angular side panel "Integrate Google reCaptcha v3 in your pages", from the saved configuration. */
function RecaptchaIntegration({ botDetection }: { readonly botDetection: BotDetection }) {
    if (botDetection.type !== RECAPTCHA_V3) return null;
    const configuration = parseConfiguration(botDetection);
    const siteKey = String(configuration.siteKey ?? '');
    const tokenParameterName = String(configuration.tokenParameterName ?? '');
    return (
        <Card>
            <CardHeader>
                <CardTitle className="text-base">Integration</CardTitle>
                <CardDescription>
                    Integrate Google reCAPTCHA v3 in your custom pages. See{' '}
                    <a className="underline" href="https://developers.google.com/recaptcha/docs/v3" target="_blank" rel="noreferrer">
                        the official documentation
                    </a>{' '}
                    for details.
                </CardDescription>
            </CardHeader>
            <CardContent className="grid gap-4">
                <div className="space-y-2">
                    <p className="text-sm font-medium">1. Import the JavaScript API</p>
                    <CopyableText className="max-w-full" value={importScriptSnippet(siteKey)} />
                </div>
                <div className="space-y-2">
                    <div className="flex items-center justify-between gap-2">
                        <p className="text-sm font-medium">2. Call the reCAPTCHA service before submitting the form</p>
                        <Button
                            type="button"
                            variant="outline"
                            size="icon"
                            aria-label="Copy to clipboard"
                            onClick={() =>
                                void navigator.clipboard?.writeText(callServiceSnippet(siteKey, tokenParameterName)).then(
                                    () => toast.success('Copied to clipboard'),
                                    () => undefined,
                                )
                            }
                        >
                            <CopyIcon aria-hidden />
                        </Button>
                    </div>
                    <pre className="overflow-x-auto rounded-md bg-muted p-3 font-mono text-xs">
                        {callServiceSnippet(siteKey, tokenParameterName)}
                    </pre>
                </div>
            </CardContent>
        </Card>
    );
}

interface BotDetectionFormProps {
    readonly botDetection: BotDetection;
    readonly adapted: AdaptedSchema;
    readonly feature?: string;
    readonly listPath: string;
}

function BotDetectionForm({ botDetection, adapted, feature, listPath }: BotDetectionFormProps) {
    const { ref } = useCurrentDomain();
    const navigate = useNavigate();
    const queryClient = useQueryClient();
    const canUpdate = usePermissions().has('domain_bot_detection_update');
    const [confirmDelete, setConfirmDelete] = useState(false);
    const resolver = useMemo(
        () =>
            composeResolvers<BotDetectionValues>(
                nameResolver,
                jsonSchemaResolver<BotDetectionValues>(adapted.schema, { basePath: 'config' }),
            ),
        [adapted],
    );
    const defaultValues = useMemo(
        () => ({ name: botDetection.name, config: withSchemaDefaults(adapted.schema, parseConfiguration(botDetection)) }),
        [botDetection, adapted],
    );
    const form = useForm<BotDetectionValues>({ resolver, mode: 'onTouched', criteriaMode: 'all', defaultValues });
    const save = useMutation({
        mutationFn: (values: BotDetectionValues) =>
            updateBotDetection(ref, botDetection.id, {
                name: values.name,
                type: botDetection.type,
                configuration: JSON.stringify(values.config),
            }),
        onSuccess: updated => {
            queryClient.setQueryData(['bot-detection', ref, botDetection.id], updated);
            void queryClient.invalidateQueries({ queryKey: ['bot-detections', ref] });
            form.reset({ name: updated.name, config: withSchemaDefaults(adapted.schema, parseConfiguration(updated)) });
            toast.success('Bot detection updated');
        },
        onError: error => toast.error(`Could not update the bot detection. ${error.message}`),
    });
    const remove = useMutation({
        mutationFn: () => deleteBotDetection(ref, botDetection.id),
        onSuccess: () => {
            void queryClient.invalidateQueries({ queryKey: ['bot-detections', ref] });
            toast.success('Bot detection deleted');
            navigate(listPath);
        },
        onError: error => toast.error(`Could not delete the bot detection. ${error.message}`),
    });
    const nameError = form.formState.errors.name;

    return (
        <form noValidate onSubmit={form.handleSubmit(values => save.mutate(values))} className="flex flex-col gap-6">
            <Card>
                <CardHeader>
                    <CardTitle className="text-base">General</CardTitle>
                </CardHeader>
                <CardContent className="grid gap-5">
                    <Field data-invalid={!!nameError}>
                        <FieldLabel htmlFor="bot-detection-name">Name</FieldLabel>
                        <Input id="bot-detection-name" aria-invalid={!!nameError} disabled={!canUpdate} {...form.register('name')} />
                        {nameError && <FieldError errors={[nameError]} />}
                    </Field>
                    <Field>
                        <FieldLabel htmlFor="bot-detection-type">Detection type</FieldLabel>
                        <Input id="bot-detection-type" value={botDetection.detectionType ?? ''} disabled />
                        <FieldDescription>Type of bot detection</FieldDescription>
                    </Field>
                </CardContent>
            </Card>

            <Card>
                <CardHeader>
                    <CardTitle className="text-base">Configuration</CardTitle>
                </CardHeader>
                <CardContent className="grid gap-5">
                    {adapted.unsupported.length > 0 && (
                        <Alert>
                            <AlertTitle>Some fields use a plain input</AlertTitle>
                            <AlertDescription>{adapted.unsupported.join(', ')}</AlertDescription>
                        </Alert>
                    )}
                    <JsonSchemaForm
                        schema={adapted.schema}
                        control={form.control as unknown as Control}
                        name="config"
                        disabled={!canUpdate}
                    />
                </CardContent>
            </Card>

            <LicenseGuard feature={feature}>
                <SaveBar canEdit={canUpdate} dirty={form.formState.isDirty} saving={save.isPending} onDiscard={() => form.reset()} />
            </LicenseGuard>

            <Can all={['domain_bot_detection_delete']}>
                <Card className="border-destructive/30">
                    <CardHeader>
                        <CardTitle className="text-base">Danger zone</CardTitle>
                    </CardHeader>
                    <CardContent>
                        <div className="flex flex-wrap items-center justify-between gap-4">
                            <div className="flex flex-col gap-0.5">
                                <p className="text-sm font-medium">Delete bot detection</p>
                                <p className="text-sm text-muted-foreground">
                                    Applications will no longer be able to use this bot detection plugin.
                                </p>
                            </div>
                            <Button type="button" variant="destructive" onClick={() => setConfirmDelete(true)}>
                                Delete
                            </Button>
                        </div>
                    </CardContent>
                </Card>
            </Can>

            <ConfirmDialog
                open={confirmDelete}
                title="Delete bot detection"
                description="Are you sure you want to delete this bot detection?"
                confirmLabel="Delete"
                destructive
                pending={remove.isPending}
                onOpenChange={setConfirmDelete}
                onConfirm={() => remove.mutate()}
            />
        </form>
    );
}

/** The Angular `BotDetectionComponent`: name, read-only detection type, plugin configuration, save and delete. */
export function BotDetectionPage() {
    const { botDetectionId = '' } = useParams();
    const { domain, ref, basePath, domainsPath } = useCurrentDomain();
    const listPath = `${basePath}/settings/bot-detection`;
    const { data: botDetection, error } = useQuery({
        queryKey: ['bot-detection', ref, botDetectionId],
        queryFn: () => getBotDetection(ref, botDetectionId),
    });
    const plugins = useQuery({ queryKey: ['bot-detection-plugins'], queryFn: listBotDetectionPlugins, staleTime: Infinity });
    const schema = useQuery({
        queryKey: ['bot-detection-schema', botDetection?.type],
        queryFn: () => getBotDetectionSchema(botDetection!.type),
        enabled: botDetection !== undefined,
        staleTime: Infinity,
    });
    const plugin = plugins.data?.find(candidate => candidate.id === botDetection?.type);
    const adapted = useMemo(() => (schema.data ? adaptAmSchema(configurationSchema(schema.data)) : undefined), [schema.data]);
    useBreadcrumbs([
        { label: 'Domains', to: domainsPath },
        { label: domain.name, to: `${basePath}/dashboard` },
        { label: 'Settings', to: `${basePath}/settings/general` },
        { label: 'Bot Detection', to: listPath },
        { label: botDetection?.name ?? '…' },
    ]);

    if (error) {
        return isNotFound(error) ? (
            <NotFoundPanel
                title="Bot detection not found"
                description={`No bot detection with this id exists in ${domain.name}. It may have been deleted.`}
                action={
                    <Button asChild variant="outline">
                        <Link to={listPath}>Back to bot detection</Link>
                    </Button>
                }
            />
        ) : (
            <ErrorPanel description="Could not load the bot detection." error={error} />
        );
    }
    const loadError = schema.error ?? plugins.error;
    if (loadError) {
        return <ErrorPanel description="Could not load the bot detection configuration." error={loadError} />;
    }
    if (!botDetection || !adapted || !plugins.data) {
        return <Skeleton className="h-64 w-full" />;
    }

    return (
        <PageFocused>
            <div className="flex flex-col gap-6">
                <div className="space-y-1">
                    <Button asChild variant="ghost" size="sm" className="-ml-2">
                        <Link to={listPath}>
                            <ArrowLeftIcon aria-hidden />
                            Bot detection
                        </Link>
                    </Button>
                    <div className="flex flex-wrap items-center gap-2">
                        <h1>{botDetection.name}</h1>
                        <Badge variant="outline">{botDetectionTypeName(botDetection.type, plugin?.name ?? botDetection.type)}</Badge>
                    </div>
                    <p className="text-sm text-muted-foreground">
                        Bot detection plugins protect the sign-in, sign-up and forgot password pages against bots.
                    </p>
                </div>
                <BotDetectionForm
                    key={botDetection.id}
                    botDetection={botDetection}
                    adapted={adapted}
                    feature={plugin?.feature}
                    listPath={listPath}
                />
                {/* Outside the form: CopyableText renders a button without a type, which would submit the form. */}
                <RecaptchaIntegration botDetection={botDetection} />
            </div>
        </PageFocused>
    );
}
