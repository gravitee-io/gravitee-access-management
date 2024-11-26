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
import { Component, EventEmitter, Input, OnChanges, OnDestroy, Output, SimpleChanges } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { combineLatestWith, debounceTime, firstValueFrom, Subject, timer } from 'rxjs';
import { map, takeUntil } from 'rxjs/operators';

import { PasswordPolicyService } from '../../../../services/password-policy.service';
import { DomainPasswordPolicy } from '../domain-password-policy.model';

enum RuleStatus {
  VALID,
  INVALID,
  CHECKING,
}

type PasswordRule = { id: string; check: (val: PasswordInput) => Promise<PasswordRuleResult[]> };
type PasswordRuleResult = { id: string; description: string; status: RuleStatus };

type MaybeDefaultPasswordPolicy = DomainPasswordPolicy & { regex: string };
type UserProfile = { id?: string; firstName?: string; lastName?: string; email?: string };
type PasswordInput = { pass: string; profile?: UserProfile };

@Component({
  selector: 'password-policy-status',
  templateUrl: './password-policy-status.component.html',
  styleUrls: ['./password-policy-status.component.scss'],
})
export class PasswordPolicyStatusComponent implements OnChanges, OnDestroy {
  @Input() policy: DomainPasswordPolicy;
  @Input() set password(val: string) {
    const input = { pass: val, profile: this._lastInput?.profile || {} };
    this._passwordInput.next(input);
    this._lastInput = input;
  }
  @Input() set profile(val: UserProfile) {
    const input = { pass: this._lastInput?.pass || '', profile: val };
    this._passwordInput.next(input);
    this._lastInput = input;
  }
  @Output() valid = new EventEmitter<boolean>();

  private _passwordInput = new Subject<PasswordInput>();
  private _lastInput: PasswordInput;
  private _backendCheckDisabled = new Subject<void>();
  private domainId: string;
  private rules: PasswordRule[] = [];
  ruleResults: { [x: string]: PasswordRuleResult } = {};
  ruleStatus: typeof RuleStatus = RuleStatus;

