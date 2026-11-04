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
    PageFocused,
    Spinner,
} from '@gravitee/graphene-core';
import { ArrowLeftIcon, ArrowRightIcon, DatabaseIcon, LockIcon, SearchIcon } from '@gravitee/graphene-core/icons';
import { useQuery } from '@tanstack/react-query';
import { type KeyboardEvent, type ReactNode, useEffect, useMemo, useState } from 'react';
import { type Resolver, type ResolverResult, useForm } from 'react-hook-form';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { type AdaptedSchema, type SchemaResource, withSchemaDefaults } from '../../lib/forms/am-schema';
import { LicenseGuard, useLicense } from '../../lib/session/license';
import { ErrorPanel } from './ErrorPanel';
import { PluginSchemaForm, pluginSchemaResolver, useAdaptedSchema } from './PluginSchemaForm';

export interface WizardPlugin {
    readonly id: string;
    readonly name: string;
    readonly displayName?: string;
    /** A data URI. */
    readonly icon?: string;
    readonly labels?: readonly string[];
    readonly feature?: string;
}

// Graphene's resolvers constrain the values to `Record<string, unknown>`, which an interface does not satisfy.
export type PluginWizardValues = {
    name: string;
    config: Record<string, unknown>;
};

interface PluginWizardProps<P extends WizardPlugin> {
    readonly title: string;
    readonly back: { readonly to: string; readonly label: string };
    readonly pickDescription: string;
    readonly configureDescription: string;
    /** Plural, for example `providers`. */
    readonly noun: string;
    readonly plugins: readonly P[] | undefined;
    readonly pluginsError: Error | null;
    /** The first element of the schema query key, for example `identity-provider-schema`. */
    readonly schemaKey: string;
    readonly getSchema: (plugin: P) => Promise<Record<string, unknown>>;
    readonly resources?: readonly SchemaResource[];
    readonly onSelect?: (plugin: P) => void;
    /** Rendered below the name in step 2. */
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

function matches(plugin: WizardPlugin, filter: string): boolean {
    const needle = filter.trim().toLowerCase();
    return !needle || [plugin.name, plugin.displayName ?? '', ...(plugin.labels ?? [])].some(field => field.toLowerCase().includes(needle));
}

/** The step is in the URL, so the browser back goes from the settings to the plugin list. */
const STEP_PARAM = 'step';
const SETTINGS_STEP = 'settings';

function StepIndicator({
    step,
    canOpenSettings,
    onOpen,
}: {
    readonly step: number;
    readonly canOpenSettings: boolean;
    readonly onOpen: (step: number) => void;
}) {
    return (
        <nav aria-label="Progress" className="flex items-center justify-center gap-2">
            {STEPS.map((label, i) => (
                <div key={label} className="flex items-center gap-2">
                    <button
                        type="button"
                        aria-current={i === step ? 'step' : undefined}
                        disabled={i === step || (i === 1 && !canOpenSettings)}
                        onClick={() => onOpen(i)}
                        className="group flex items-center gap-1.5 rounded-md disabled:cursor-default"
                    >
                        <span
                            aria-hidden
                            className={`flex size-6 items-center justify-center rounded-full text-xs font-medium ${
                                i <= step ? 'bg-primary text-primary-foreground' : 'border border-border text-muted-foreground'
                            }`}
                        >
                            {i + 1}
                        </span>
                        <span
                            className={`text-sm group-enabled:group-hover:underline ${i <= step ? 'font-medium' : 'text-muted-foreground'}`}
                        >
                            {label}
                        </span>
                    </button>
                    {i < STEPS.length - 1 && <div className="h-px w-8 bg-border" />}
                </div>
            ))}
        </nav>
    );
}

const ARROW_STEPS: Record<string, number> = { ArrowDown: 1, ArrowRight: 1, ArrowUp: -1, ArrowLeft: -1 };

/** The radio group keys: an arrow moves the focus and selects, except a locked plugin, which opens the upgrade dialog on a click. */
function moveSelection(event: KeyboardEvent<HTMLButtonElement>) {
    const step = ARROW_STEPS[event.key];
    if (!step) {
        return;
    }
    event.preventDefault();
    const radios = [...(event.currentTarget.closest('[role="radiogroup"]')?.querySelectorAll<HTMLButtonElement>('[role="radio"]') ?? [])];
    const next = radios[(radios.indexOf(event.currentTarget) + step + radios.length) % radios.length];
    next.focus();
    if (next.dataset.locked === undefined) {
        next.click();
    }
}

function PluginCard({
    plugin,
    selected,
    focusable,
    onSelect,
}: {
    readonly plugin: WizardPlugin;
    readonly selected: boolean;
    readonly focusable: boolean;
    readonly onSelect: () => void;
}) {
    const missing = useLicense().isMissing(plugin.feature);
    return (
        <LicenseGuard feature={plugin.feature}>
            <button
                type="button"
                role="radio"
                aria-checked={selected}
                tabIndex={focusable ? 0 : -1}
                data-locked={missing || undefined}
                aria-label={missing ? `${displayName(plugin)}, Enterprise` : displayName(plugin)}
                onClick={onSelect}
                onKeyDown={moveSelection}
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
        () => composeResolvers<PluginWizardValues>(nameResolver, pluginSchemaResolver<PluginWizardValues>(adapted, 'config')),
        [adapted],
    );
    // Without the defaults here, a required field with a default fails validation until the user picks a value.
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
                    <Field data-invalid={!!nameError}>
                        <FieldLabel htmlFor="plugin-name">Name</FieldLabel>
                        <Input id="plugin-name" aria-invalid={!!nameError} {...form.register('name')} />
                        {nameError && <FieldError errors={[nameError]} />}
                    </Field>
                    {extraFields?.(plugin)}
                    <PluginSchemaForm adapted={adapted} control={form.control} name="config" />
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
    resources,
    onSelect,
    extraFields,
    creating,
    onCreate,
}: PluginWizardProps<P>) {
    const [params, setParams] = useSearchParams();
    const navigate = useNavigate();
    const [selected, setSelected] = useState<P | null>(null);
    const settingsInUrl = params.get(STEP_PARAM) === SETTINGS_STEP;
    // The pick does not survive a reload, so a reload on the settings shows the plugin list.
    const step = settingsInUrl && selected ? 1 : 0;
    useEffect(() => {
        if (settingsInUrl && !selected) {
            setParams(
                previous => {
                    const next = new URLSearchParams(previous);
                    next.delete(STEP_PARAM);
                    return next;
                },
                { replace: true },
            );
        }
    }, [settingsInUrl, selected, setParams]);
    const openSettings = () =>
        setParams(previous => {
            const next = new URLSearchParams(previous);
            next.set(STEP_PARAM, SETTINGS_STEP);
            return next;
        });
    // The settings entry always follows the plugin list entry, so a step back pops it.
    const openPlatform = () => navigate(-1);
    const [filter, setFilter] = useState('');
    const { data: schema, error: loadError } = useQuery({
        queryKey: [schemaKey, selected?.id],
        queryFn: () => getSchema(selected!),
        enabled: step === 1 && !!selected,
        staleTime: Infinity,
    });
    const { adapted } = useAdaptedSchema(schema, resources);
    const shown = (plugins ?? []).filter(plugin => matches(plugin, filter));
    const focusableId = shown.find(plugin => plugin.id === selected?.id)?.id ?? shown[0]?.id;

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
                <StepIndicator step={step} canOpenSettings={!!selected} onOpen={i => (i === 0 ? openPlatform() : openSettings())} />
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
                                            focusable={plugin.id === focusableId}
                                            onSelect={() => {
                                                setSelected(plugin);
                                                onSelect?.(plugin);
                                            }}
                                        />
                                    ))}
                                </div>
                            )}
                            <div className="flex items-center justify-end">
                                <Button type="button" disabled={!selected} onClick={openSettings}>
                                    Next
                                    <ArrowRightIcon aria-hidden />
                                </Button>
                            </div>
                        </>
                    )
                ) : loadError ? (
                    <div className="flex flex-col gap-4">
                        <ErrorPanel description="Could not load the plugin configuration form." error={loadError} />
                        <Button type="button" variant="outline" className="self-start" onClick={openPlatform}>
                            <ArrowLeftIcon aria-hidden />
                            Back
                        </Button>
                    </div>
                ) : !adapted ? (
                    <Spinner />
                ) : (
                    <ConfigureForm
                        key={selected!.id}
                        plugin={selected!}
                        adapted={adapted}
                        extraFields={extraFields}
                        creating={creating}
                        onBack={openPlatform}
                        onCreate={onCreate}
                    />
                )}
            </div>
        </PageFocused>
    );
}
