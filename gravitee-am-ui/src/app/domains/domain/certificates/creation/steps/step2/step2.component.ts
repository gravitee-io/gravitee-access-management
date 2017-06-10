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
import { Component, OnInit, Input } from '@angular/core';
import { PlatformService } from "../../../../../../services/platform.service";
import { SnackbarService } from "../../../../../../services/snackbar.service";
import { Router, ActivatedRoute } from "@angular/router";
import { CertificateService } from "../../../../../../services/certificate.service";

@Component({
  selector: 'certificate-creation-step2',
  templateUrl: 'step2.component.html',
  styleUrls: ['step2.component.scss']
})
export class CertificateCreationStep2Component implements OnInit {
  @Input('certificate') certificate: any;
  configuration: any;
  configurationIsValid: boolean = false;
  certificateSchema: any = {};
  private domainId: string;

  constructor(private platformService: PlatformService, private certificateService: CertificateService,
              private snackbarService: SnackbarService, private router: Router, private route: ActivatedRoute) { }

  ngOnInit() {
    this.domainId = this.route.snapshot.parent.params['domainId'];
    this.platformService.certificateSchema(this.certificate.type).map(resp => resp.json()).subscribe(data => this.certificateSchema = data);
  }

  enableCertificateCreation(configurationWrapper) {
    this.configurationIsValid = configurationWrapper.isValid;
    this.certificate.configuration = configurationWrapper.configuration;
  }

  create() {
    this.certificate.configuration = JSON.stringify(this.certificate.configuration);
    this.certificateService.create(this.domainId, this.certificate).map(res => res.json()).subscribe(data => {
      this.snackbarService.open("Certificate " + data.name + " created");
      this.router.navigate(['/domains', this.domainId, 'certificates', data.id]);
    })
  }

  get isValid() {
    if (this.certificate.name && this.configurationIsValid) {
      return true;
    } else {
      return false;
    }
  }
}