  constructor(
    private readonly passwordPolicyService: PasswordPolicyService,
    private route: ActivatedRoute,
  ) {
    this.domainId = this.route.snapshot.data['domain']?.id;
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes['policy'] && changes['policy'].currentValue) {
      this.ruleResults = {};
      this.rules = this.getRules(changes['policy'].currentValue);
      // debounce if we're checking with the backend, but not if we're doing everything in-browser
      if (this.rules.some((x) => x.id === 'backend-check')) {
        this._passwordInput.pipe(debounceTime(250), takeUntil(this._backendCheckDisabled)).subscribe((pass) => this.checkRules(pass));
      } else {
        this._backendCheckDisabled.next();
        this._passwordInput.pipe(takeUntil(this._backendCheckDisabled)).subscribe((pass) => this.checkRules(pass));
      }
      if (this._lastInput) {
        this.checkRules(this._lastInput);
      }
    }
    if (changes['profile'] && changes['profile'].currentValue && this.policy?.excludeUserProfileInfoInPassword) {
      this.checkRules(this._lastInput);
    }
  }

  ngOnDestroy() {
    this._backendCheckDisabled.next();
    this._backendCheckDisabled.complete();
  }

  private getRules(policy: MaybeDefaultPasswordPolicy) {
    const _rules: PasswordRule[] = [];
    if (policy.regex) {
      _rules.push(this.regexRule('regex', policy.regex));
      // special case for the default policy, there shouldn't be any other rules then
      return _rules;
    }
    if (policy.minLength) {
      _rules.push(this.syncRule('minLength', `has at least ${policy.minLength} characters`, ({ pass }) => pass.length >= policy.minLength));
    }
    if (policy.includeNumbers) {
      _rules.push(this.regexRule('numbers', /\d/, 'contains a number'));
    }
    if (policy.includeSpecialCharacters) {
      _rules.push(this.regexRule('specialCharacters', /[^a-zA-Z0-9]/, 'contains a special character'));
    }
    if (policy.lettersInMixedCase) {
      _rules.push(
        this.syncRule('mixedCase', 'has lower- and uppercase letters', ({ pass }) => {
          return pass != pass.toLowerCase() && pass != pass.toUpperCase();
        }),
      );
    }
    if (policy.maxConsecutiveLetters) {
      _rules.push(
        this.regexRule(
          'consecutiveChars',
          `(.)\\1{${policy.maxConsecutiveLetters - 1}}`,
          `doesn't contain the same character ${policy.maxConsecutiveLetters} times in a row`,
          true,
        ),
      );
    }
    if (policy.excludeUserProfileInfoInPassword) {
      _rules.push(this.excludeUserProfileInfoRule());
    }

    if (policy.excludePasswordsInDictionary || policy.passwordHistoryEnabled) {
      _rules.push(this.backendCheck());
    }
    return _rules;
  }

  private backendCheck() {
    return {
      id: 'backend-check',
      check: ({ pass, profile }) => {
        const policyEvaluation = this.passwordPolicyService.evaluatePassword(this.domainId, this.policy.id, profile.id, pass).pipe(
          combineLatestWith(
            // add a spinner after a tiny delay, to avoid blinking on fast connections
            timer(100).pipe(
              map(() => {
                if (this.policy.excludePasswordsInDictionary != null) {
                  this.ruleResults['dictionary'] = {
                    id: 'dictionary',
                    description: 'Should not be a common password',
                    status: RuleStatus.CHECKING,
                  };
                }
                if (this.policy.passwordHistoryEnabled != null) {
                  this.ruleResults['history'] = {
                    id: 'history',
                    description: 'Should not re-use a recent password',
                    status: RuleStatus.CHECKING,
                  };
                }
              }),
            ),
          ),
          map(([result, _]) => result),
        );
        return firstValueFrom(policyEvaluation).then((policyResult) => {
          const results = [];
          if (policyResult.excludePasswordsInDictionary != null) {
            results.push({
              id: 'dictionary',
              description: 'Should not be a common password',
              status: policyResult.excludePasswordsInDictionary ? RuleStatus.VALID : RuleStatus.INVALID,
            });
          }
          if (policyResult.recentPasswordsNotReused != null) {
            results.push({
              id: 'history',
              description: 'Should not re-use a recent password',
              status: policyResult.recentPasswordsNotReused ? RuleStatus.VALID : RuleStatus.INVALID,
            });
          }
          return results;
        });
      },
    };
  }

  private regexRule(id: string, regex: string | RegExp, description?: string, shouldNotMatch: boolean = false): PasswordRule {
    description = description || `matches the regex: ${regex}`;
    const check = shouldNotMatch ? ({ pass }: PasswordInput) => pass.match(regex) == null : ({ pass }) => pass.match(regex) != null;
    return this.syncRule(id, description, check);
  }

  private syncRule(id: string, description: string, doCheck: (val: PasswordInput) => boolean): PasswordRule {
    const check: (pass: PasswordInput) => Promise<PasswordRuleResult[]> = (input) => {
      const status = doCheck(input) ? RuleStatus.VALID : RuleStatus.INVALID;
      return Promise.resolve([{ id, description, status }]);
    };
    return { id, check };
  }

  private checkRules(input: PasswordInput) {
    if (input.pass == null) {
      this.ruleResults = {};
      return;
    }
    this.rules.forEach((rule) => {
      if (this.ruleResults[rule.id]) {
        this.ruleResults[rule.id].status = RuleStatus.CHECKING;
      }
      rule.check(input).then((ruleResults) => {
        for (const result of ruleResults) {
          this.ruleResults[result.id] = result;
        }
      });
      this.checkAllRulesValid();
    });
  }
  private checkAllRulesValid() {
    let allRulesValid = true;
    for (const result of Object.values(this.ruleResults)) {
      allRulesValid &&= result.status === RuleStatus.VALID;
    }
    this.valid.emit(allRulesValid);
  }

  private excludeUserProfileInfoRule() {
    const doCheck = ({ pass, profile }) => {
      const lowercasePass = pass.toLowerCase();
      return (
        !(profile.firstName && lowercasePass.includes(profile.firstName)) &&
        !(profile.lastName && lowercasePass.includes(profile.lastName)) &&
        !(profile.email && lowercasePass.includes(profile.email))
      );
    };
    return this.syncRule('profile', 'should not contain information from profile', doCheck);
  }
}
