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
import {Component, OnInit, ViewChild} from '@angular/core';
import {ActivatedRoute, Router} from '@angular/router';
import {ApplicationService} from "../../../../../services/application.service";
import {SnackbarService} from "../../../../../services/snackbar.service";
import {AuthService} from "../../../../../services/auth.service";
import {CertificateService} from "../../../../../services/certificate.service";

@Component({
  selector: 'app-application-saml2',
  templateUrl: './saml2.component.html',
  styleUrls: ['./saml2.component.scss']
})
export class ApplicationSaml2Component implements OnInit {
  @ViewChild('samlSettingsForm', { static: true }) form: any;
  private domainId: string;
  application: any;
  applicationSamlSettings: any;
  formChanged: boolean;
  editMode: boolean;
  certificates: any[] = [];
  certificatePublicKeys: any[] = [];

  constructor(private router: Router,
              private route: ActivatedRoute,
              private applicationService: ApplicationService,
              private authService: AuthService,
              private snackbarService: SnackbarService,
              private certificateService: CertificateService) {
  }

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.application = this.route.snapshot.data['application'];
    this.certificates = this.route.snapshot.data['certificates'];
    this.applicationSamlSettings = (this.application.settings == null) ? {} : this.application.settings.saml || {};
    this.editMode = this.authService.hasPermissions(['application_saml_update']);
  }

  patch() {
    this.applicationSamlSettings.certificate = (this.applicationSamlSettings.certificate) ? this.applicationSamlSettings.certificate : null;
    let settings = {
      'settings': {
        'saml': this.applicationSamlSettings
      }
    };
    this.applicationService.patch(this.domainId, this.application.id, settings).subscribe(data => {
      this.router.navigate(['.'], { relativeTo: this.route, queryParams: { 'reload': true }});
      this.formChanged = false;
      this.application = data;
      this.form.reset(this.application.settings.saml);
      this.snackbarService.open('Application updated');
    });
  }
}
