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
import { ActivatedRouteSnapshot } from '@angular/router';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { PasswordPolicyService } from '../services/password-policy.service';

@Injectable()
export class PasswordPolicyResolver {
  constructor(private passwordPolicyService: PasswordPolicyService) {}

  resolve(route: ActivatedRouteSnapshot): Observable<any> | Promise<any> | any {
    const domainId = route.parent.data['domain'].id;
    return this.passwordPolicyService.list(domainId);
  }
}
