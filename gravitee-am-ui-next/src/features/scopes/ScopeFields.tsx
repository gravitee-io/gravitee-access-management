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
    CardHeader,
    CardTitle,
    Field,
    FieldDescription,
    FieldError,
    FieldLabel,
    Input,
    Label,
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
    Switch,
} from '@gravitee/graphene-core';
import { ClockIcon, XIcon } from '@gravitee/graphene-core/icons';
import { type Control, Controller, type UseFormRegister, useWatch } from 'react-hook-form';
import { consentDraftInvalid, DURATION_UNITS, type DurationUnit, humanizeSeconds } from './consent';

// A type alias, not an interface: react-hook-form constrains the values to `FieldValues`.
export type ScopeFormValues = {
    key: string;
    name: string;
    description: string;
    discovery: boolean;
    parameterized: boolean;
    amount: string;
    unit: DurationUnit | '';
    /** The consent period was cleared with the X button, as the Angular `clearExpiry()`. */
    clearedExpiry: boolean;
};

interface ToggleRowProps {
    readonly id: string;
    readonly label: string;
    readonly description: string;
    readonly name: 'discovery' | 'parameterized';
    readonly control: Control<ScopeFormValues>;
    readonly disabled: boolean;
}

function ToggleRow({ id, label, description, name, control, disabled }: ToggleRowProps) {
    return (
        <div className="flex items-start justify-between gap-4">
            <div className="flex flex-col gap-0.5">
                <Label htmlFor={id}>{label}</Label>
                <p className="text-sm text-muted-foreground">{description}</p>
            </div>
            <Controller
                control={control}
                name={name}
                render={({ field }) => <Switch id={id} checked={field.value} disabled={disabled} onCheckedChange={field.onChange} />}
            />
        </div>
    );
}

interface ScopeFieldsProps {
    readonly control: Control<ScopeFormValues>;
    readonly register: UseFormRegister<ScopeFormValues>;
    readonly errors: {
        readonly name?: { message?: string };
        readonly description?: { message?: string };
        readonly key?: { message?: string };
    };
    /** The key is fixed once the scope exists. */
    readonly keyReadOnly?: boolean;
    /** Name, description and consent period are read-only without the update permission. */
    readonly canEdit: boolean;
    /** A system scope keeps its discovery and parameterized switches locked. */
    readonly system?: boolean;
    /** The stored consent period in seconds, shown as the current one until it is cleared. */
    readonly storedExpiresIn?: number;
    readonly onClearExpiry?: () => void;
}

