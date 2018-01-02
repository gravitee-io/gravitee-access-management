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
import { ActivatedRoute } from "@angular/router";
import { BreadcrumbService } from "../../../../../libraries/ng2-breadcrumb/components/breadcrumbService";
import { PlatformService } from "../../../../services/platform.service";
import { CertificateService}  from "../../../../services/certificate.service";
import { SnackbarService } from "../../../../services/snackbar.service";

@Component({
  selector: 'app-certificate',
  templateUrl: './certificate.component.html',
  styleUrls: ['./certificate.component.scss']
})
export class CertificateComponent implements OnInit {
  private domainId: string;
  configurationIsValid: boolean = true;
  configurationPristine: boolean = true;
  certificate: any;
  certificateSchema: any;
  certificateConfiguration: any;
  updateCertificateConfiguration: any;

  constructor(private route: ActivatedRoute, private breadcrumbService: BreadcrumbService, private platformService: PlatformService,
              private certificateService: CertificateService, private snackbarService: SnackbarService) { }

  ngOnInit() {
    this.domainId = this.route.snapshot.parent.parent.params['domainId'];
    this.certificate = this.route.snapshot.data['certificate'];
    this.certificateConfiguration = JSON.parse(this.certificate.configuration);
    this.updateCertificateConfiguration = this.certificateConfiguration;
    this.platformService.certificateSchema(this.certificate.type).map(res => res.json()).subscribe(data => this.certificateSchema = data);
    this.initBreadcrumb();
  }

  update() {
    this.certificate.configuration = JSON.stringify(this.updateCertificateConfiguration);
    this.certificateService.update(this.domainId, this.certificate.id, this.certificate).map(res => res.json()).subscribe(data => {
      this.breadcrumbService.addFriendlyNameForRouteRegex('/domains/'+this.domainId+'/settings/certificates/'+this.certificate.id+'$', this.certificate.name);
      this.snackbarService.open("Certificate updated");
    })
  }

  enableCertificateUpdate(configurationWrapper) {
    window.setTimeout(() => {
      this.configurationPristine = this.certificate.configuration === JSON.stringify(configurationWrapper.configuration);
      this.configurationIsValid = configurationWrapper.isValid;
      this.updateCertificateConfiguration = configurationWrapper.configuration;
    });
  }

  initBreadcrumb() {
    this.breadcrumbService.addFriendlyNameForRouteRegex('/domains/'+this.domainId+'/settings/certificates/'+this.certificate.id+'$', this.certificate.name);
  }

}
