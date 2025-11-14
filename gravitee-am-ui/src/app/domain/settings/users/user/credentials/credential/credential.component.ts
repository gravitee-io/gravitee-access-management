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
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { CREDENTIAL_TYPE, Credential } from '../credentials.component';

@Component({
  selector: 'app-user-credential',
  templateUrl: './credential.component.html',
  styleUrls: ['./credential.component.scss'],
  standalone: false,
})
export class UserCredentialComponent implements OnInit {
  credential: Credential;
  credentialType: string;

  constructor(
    private readonly route: ActivatedRoute,
    private readonly router: Router,
  ) {}

  ngOnInit() {
    this.credential = this.route.snapshot.data['credential'];
    // Credential type is set by the resolver
    this.credentialType = this.credential?.credentialType || CREDENTIAL_TYPE.WEBAUTHN;
  }

  isCertificate(): boolean {
    return this.credentialType === CREDENTIAL_TYPE.CERTIFICATE;
  }

  isWebAuthn(): boolean {
    return this.credentialType === CREDENTIAL_TYPE.WEBAUTHN;
  }

  goBackToCredentials(): void {
    // Navigate to credentials list (sibling route)
    this.router.navigate(['../credentials'], { relativeTo: this.route.parent });
  }
}