/** The fields the Angular scope creation and scope pages share: the details and the user consent card. */
export function ScopeFields({ control, register, errors, keyReadOnly, canEdit, system, storedExpiresIn, onClearExpiry }: ScopeFieldsProps) {
    const [amount, unit, cleared] = useWatch({ control, name: ['amount', 'unit', 'clearedExpiry'] });
    const draft = { amount, unit };
    const invalid = consentDraftInvalid(draft);
    const currentSeconds = cleared ? undefined : storedExpiresIn;
    return (
        <>
            <Card>
                <CardHeader>
                    <CardTitle className="text-base">Details</CardTitle>
                </CardHeader>
                <CardContent className="flex flex-col gap-5">
                    <Field data-invalid={!!errors.key}>
                        <FieldLabel htmlFor="scope-key">Key</FieldLabel>
                        <Input
                            id="scope-key"
                            placeholder="orders:read"
                            className={keyReadOnly ? 'font-mono' : undefined}
                            disabled={keyReadOnly}
                            aria-invalid={!!errors.key}
                            {...register('key', { required: 'Key is required' })}
                        />
                        <FieldDescription>
                            {keyReadOnly
                                ? 'The key cannot be changed once the scope exists.'
                                : 'What applications request, e.g. in the scope parameter. Whitespace becomes an underscore.'}
                        </FieldDescription>
                        {errors.key && <FieldError errors={[errors.key]} />}
                    </Field>
                    <Field data-invalid={!!errors.name}>
                        <FieldLabel htmlFor="scope-name">Name</FieldLabel>
                        <Input
                            id="scope-name"
                            readOnly={!canEdit}
                            aria-invalid={!!errors.name}
                            {...register('name', { required: 'Name is required' })}
                        />
                        {errors.name && <FieldError errors={[errors.name]} />}
                    </Field>
                    <Field data-invalid={!!errors.description}>
                        <FieldLabel htmlFor="scope-description">Description</FieldLabel>
                        <Input
                            id="scope-description"
                            readOnly={!canEdit}
                            aria-invalid={!!errors.description}
                            {...register('description', { required: 'Description is required' })}
                        />
                        <FieldDescription>Shown to users on the consent screen.</FieldDescription>
                        {errors.description && <FieldError errors={[errors.description]} />}
                    </Field>
                    <ToggleRow
                        id="scope-discovery"
                        label="Show in discovery"
                        description="List the scope in the OpenID discovery endpoint, /:domain/oidc/.well-known/openid-configuration."
                        name="discovery"
                        control={control}
                        disabled={!!system || !canEdit}
                    />
                    <ToggleRow
                        id="scope-parameterized"
                        label="Allow a parameter"
                        description="A parameterized scope may be suffixed with a value: [scope]:[value]."
                        name="parameterized"
                        control={control}
                        disabled={!!system || !canEdit}
                    />
                </CardContent>
            </Card>

            <Card>
                <CardHeader>
                    <CardTitle className="text-base">User consent</CardTitle>
                    <p className="text-sm text-muted-foreground">
                        How long the user&apos;s consent to this scope lasts before they are asked to confirm it again.
                    </p>
                </CardHeader>
                <CardContent className="flex flex-col gap-4">
                    <div className="flex items-center gap-2 text-sm">
                        <ClockIcon className="size-4 text-muted-foreground" aria-hidden />
                        <span className="text-muted-foreground">Current period:</span>
                        <span className="font-medium">{currentSeconds ? humanizeSeconds(currentSeconds) : 'No time set'}</span>
                        {currentSeconds && canEdit && onClearExpiry && (
                            <Button
                                type="button"
                                variant="ghost"
                                size="icon-xs"
                                aria-label="Clear the consent period"
                                onClick={onClearExpiry}
                            >
                                <XIcon aria-hidden />
                            </Button>
                        )}
                    </div>
                    {canEdit && (
                        <div className="grid gap-5 sm:grid-cols-2">
                            <Field data-invalid={invalid || undefined}>
                                <FieldLabel htmlFor="scope-consent-amount">Duration</FieldLabel>
                                <Input
                                    id="scope-consent-amount"
                                    type="number"
                                    min={0}
                                    aria-invalid={invalid || undefined}
                                    {...register('amount')}
                                />
                                <FieldDescription>How long the user&apos;s consent lasts.</FieldDescription>
                            </Field>
                            <Field data-invalid={invalid || undefined}>
                                <FieldLabel htmlFor="scope-consent-unit">Unit</FieldLabel>
                                <Controller
                                    control={control}
                                    name="unit"
                                    render={({ field }) => (
                                        <Select value={field.value} onValueChange={field.onChange}>
                                            <SelectTrigger id="scope-consent-unit" aria-invalid={invalid || undefined}>
                                                <SelectValue placeholder="Select a unit" />
                                            </SelectTrigger>
                                            <SelectContent>
                                                {DURATION_UNITS.map(unit => (
                                                    <SelectItem key={unit.value} value={unit.value}>
                                                        {unit.label}
                                                    </SelectItem>
                                                ))}
                                            </SelectContent>
                                        </Select>
                                    )}
                                />
                                <FieldDescription>
                                    {invalid ? 'Enter both a positive duration and a unit.' : 'Unit of the consent duration.'}
                                </FieldDescription>
                            </Field>
                        </div>
                    )}
                </CardContent>
            </Card>
        </>
    );
}
