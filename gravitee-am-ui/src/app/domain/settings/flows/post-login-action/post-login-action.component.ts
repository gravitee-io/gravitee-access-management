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
import { deepClone } from '@gravitee/ui-components/src/lib/utils';

import { AuthService } from '../../../../services/auth.service';
import { CertificateService } from '../../../../services/certificate.service';
import { DomainService } from '../../../../services/domain.service';
import { SnackbarService } from '../../../../services/snackbar.service';
import { DomainStoreService } from '../../../../stores/domain.store';

@Component({
  selector: 'app-domain-post-login-action',
  templateUrl: './post-login-action.component.html',
  styleUrls: ['./post-login-action.component.scss'],
  standalone: false,
})
export class DomainSettingsPostLoginActionComponent implements OnInit {
  domainId: string;
  domain: any = {};
  postLoginAction: any = {};
  certificates: any[] = [];
  readonly = false;
  private loadedDomainId: string;

  constructor(
    private domainService: DomainService,
    private certificateService: CertificateService,
    private snackbarService: SnackbarService,
    private authService: AuthService,
    private domainStore: DomainStoreService,
  ) {}

  ngOnInit() {
    this.readonly = !this.authService.hasPermissions(['domain_settings_update']);
    this.domainStore.domain$.subscribe((domain) => {
      if (!domain) {
        return;
      }
      this.domain = deepClone(domain);
      const previousDomainId = this.domainId;
      this.domainId = this.domain.id;
      this.postLoginAction = this.domain.postLoginAction || {};
      if (previousDomainId !== this.domainId) {
        this.loadCertificates();
      }
      if (this.loadedDomainId !== this.domainId) {
        this.loadPostLoginAction();
      }
    });
  }

  updatePostLoginAction(postLoginAction) {
    postLoginAction.inherited = false;
    this.postLoginAction = postLoginAction;
    this.domainService.patch(this.domainId, { postLoginAction }).subscribe(() => {
      this.loadPostLoginAction();
      this.snackbarService.open('Post login action updated');
    });
  }

  private loadCertificates(): void {
    if (!this.domainId) {
      return;
    }
    this.certificateService.findByDomainAndUse(this.domainId, 'sig').subscribe((certificates) => {
      this.certificates = certificates || [];
    });
  }

  private loadPostLoginAction(): void {
    if (!this.domainId) {
      return;
    }
    this.domainService.getById(this.domainId).subscribe((domain) => {
      this.loadedDomainId = domain?.id;
      this.domain = domain;
      this.postLoginAction = this.domain.postLoginAction || {};
      this.domainStore.set(domain);
    });
  }
}
