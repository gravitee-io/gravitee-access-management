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
import { Controller, type UseFormReturn } from 'react-hook-form';
import { TagInput } from '../../app/components/TagInput';

// Graphene's resolvers constrain the values to `Record<string, unknown>`, which an interface does not satisfy.
export type ProviderValues = {
    name: string;
    domainWhitelist: string[];
    config: Record<string, unknown>;
};

interface ProviderGeneralCardProps {
    readonly form: UseFormReturn<ProviderValues>;
    /** An external provider filters its users on a domain whitelist. */
    readonly external: boolean;
    readonly disabled: boolean;
}

/** The name and, for an external provider, the domain whitelist. */
export function ProviderGeneralCard({ form, external, disabled }: ProviderGeneralCardProps) {
    const nameError = form.formState.errors.name;
    return (
        <Card>
            <CardHeader>
                <CardTitle>General</CardTitle>
                <CardDescription>Register your users to interact with the Access Management server.</CardDescription>
            </CardHeader>
            <CardContent className="grid gap-5">
                <Field data-invalid={!!nameError}>
                    <FieldLabel htmlFor="name">Name</FieldLabel>
                    <Input
                        id="name"
                        aria-invalid={!!nameError}
                        aria-describedby={nameError ? 'name-error' : undefined}
                        disabled={disabled}
                        {...form.register('name')}
                    />
                    {nameError && <FieldError id="name-error" errors={[nameError]} />}
                </Field>
                {external && (
                    <Field>
                        <FieldLabel htmlFor="domain-whitelist">Domain whitelist</FieldLabel>
                        <Controller
                            control={form.control}
                            name="domainWhitelist"
                            render={({ field }) => (
                                <TagInput
                                    id="domain-whitelist"
                                    values={field.value}
                                    onChange={field.onChange}
                                    onDuplicate={value => toast.error(`Domain pattern "${value}" is already in the list.`)}
                                    placeholder="Enter a domain whitelist pattern"
                                    disabled={disabled}
                                />
                            )}
                        />
                        <FieldDescription>
                            Filters users on their email domain after they authenticate with the provider. Users outside the list are logged
                            out.
                        </FieldDescription>
                    </Field>
                )}
            </CardContent>
        </Card>
    );
}
