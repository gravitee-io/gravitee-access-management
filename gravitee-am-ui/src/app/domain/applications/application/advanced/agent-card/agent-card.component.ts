/**
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
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { JsonPipe } from '@angular/common';
import { ApplicationService } from '../../../../../services/application.service';
import { SnackbarService } from '../../../../../services/snackbar.service';

@Component({
  selector: 'app-application-agent-card',
  templateUrl: './agent-card.component.html',
  styleUrls: ['./agent-card.component.scss'],
  standalone: false,
})
export class ApplicationAgentCardComponent implements OnInit {
  private domainId: string;
  application: any;
  agentInfo: any = null;
  loading = false;
  error: string = null;
  showRawJson = false;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private applicationService: ApplicationService,
    private http: HttpClient,
    private snackbarService: SnackbarService,
  ) {}

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.application = structuredClone(this.route.snapshot.data['application']);
    
    // Auto-fetch agent info if agentCardUrl is available (using backend proxy by default)
    if (this.application.agentCardUrl) {
      this.fetchViaBackend();
    }
  }

  fetchAgentInfo(): void {
    if (!this.application.agentCardUrl) {
      this.snackbarService.open('No Agent Card URL configured for this application');
      return;
    }

    this.loading = true;
    this.error = null;

    // Try direct fetch first
    this.http.get(this.application.agentCardUrl).subscribe({
      next: (data: any) => {
        this.agentInfo = data;
        this.loading = false;
        this.snackbarService.open('Agent information loaded successfully');
      },
      error: (error) => {
        this.loading = false;
        if (error.status === 0) {
          this.error = 'CORS Error: Unable to fetch agent information directly. The agent server needs to allow cross-origin requests.';
        } else if (error.status === 404) {
          this.error = 'Agent Card URL not found (404). Please check the URL.';
        } else if (error.status === 403) {
          this.error = 'Access forbidden (403). Please check if the agent server allows access.';
        } else if (error.status >= 500) {
          this.error = `Server error (${error.status}). The agent server is experiencing issues.`;
        } else {
          this.error = `Failed to fetch agent information: ${error.message || 'Unknown error'}`;
        }
        this.snackbarService.open('Failed to fetch agent information');
      }
    });
  }

  fetchViaBackend(): void {
    if (!this.application.agentCardUrl) {
      this.snackbarService.open('No Agent Card URL configured for this application');
      return;
    }

    this.loading = true;
    this.error = null;

    // Use backend proxy to bypass CORS
    this.applicationService.fetchAgentCard(this.domainId, this.application.id).subscribe({
      next: (data: any) => {
        this.agentInfo = data;
        this.loading = false;
        this.snackbarService.open('Agent information loaded successfully via backend');
      },
      error: (error) => {
        this.loading = false;
        if (error.status === 404) {
          this.error = 'Agent Card URL not found (404). Please check the URL.';
        } else if (error.status === 403) {
          this.error = 'Access forbidden (403). Please check if the agent server allows access.';
        } else if (error.status >= 500) {
          this.error = `Server error (${error.status}). The agent server is experiencing issues.`;
        } else {
          this.error = `Failed to fetch agent information: ${error.message || 'Unknown error'}`;
        }
        this.snackbarService.open('Failed to fetch agent information via backend');
      }
    });
  }

  testUrl(): void {
    if (this.application.agentCardUrl) {
      window.open(this.application.agentCardUrl, '_blank');
    }
  }

  refreshAgentInfo(): void {
    this.agentInfo = null;
    this.error = null;
    this.fetchViaBackend();
  }
}
