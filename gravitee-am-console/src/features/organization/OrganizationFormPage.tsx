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
import { Button, Skeleton } from '@gravitee/graphene-core';
import { useQuery } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router-dom';
import { ErrorPanel } from '../../app/components/ErrorPanel';
import { NotFoundPanel } from '../../app/components/NotFoundPanel';
import { getOrganizationForm } from '../../lib/api/forms';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { useSession } from '../../lib/session/session';
import { OrganizationFormEditor } from './OrganizationFormEditor';
import { formKey, templateWords } from './form-template';
import { useOrganizationPath } from './useOrganizationPath';

export function OrganizationFormPage() {
    const [params] = useSearchParams();
    const template = params.get('template');
    const { user } = useSession();
    const base = useOrganizationPath();
    const listPath = `${base}/forms`;
    const form = useQuery({
        queryKey: formKey(user.org, template),
        queryFn: () => getOrganizationForm(user.org, template ?? ''),
        enabled: template !== null,
    });
    useBreadcrumbs([
        { label: 'Organization', to: `${base}/general` },
        { label: 'Forms', to: listPath },
        { label: template ? templateWords(template) : '' },
    ]);

    if (template === null) {
        return (
            <NotFoundPanel
                title="Form not found"
                description="No form template is selected."
                action={
                    <Button asChild variant="outline">
                        <Link to={listPath}>Back to forms</Link>
                    </Button>
                }
            />
        );
    }
    if (form.error) {
        return <ErrorPanel description="Could not load the form." error={form.error} onRetry={() => void form.refetch()} />;
    }
    if (!form.data) {
        return <Skeleton className="h-72 w-full" />;
    }
    return <OrganizationFormEditor key={form.dataUpdatedAt} template={template} form={form.data} listPath={listPath} />;
}
