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
import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { deepClone } from '@gravitee/ui-components/src/lib/utils';

import { AuthService } from '../../../../services/auth.service';
import { SnackbarService } from '../../../../services/snackbar.service';
import { EntrypointService } from '../../../../services/entrypoint.service';
import { DomainStoreService } from '../../../../stores/domain.store';

@Component({
  selector: 'application-overview',
  templateUrl: './overview.component.html',
  styleUrls: ['./overview.component.scss'],
  standalone: false,
})
export class ApplicationOverviewComponent implements OnInit {
  domain: any;
  application: any;
  redirectUri: string;
  encodedRedirectUri: string;
  grantTypes: string[] = [];
  clientId: string;
  entrypoint: any;
  baseUrl: string;
  tokenEndpointAuthMethod: string;
  codeVerifier: string;
  codeChallenge: string;
  private forcePKCE: boolean;
  private CHARSET = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
  @ViewChild('copyText', { read: ElementRef, static: true }) copyText: ElementRef;

  constructor(
    private route: ActivatedRoute,
    private authService: AuthService,
    private snackbarService: SnackbarService,
    private entrypointService: EntrypointService,
    private domainStore: DomainStoreService,
  ) {}

  ngOnInit() {
    this.domain = deepClone(this.domainStore.current);
    this.entrypoint = this.route.snapshot.data['entrypoint'];
    this.application = this.route.snapshot.data['application'];
    const applicationOAuthSettings = this.application.settings == null ? {} : this.application.settings.oauth || {};

    if (this.authService.hasPermissions(['application_openid_read'])) {
      this.grantTypes = applicationOAuthSettings.grantTypes;
      this.clientId = applicationOAuthSettings.clientId;
      this.redirectUri =
        applicationOAuthSettings.redirectUris && applicationOAuthSettings.redirectUris[0] !== undefined
          ? this.cleanELParameters(applicationOAuthSettings.redirectUris[0])
          : 'Not defined';
      this.encodedRedirectUri = encodeURIComponent(this.redirectUri);
      this.tokenEndpointAuthMethod = applicationOAuthSettings.tokenEndpointAuthMethod;
      this.forcePKCE = applicationOAuthSettings.forcePKCE;
    } else {
      this.clientId = 'Insufficient permission';
      this.redirectUri = 'Insufficient permission';
    }
    this.baseUrl = this.entrypointService.resolveBaseUrl(this.entrypoint, this.domain);
    if (this.forcePKCE) {
      this.codeVerifier = this.generateCodeVerifier();
      this.generateCodeChallenge(this.codeVerifier).then((data) => (this.codeChallenge = data));
    }
  }

  cleanELParameters(redirectUri: string): string {
    try {
      let encoded = redirectUri.replace(/([^={}?&]*=)?\{#[^={}?&]*}&?/g, '');
      if (encoded.endsWith('&')) {
        encoded = encoded.slice(0, -1);
      }
      if (encoded.endsWith('?')) {
        encoded = encoded.slice(0, -1);
      }
      return encoded;
    } catch {
      return redirectUri;
    }
  }

  isServiceApp(): boolean {
    return this.application.type.toLowerCase() === 'service';
  }

  isUmaApp(): boolean {
    return this.application.type.toLowerCase() === 'resource_server';
  }

  isMcpApp(): boolean {
    return this.application.type.toLowerCase() === 'mcp';
  }

  valueCopied(message: string) {
    this.snackbarService.open(message);
  }

  copyToClipboard(element: HTMLElement | EventTarget) {
    const target = element as HTMLElement;
    this.copyText.nativeElement.value = target.textContent;
    this.copyText.nativeElement.select();
    document.execCommand('copy');
    this.valueCopied('Copied to clipboard');
  }

  getAuthorizationFlowResponseType(): string {
    if (this.grantTypes.includes('authorization_code')) {
      return 'code';
    } else {
      return 'token';
    }
  }

  getCodeVerifier(): string {
    if (!this.forcePKCE) {
      return '';
    }
    return '&code_verifier=' + this.codeVerifier;
  }

  getCodeChallenge(): string {
    if (!this.forcePKCE) {
      return '';
    }
    return '&code_challenge=' + this.codeChallenge + '&code_challenge_method=S256';
  }

  private generateCodeVerifier(): string {
    const array = new Uint8Array(32);
    window.crypto.getRandomValues(array);
    return this.base64URLEncode(this.bufferToString(array));
  }

  private generateCodeChallenge(codeVerifier): PromiseLike<string> {
    const encoder = new TextEncoder();
    const data = encoder.encode(codeVerifier);
    return window.crypto.subtle.digest('SHA-256', data).then((buffer) => {
      return this.base64URLEncode(String.fromCharCode.apply(null, new Uint8Array(buffer)));
    });
  }

  private base64URLEncode(str) {
    return btoa(str).replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '');
  }

  private bufferToString(buffer: Uint8Array) {
    const state = [];
    for (let i = 0; i < buffer.byteLength; i += 1) {
      const index = buffer[i] % this.CHARSET.length;
      state.push(this.CHARSET[index]);
    }
    return state.join('');
  }

  displayAuthBasicExample(): boolean {
    return !this.tokenEndpointAuthMethod || this.tokenEndpointAuthMethod === 'client_secret_basic';
  }

  displayAuthPostExample(): boolean {
    return !this.tokenEndpointAuthMethod || this.tokenEndpointAuthMethod === 'client_secret_post';
  }

  getEncodedClientId() {
    return encodeURIComponent(this.clientId);
  }

  getToolsData(): any[] {
    if (!this.application?.settings?.mcp?.toolDefinitions) {
      return [];
    }

    return this.application.settings.mcp.toolDefinitions.map((tool: any) => {
      // Parse scopes from the tool definition
      let scopes: string[] = [];
      if (tool.requiredScopes && Array.isArray(tool.requiredScopes)) {
        scopes = tool.requiredScopes;
      } else if (tool.requiredScopes && typeof tool.requiredScopes === 'string') {
        // Handle comma-separated scopes
        scopes = tool.requiredScopes.split(',').map((scope: string) => scope.trim()).filter((scope: string) => scope.length > 0);
      }

      return {
        name: tool.name || 'Unnamed Tool',
        description: tool.description || 'No description available',
        scopes: scopes
      };
    });
  }
}
