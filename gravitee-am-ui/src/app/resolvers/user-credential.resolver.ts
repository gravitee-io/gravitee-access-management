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
import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot } from '@angular/router';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { UserService } from '../services/user.service';

@Injectable()
export class UserCredentialResolver {
  constructor(private readonly userService: UserService) {}

  resolve(route: ActivatedRouteSnapshot): Observable<any> {
    const credentialId = route.paramMap.get('credentialId');
    const userId = route.parent.paramMap.get('userId');
    const domainId = route.parent.data['domain'].id;

    // Check if this is a certificate credential route
    const isCertificateRoute = route.parent?.routeConfig?.path === 'cert-credentials';

    const credential$ = isCertificateRoute
      ? this.userService.certificateCredential(domainId, userId, credentialId)
      : this.userService.credential(domainId, userId, credentialId);

    const credentialType = isCertificateRoute ? 'certificate' : 'webauthn';

    return credential$.pipe(
      map((credential) => ({
        ...credential,
        credentialType,
      })),
    );
  }
}
