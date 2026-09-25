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
import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { redirectToLogin, redirectToLogout, takeReturnTo } from '../../lib/api/auth';

export function LoginRedirect() {
    useEffect(() => redirectToLogin('/'), []);
    return null;
}

/** The Management API redirects here once the session cookie is set. */
export function LoginCallback() {
    const navigate = useNavigate();
    useEffect(() => {
        navigate(takeReturnTo(), { replace: true });
    }, [navigate]);
    return null;
}

export function LogoutRedirect() {
    useEffect(() => redirectToLogout(), []);
    return null;
}

export function LogoutCallback() {
    const navigate = useNavigate();
    useEffect(() => {
        navigate('/login', { replace: true });
    }, [navigate]);
    return null;
}
