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
import type { AccountSettings } from '../../lib/api/domains';
import { type DurationDraft, draftToSeconds, exactDuration } from '../../lib/forms/duration';

export type Settings = { -readonly [K in keyof AccountSettings]: AccountSettings[K] };

export type CustomField = NonNullable<AccountSettings['resetPasswordCustomFormFields']>[number];

export type DurationKey = 'loginAttemptsResetTime' | 'accountBlockedDuration' | 'mfaChallengeAttemptsResetTime' | 'rememberMeDuration';

export interface FormState {
    readonly settings: Settings;
    readonly durations: Readonly<Record<DurationKey, DurationDraft>>;
    readonly fields: readonly CustomField[];
}

export type FormErrors = Partial<Record<DurationKey | 'maxLoginAttempts' | 'mfaChallengeMaxAttempts' | 'fields', string>>;

export const DEFAULT_MAX_ATTEMPTS = 10;
export const DEFAULT_LOGIN_ATTEMPTS_RESET = 43_200;
export const DEFAULT_ACCOUNT_BLOCKED = 7_200;
export const DEFAULT_MFA_RESET = 60;
export const DEFAULT_REMEMBER_ME = 1_209_600;
export const DEFAULT_MFA_MAX_ATTEMPTS = 3;

const EMPTY_DURATION: DurationDraft = { time: '', unit: '' };

/** A duration shows only when one is stored. */
function storedDuration(seconds: number | undefined): DurationDraft {
    return seconds && seconds > 0 ? exactDuration(seconds) : EMPTY_DURATION;
}

/** The current duration, or the default when it is empty. */
export function orDefault(duration: DurationDraft, seconds: number): DurationDraft {
    return draftToSeconds(duration) ? duration : exactDuration(seconds);
}

export function fromAccountSettings(accountSettings: AccountSettings | undefined): FormState {
    const settings: Settings = { ...accountSettings };
    return {
        settings,
        durations: {
            loginAttemptsResetTime: storedDuration(settings.loginAttemptsResetTime),
            accountBlockedDuration: storedDuration(settings.accountBlockedDuration),
            mfaChallengeAttemptsResetTime: storedDuration(settings.mfaChallengeAttemptsResetTime),
            rememberMeDuration: storedDuration(settings.rememberMeDuration),
        },
        fields: settings.resetPasswordCustomForm ? (settings.resetPasswordCustomFormFields ?? []) : [],
    };
}

/** Keys the form does not show stay in the payload. An empty duration is sent as 0. */
export function toPayload({ settings, durations, fields }: FormState): AccountSettings {
    const payload: Settings = {
        ...settings,
        inherited: false,
        loginAttemptsResetTime: draftToSeconds(durations.loginAttemptsResetTime),
        accountBlockedDuration: draftToSeconds(durations.accountBlockedDuration),
        mfaChallengeAttemptsResetTime: draftToSeconds(durations.mfaChallengeAttemptsResetTime),
        rememberMeDuration: draftToSeconds(durations.rememberMeDuration),
    };
    if (settings.resetPasswordCustomForm) {
        payload.resetPasswordCustomFormFields = [...fields];
    } else {
        delete payload.resetPasswordCustomFormFields;
        delete payload.resetPasswordConfirmIdentity;
    }
    if (!settings.useBotDetection) {
        delete payload.botDetectionPlugin;
    }
    return payload;
}

const durationValid = (duration: DurationDraft) => !!duration.unit && Number(duration.time) >= 1;

export function validate({ settings, durations, fields }: FormState): FormErrors {
    const errors: FormErrors = {};
    if (settings.loginAttemptsDetectionEnabled) {
        if (!settings.maxLoginAttempts || settings.maxLoginAttempts < 1) {
            errors.maxLoginAttempts = 'Enter at least 1 attempt.';
        }
        if (!durationValid(durations.loginAttemptsResetTime)) {
            errors.loginAttemptsResetTime = 'Enter a duration of 1 or more.';
        }
        if (!durationValid(durations.accountBlockedDuration)) {
            errors.accountBlockedDuration = 'Enter a duration of 1 or more.';
        }
    }
    if (settings.mfaChallengeAttemptsDetectionEnabled) {
        if (!settings.mfaChallengeMaxAttempts || settings.mfaChallengeMaxAttempts < 1) {
            errors.mfaChallengeMaxAttempts = 'Enter at least 1 attempt.';
        }
        if (!durationValid(durations.mfaChallengeAttemptsResetTime)) {
            errors.mfaChallengeAttemptsResetTime = 'Enter a duration of 1 or more.';
        }
    }
    if (settings.rememberMe && !durationValid(durations.rememberMeDuration)) {
        errors.rememberMeDuration = 'Enter a duration of 1 or more.';
    }
    if (settings.resetPasswordCustomForm && fields.length === 0) {
        errors.fields = 'Add at least one field to the custom form.';
    }
    return errors;
}
