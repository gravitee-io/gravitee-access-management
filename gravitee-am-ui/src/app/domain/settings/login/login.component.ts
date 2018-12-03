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
import {DomainService} from "../../../services/domain.service";
import {SnackbarService} from "../../../services/snackbar.service";
import {AppConfig} from "../../../../config/app.config";
import {ActivatedRoute, Router} from "@angular/router";

@Component({
  selector: 'app-domain-login',
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.scss']
})
export class DomainSettingsLoginComponent implements OnInit {
  domainId: string;
  domain: any = {};
  formChanged: boolean = false;

  constructor(private domainService: DomainService,
              private snackbarService: SnackbarService,
              private route: ActivatedRoute,
              private router: Router) { }

  ngOnInit() {
    this.domainId = this.route.snapshot.parent.parent.params['domainId'];
    if (this.router.routerState.snapshot.url.startsWith('/settings')) {
      this.domainId = AppConfig.settings.authentication.domainId;
    }
    this.domain = this.route.snapshot.data['domain'];
    this.domain.loginSettings = this.domain.loginSettings || {};
  }

  save() {
    this.domainService.patchLoginSettings(this.domainId, this.domain).map(res => res.json()).subscribe(data => {
      this.domain = data;
      this.formChanged = false;
      this.snackbarService.open("Login configuration updated");
    });
  }

  enableRegistration(event) {
    this.domain.loginSettings.registerEnabled = event.checked;
    this.formChanged = true;
  }

  isRegistrationEnabled() {
    return this.domain.loginSettings && this.domain.loginSettings.registerEnabled;
  }


  enableForgotPassword(event) {
    this.domain.loginSettings.forgotPasswordEnabled = event.checked;
    this.formChanged = true;
  }

  isForgotPasswordEnabled() {
    return this.domain.loginSettings && this.domain.loginSettings.forgotPasswordEnabled;
  }

}
