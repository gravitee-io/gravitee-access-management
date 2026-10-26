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
import { Button } from '@gravitee/graphene-core';
import { Link } from 'react-router-dom';
import { NotFoundPanel } from '../../app/components/NotFoundPanel';

export function NotFoundPage() {
    return (
        <div className="mx-auto max-w-2xl p-10">
            <NotFoundPanel
                title="Page not found"
                description="The page you are looking for does not exist or no longer exists. No worries, nothing is lost yet!"
                action={
                    <Button asChild>
                        <Link to="/">Go home</Link>
                    </Button>
                }
            />
        </div>
    );
}
