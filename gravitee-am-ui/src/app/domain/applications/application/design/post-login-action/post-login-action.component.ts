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
import { ActivatedRoute } from '@angular/router';

import { ApplicationService } from '../../../../../services/application.service';
import { AuthService } from '../../../../../services/auth.service';
import { CertificateService } from '../../../../../services/certificate.service';
import { SnackbarService } from '../../../../../services/snackbar.service';

@Component({
  selector: 'app-application-post-login-action',
  templateUrl: './post-login-action.component.html',
  styleUrls: ['./post-login-action.component.scss'],
  standalone: false,
})
export class ApplicationPostLoginActionComponent implements OnInit {
  private domainId: string;
  application: any;
  postLoginAction: any;
  certificates: any[] = [];
  readonly = false;

  constructor(
    private route: ActivatedRoute,
    private applicationService: ApplicationService,
    private certificateService: CertificateService,
    private authService: AuthService,
    private snackbarService: SnackbarService,
  ) {}

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.application = structuredClone(this.route.snapshot.data['application']);
    this.postLoginAction = this.application.settings?.postLoginAction || { inherited: true };
    this.readonly = !this.authService.hasPermissions(['application_settings_update']);
    this.loadCertificates();
    this.loadPostLoginAction();
  }

  updatePostLoginAction(postLoginAction) {
    this.postLoginAction = postLoginAction;
    this.applicationService.patch(this.domainId, this.application.id, { settings: { postLoginAction } }).subscribe(() => {
      this.snackbarService.open('Application updated');
      this.loadPostLoginAction();
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
    if (!this.domainId || !this.application?.id) {
      return;
    }
    this.applicationService.get(this.domainId, this.application.id).subscribe((application) => {
      this.application = application;
      this.postLoginAction = this.application.settings?.postLoginAction || { inherited: true };
    });
  }
}
