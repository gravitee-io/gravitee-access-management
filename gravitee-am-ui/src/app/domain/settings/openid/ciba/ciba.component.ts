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
import {Component, OnInit} from '@angular/core';
import {ActivatedRoute, Router} from "@angular/router";
import { AuthService } from 'app/services/auth.service';
import { DomainService } from 'app/services/domain.service';
import { SnackbarService } from 'app/services/snackbar.service';

@Component({
  selector: 'app-oidc-ciba',
  templateUrl: './ciba.component.html',
  styleUrls: ['./ciba.component.scss']
})
export class CibaComponent implements OnInit {
  domainId: string;
  domain: any = {};
  formChanged = false;
  editMode: boolean;

  constructor(private domainService: DomainService,
              private snackbarService: SnackbarService,
              private authService: AuthService,
              private route: ActivatedRoute,
              private router: Router) {
  }

  ngOnInit() {
    this.domain = this.route.snapshot.data['domain'];
    this.domainId = this.domain.id;
    this.editMode = this.authService.hasPermissions(['domain_openid_update']);
    if (!this.domain.oidc.cibaSettings) {
      this.domain.oidc.cibaSettings = {};
    }
  }

  save() {
    this.domainService.patchOpenidDCRSettings(this.domainId, this.domain).subscribe(data => {
      this.domain = data;
      this.formChanged = false;
      this.snackbarService.open('OpenID Profile configuration updated');
    });
  }

  enableCIBA(event) {
    if (!this.domain.oidc.cibaSettings) {
      this.domain.oidc.cibaSettings = {};
    }
    this.domain.oidc.cibaSettings.enabled = event.checked;
    if (!event.checked) {
      // Disable Plain FAPI imply to disable FAPI Brazil
      this.domain.oidc.cibaSettings.enabled = event.checked;
    }
    this.formChanged = true;
  }

  isCIBAEnabled() {
    return this.domain.oidc.cibaSettings && this.domain.oidc.cibaSettings.enabled;
  }
  
  modelChanged(event) {
    this.formChanged = true;
  }
}
