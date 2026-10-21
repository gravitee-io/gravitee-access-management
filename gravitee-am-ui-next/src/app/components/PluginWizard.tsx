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
    Field,
    FieldError,
    FieldLabel,
    Input,
    InputGroup,
    InputGroupAddon,
    InputGroupInput,
    JsonSchemaForm,
    jsonSchemaResolver,
    PageFocused,
    Spinner,
} from '@gravitee/graphene-core';
import { ArrowLeftIcon, ArrowRightIcon, DatabaseIcon, LockIcon, SearchIcon } from '@gravitee/graphene-core/icons';
import { useQuery } from '@tanstack/react-query';
import { type ReactNode, useMemo, useState } from 'react';
import { type Control, type Resolver, type ResolverResult, useForm } from 'react-hook-form';
import { Link } from 'react-router-dom';
import { type AdaptedSchema, adaptAmSchema, withSchemaDefaults } from '../../lib/forms/am-schema';
import { useLicense, LicenseGuard } from '../../lib/session/license';
import { ErrorPanel } from './ErrorPanel';

/** The plugin fields the wizard reads. The identity provider plugins carry all of them. */
export interface WizardPlugin {
    readonly id: string;
    readonly name: string;
    readonly displayName?: string;
    /** A data URI. */
    readonly icon?: string;
    readonly labels?: readonly string[];
    readonly feature?: string;
}

// A type alias, not an interface: Graphene's resolvers constrain the values to `Record<string, unknown>`.
export type PluginWizardValues = {
    name: string;
    config: Record<string, unknown>;
};

interface PluginWizardProps<P extends WizardPlugin> {
    readonly title: string;
    readonly back: { readonly to: string; readonly label: string };
    /** Step 1 text, from the Angular step 1 template. */
    readonly pickDescription: string;
    /** Step 2 text, from the Angular step 2 template. */
    readonly configureDescription: string;
    /** The plural noun of the filter box, for example `providers`. */
    readonly noun: string;
    readonly plugins: readonly P[] | undefined;
    readonly pluginsError: Error | null;
    /** The first element of the schema query key, for example `identity-provider-schema`. */
    readonly schemaKey: string;
    readonly getSchema: (plugin: P) => Promise<Record<string, unknown>>;
    readonly onSelect?: (plugin: P) => void;
    /** Host fields for step 2, below the name. */
    readonly extraFields?: (plugin: P) => ReactNode;
    readonly creating: boolean;
    readonly onCreate: (plugin: P, values: PluginWizardValues) => void;
}

const STEPS = ['Platform', 'Settings'] as const;

const nameResolver: Resolver<PluginWizardValues> = async (values): Promise<ResolverResult<PluginWizardValues>> =>
    values.name.trim() ? { values, errors: {} } : { values: {}, errors: { name: { type: 'required', message: 'Name is required' } } };

function displayName(plugin: WizardPlugin): string {
    return plugin.displayName ?? plugin.name;
}

/** The Angular plugin filter: the name, the display name and the labels, without case. */
function matches(plugin: WizardPlugin, filter: string): boolean {
    const needle = filter.trim().toLowerCase();
    return !needle || [plugin.name, plugin.displayName ?? '', ...(plugin.labels ?? [])].some(field => field.toLowerCase().includes(needle));
}

function StepIndicator({ step }: { readonly step: number }) {
    return (
        <nav aria-label="Progress" className="flex items-center justify-center gap-2">
            {STEPS.map((label, i) => (
                <div key={label} className="flex items-center gap-2" aria-current={i === step ? 'step' : undefined}>
                    <div className="flex items-center gap-1.5">
                        <div
                            className={`flex size-6 items-center justify-center rounded-full text-xs font-medium ${
                                i <= step ? 'bg-primary text-primary-foreground' : 'border border-border text-muted-foreground'
                            }`}
                        >
                            {i + 1}
                        </div>
                        <span className={`text-sm ${i <= step ? 'font-medium' : 'text-muted-foreground'}`}>{label}</span>
                    </div>
                    {i < STEPS.length - 1 && <div className="h-px w-8 bg-border" />}
                </div>
            ))}
        </nav>
    );
}

function PluginCard({
    plugin,
    selected,
    onSelect,
}: {
    readonly plugin: WizardPlugin;
    readonly selected: boolean;
    readonly onSelect: () => void;
}) {
    const missing = useLicense().isMissing(plugin.feature);
    return (
        <LicenseGuard feature={plugin.feature}>
            <button
                type="button"
                role="radio"
                aria-checked={selected}
                aria-label={missing ? `${displayName(plugin)}, Enterprise` : displayName(plugin)}
                onClick={onSelect}
                className={`flex items-center gap-3 rounded-lg border border-border p-4 text-left transition-colors hover:border-primary ${
                    selected ? 'border-primary bg-primary/5 ring-2 ring-primary' : ''
                }`}
            >
                {plugin.icon ? (
                    <img src={plugin.icon} alt="" className="size-8 shrink-0 object-contain" />
                ) : (
                    <DatabaseIcon className="size-8 shrink-0 text-muted-foreground" aria-hidden />
                )}
                <span className="flex-1 font-medium">{displayName(plugin)}</span>
                {missing && (
                    <Badge variant="outline">
                        <LockIcon aria-hidden />
                        Enterprise
                    </Badge>
                )}
            </button>
        </LicenseGuard>
    );
}

