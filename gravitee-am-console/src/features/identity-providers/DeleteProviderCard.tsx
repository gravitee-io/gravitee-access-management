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
import { Button, Card, CardContent, CardHeader, CardTitle, toast } from '@gravitee/graphene-core';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ConfirmDialog } from '../../app/components/ConfirmDialog';
import { deleteIdentityProvider, type IdentityProvider } from '../../lib/api/identity-providers';
import { useCurrentDomain } from '../../lib/session/domain';
import { useIdentityProvider } from './IdentityProviderLayout';

interface DeleteProviderCardProps {
    readonly provider: IdentityProvider;
}

/** The danger zone of the settings tab. A delete goes back to the provider list. */
export function DeleteProviderCard({ provider }: DeleteProviderCardProps) {
    const { ref } = useCurrentDomain();
    const { listPath } = useIdentityProvider();
    const navigate = useNavigate();
    const queryClient = useQueryClient();
    const [confirming, setConfirming] = useState(false);
    const remove = useMutation({
        mutationFn: () => deleteIdentityProvider(ref, provider.id),
        onSuccess: () => {
            void queryClient.invalidateQueries({ queryKey: ['identity-providers', ref] });
            toast.success('Identity provider deleted');
            navigate(listPath);
        },
        onError: error => toast.error(`Could not delete the provider. ${error.message}`),
    });

    return (
        <>
            <Card>
                <CardHeader>
                    <CardTitle>Danger zone</CardTitle>
                </CardHeader>
                <CardContent>
                    <div className="flex flex-wrap items-center justify-between gap-4">
                        <div className="flex flex-col gap-0.5">
                            <p className="text-sm font-medium">Delete identity provider</p>
                            <p className="text-sm text-muted-foreground">
                                Applications will no longer be able to use this identity provider to authenticate users.
                            </p>
                        </div>
                        <Button type="button" variant="destructive" onClick={() => setConfirming(true)}>
                            Delete
                        </Button>
                    </div>
                </CardContent>
            </Card>
            <ConfirmDialog
                open={confirming}
                title="Delete provider"
                description="Are you sure you want to delete this provider?"
                confirmLabel="Delete"
                destructive
                pending={remove.isPending}
                onOpenChange={setConfirming}
                onConfirm={() => remove.mutate()}
            />
        </>
    );
}
