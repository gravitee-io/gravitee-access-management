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
import {Component, ElementRef, OnInit, ViewChild} from '@angular/core';
import {ActivatedRoute} from '@angular/router';
import {AuthService} from '../../../../services/auth.service';
import {SnackbarService} from '../../../../services/snackbar.service';
import {EntrypointService} from "../../../../services/entrypoint.service";

@Component({
  selector: 'application-overview',
  templateUrl: './overview.component.html',
  styleUrls: ['./overview.component.scss']
})
export class ApplicationOverviewComponent implements OnInit {
  domain: any;
  application: any;
  redirectUri: string;
  grantTypes: string[] = [];
  clientId: string;
  clientSecret: string;
  safeClientSecret: string;
  hidden = '********';
  safeAuthorizationHeader: string;
  authorizationHeader: string;
  entrypoint: any;
  baseUrl: string;
  tokenEndpointAuthMethod: string;
  @ViewChild('copyText', {read: ElementRef}) copyText: ElementRef;

  constructor(private route: ActivatedRoute,
              private authService: AuthService,
              private snackbarService: SnackbarService,
              private entrypointService: EntrypointService) {
  }

  ngOnInit() {
    this.domain = this.route.snapshot.data['domain'];
    this.entrypoint = this.route.snapshot.data['entrypoint'];
    this.application = this.route.snapshot.data['application'];
    this.safeClientSecret = this.hidden;
    this.safeAuthorizationHeader = this.hidden;
    const applicationOAuthSettings = this.application.settings == null ? {} : this.application.settings.oauth || {};

    if (this.authService.hasPermissions(['application_openid_read'])) {
      this.grantTypes = applicationOAuthSettings.grantTypes;
      this.clientId = applicationOAuthSettings.clientId;
      this.clientSecret = applicationOAuthSettings.clientSecret;
      this.redirectUri = applicationOAuthSettings.redirectUris && applicationOAuthSettings.redirectUris[0] !== undefined ? applicationOAuthSettings.redirectUris[0] : 'Not defined';
      this.authorizationHeader = btoa(this.clientId + ':' + this.clientSecret);
      this.tokenEndpointAuthMethod = applicationOAuthSettings.tokenEndpointAuthMethod;
    } else {
      this.clientId = 'Insufficient permission';
      this.clientSecret = 'Insufficient permission';
      this.redirectUri = 'Insufficient permission';
      this.authorizationHeader = 'Insufficient permission';
    }

    this.baseUrl = this.entrypointService.resolveBaseUrl(this.entrypoint, this.domain);
  }

  isServiceApp(): boolean {
    return this.application.type.toLowerCase() === 'service';
  }

  isUmaApp(): boolean {
    return this.application.type.toLowerCase() === 'resource_server';
  }

  valueCopied(message: string) {
    this.snackbarService.open(message);
  }

  copyToClipboard(element: HTMLElement, sensitiveReplacement?) {
    this.copyText.nativeElement.value = element.textContent.replace(this.hidden, sensitiveReplacement ? sensitiveReplacement : '');
    this.copyText.nativeElement.select();
    document.execCommand('copy');
    this.valueCopied('Copied to clipboard');
  }

  isHidden(value): boolean {
    return value === this.hidden;
  }

  getAuthorizationFlowResponseType(): string {
    if (this.grantTypes.includes('authorization_code')) {
      return 'code';
    } else {
      return 'token';
    }
  }

  displayAuthBasicExample(): boolean {
    return !this.tokenEndpointAuthMethod || this.tokenEndpointAuthMethod === 'client_secret_basic';
  }
}
