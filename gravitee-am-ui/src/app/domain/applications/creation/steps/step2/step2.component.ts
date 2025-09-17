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
import { Component, Input, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { find } from 'lodash';
import { HttpClient } from '@angular/common/http';
import { SnackbarService } from '../../../../../services/snackbar.service';

@Component({
  selector: 'application-creation-step2',
  templateUrl: './step2.component.html',
  styleUrls: ['./step2.component.scss'],
  standalone: false,
})
export class ApplicationCreationStep2Component implements OnInit {
  @Input() application: any;
  @ViewChild('appForm') form: any;
  domain: any;
  agentInfo: any = null;
  applicationTypes: any[] = [
    {
      icon: 'language',
      type: 'WEB',
    },
    {
      icon: 'web',
      type: 'BROWSER',
    },
    {
      icon: 'devices_other',
      type: 'NATIVE',
    },
    {
      icon: 'storage',
      type: 'SERVICE',
    },
    {
      icon: 'settings_applications',
      type: 'AGENT',
    },
    {
      icon: 'folder_shared',
      type: 'RESOURCE_SERVER',
    },
  ];

  constructor(
    private route: ActivatedRoute,
    private http: HttpClient,
    private snackbarService: SnackbarService
  ) {}

  ngOnInit(): void {
    this.domain = this.route.snapshot.data['domain'];
  }

  icon(app) {
    return find(this.applicationTypes, function (a) {
      return a.type === app.type;
    }).icon;
  }

  displayRedirectUri(): boolean {
    return this.application.type !== 'SERVICE' && this.application.type !== 'AGENT';
  }

  elRedirectUriEnabled(): boolean {
    return this.domain?.oidc?.clientRegistrationSettings?.allowRedirectUriParamsExpressionLanguage;
  }

  fetchAgentInfo(): void {
    if (!this.application.agentCardUrl) {
      this.snackbarService.open('Please enter an Agent Card URL first');
      return;
    }

    // Validate URL format
    try {
      new URL(this.application.agentCardUrl);
    } catch {
      this.snackbarService.open('Please enter a valid URL');
      return;
    }

    // Show loading state
    this.snackbarService.open('Fetching agent information...');

    this.http.get(this.application.agentCardUrl).subscribe({
      next: (data: any) => {
        this.agentInfo = data;
        this.snackbarService.open('Agent information fetched successfully');
      },
      error: (error) => {
        console.error('Error fetching agent info:', error);
        this.agentInfo = null;
        
        // Provide more specific error messages
        let errorMessage = 'Failed to fetch agent information. ';
        
        if (error.status === 0) {
          errorMessage += 'This is likely a CORS issue. The agent server needs to allow cross-origin requests.';
        } else if (error.status === 404) {
          errorMessage += 'Agent card not found at the provided URL.';
        } else if (error.status >= 500) {
          errorMessage += 'Server error. Please try again later.';
        } else if (error.status === 403) {
          errorMessage += 'Access forbidden. Check if the URL is publicly accessible.';
        } else {
          errorMessage += `HTTP ${error.status}: ${error.statusText || 'Unknown error'}`;
        }
        
        this.snackbarService.open(errorMessage);
      }
    });
  }

  testUrl(): void {
    if (!this.application.agentCardUrl) {
      this.snackbarService.open('Please enter an Agent Card URL first');
      return;
    }

    // Validate URL format
    try {
      new URL(this.application.agentCardUrl);
    } catch {
      this.snackbarService.open('Please enter a valid URL');
      return;
    }

    // Open URL in new tab
    window.open(this.application.agentCardUrl, '_blank');
    this.snackbarService.open('Opened URL in new tab for testing');
  }
}
