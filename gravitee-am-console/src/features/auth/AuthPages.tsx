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
import { toast } from '@gravitee/graphene-core';
import { useQueryClient } from '@tanstack/react-query';
import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { redirectToLogin, redirectToLogout } from '../../lib/api/auth';
import { getCurrentUser } from '../../lib/api/management-api';

export function LoginRedirect() {
    useEffect(() => redirectToLogin(), []);
    return null;
}

/** The Management API redirects here once the session cookie is set. */
export function LoginCallbackPage() {
    const navigate = useNavigate();
    useEffect(() => {
        let active = true;
        getCurrentUser().then(
            () => {
                if (active) {
                    toast.success('Login successful');
                    navigate('/', { replace: true });
                }
            },
            (error: unknown) => {
                if (active) {
                    toast.error(error instanceof Error ? error.message : String(error));
                    navigate('/logout', { replace: true });
                }
            },
        );
        return () => {
            active = false;
        };
    }, [navigate]);
    return null;
}

export function LogoutRedirect() {
    useEffect(() => redirectToLogout(), []);
    return null;
}

/** The Management API redirects here once the session cookie is gone. */
export function LogoutCallbackPage() {
    const navigate = useNavigate();
    const queryClient = useQueryClient();
    useEffect(() => {
        queryClient.clear();
        navigate('/login', { replace: true });
    }, [navigate, queryClient]);
    return null;
}