function ConfigureForm<P extends WizardPlugin>({
    plugin,
    adapted,
    extraFields,
    creating,
    onBack,
    onCreate,
}: {
    readonly plugin: P;
    readonly adapted: AdaptedSchema;
    readonly extraFields?: (plugin: P) => ReactNode;
    readonly creating: boolean;
    readonly onBack: () => void;
    readonly onCreate: (plugin: P, values: PluginWizardValues) => void;
}) {
    const resolver = useMemo(
        () =>
            composeResolvers<PluginWizardValues>(
                nameResolver,
                jsonSchemaResolver<PluginWizardValues>(adapted.schema, { basePath: 'config' }),
            ),
        [adapted.schema],
    );
    // The Angular json-schema-form fills the schema defaults, so the user does not pick a required field that has one.
    const form = useForm<PluginWizardValues>({
        resolver,
        mode: 'onTouched',
        criteriaMode: 'all',
        defaultValues: { name: '', config: withSchemaDefaults(adapted.schema, {}) },
    });
    const nameError = form.formState.errors.name;

    return (
        <form noValidate onSubmit={form.handleSubmit(values => onCreate(plugin, values))} className="flex flex-col gap-6">
            <Card>
                <CardHeader>
                    <CardTitle>{displayName(plugin)}</CardTitle>
                    {adapted.schema.description && <CardDescription>{adapted.schema.description}</CardDescription>}
                </CardHeader>
                <CardContent className="flex flex-col gap-4">
                    {adapted.unsupported.length > 0 && (
                        <Alert>
                            <AlertTitle>Some fields use a plain input</AlertTitle>
                            <AlertDescription>{adapted.unsupported.join(', ')}</AlertDescription>
                        </Alert>
                    )}
                    <Field data-invalid={!!nameError}>
                        <FieldLabel htmlFor="plugin-name">Name</FieldLabel>
                        <Input id="plugin-name" aria-invalid={!!nameError} {...form.register('name')} />
                        {nameError && <FieldError errors={[nameError]} />}
                    </Field>
                    {extraFields?.(plugin)}
                    <JsonSchemaForm schema={adapted.schema} control={form.control as unknown as Control} name="config" />
                </CardContent>
            </Card>
            <div className="flex items-center justify-between">
                <Button type="button" variant="outline" onClick={onBack}>
                    <ArrowLeftIcon aria-hidden />
                    Back
                </Button>
                <Button type="submit" disabled={creating}>
                    {creating ? 'Creating…' : 'Create'}
                </Button>
            </div>
        </form>
    );
}

/**
 * The two-step plugin creation flow of the Angular console: pick a plugin type, then name it and fill its schema form.
 * A plugin that the licence lacks shows a lock, and a click on it opens the upgrade dialog.
 */
export function PluginWizard<P extends WizardPlugin>({
    title,
    back,
    pickDescription,
    configureDescription,
    noun,
    plugins,
    pluginsError,
    schemaKey,
    getSchema,
    onSelect,
    extraFields,
    creating,
    onCreate,
}: PluginWizardProps<P>) {
    const [step, setStep] = useState(0);
    const [selected, setSelected] = useState<P | null>(null);
    const [filter, setFilter] = useState('');
    const { data: adapted, error: schemaError } = useQuery({
        queryKey: [schemaKey, selected?.id],
        queryFn: async () => adaptAmSchema(await getSchema(selected!)),
        enabled: step === 1 && !!selected,
        staleTime: Infinity,
    });
    const shown = (plugins ?? []).filter(plugin => matches(plugin, filter));

    return (
        <PageFocused>
            <div className="flex flex-col gap-6">
                <div className="space-y-1">
                    <Button asChild variant="ghost" size="sm" className="-ml-2">
                        <Link to={back.to}>
                            <ArrowLeftIcon aria-hidden />
                            {back.label}
                        </Link>
                    </Button>
                    <h1>{title}</h1>
                    <p className="text-sm text-muted-foreground">{step === 0 ? pickDescription : configureDescription}</p>
                </div>
                <StepIndicator step={step} />
                {step === 0 ? (
                    pluginsError ? (
                        <ErrorPanel description={`Could not load the available ${noun}.`} error={pluginsError} />
                    ) : !plugins ? (
                        <Spinner />
                    ) : (
                        <>
                            <InputGroup className="w-64">
                                <InputGroupAddon>
                                    <SearchIcon aria-hidden />
                                </InputGroupAddon>
                                <InputGroupInput
                                    type="search"
                                    aria-label={`Filter ${noun}`}
                                    placeholder={`Filter ${noun}…`}
                                    value={filter}
                                    onChange={event => setFilter(event.target.value)}
                                />
                            </InputGroup>
                            {shown.length === 0 ? (
                                <p className="text-sm text-muted-foreground">No {noun} match the filter.</p>
                            ) : (
                                <div role="radiogroup" aria-label={title} className="grid gap-3 sm:grid-cols-2">
                                    {shown.map(plugin => (
                                        <PluginCard
                                            key={plugin.id}
                                            plugin={plugin}
                                            selected={selected?.id === plugin.id}
                                            onSelect={() => {
                                                setSelected(plugin);
                                                onSelect?.(plugin);
                                            }}
                                        />
                                    ))}
                                </div>
                            )}
                            <div className="flex items-center justify-end">
                                <Button type="button" disabled={!selected} onClick={() => setStep(1)}>
                                    Next
                                    <ArrowRightIcon aria-hidden />
                                </Button>
                            </div>
                        </>
                    )
                ) : schemaError ? (
                    <ErrorPanel description="Could not load the plugin configuration form." error={schemaError} />
                ) : !adapted ? (
                    <Spinner />
                ) : (
                    <ConfigureForm
                        key={selected!.id}
                        plugin={selected!}
                        adapted={adapted}
                        extraFields={extraFields}
                        creating={creating}
                        onBack={() => setStep(0)}
                        onCreate={onCreate}
                    />
                )}
            </div>
        </PageFocused>
    );
}
