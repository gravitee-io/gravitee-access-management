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

import { SnackbarService } from '../../../../services/snackbar.service';
import { EntrypointService } from '../../../../services/entrypoint.service';
import { DomainStoreService } from '../../../../stores/domain.store';

@Component({
  selector: 'application-overview',
  templateUrl: './tools.component.html',
  styleUrls: ['./tools.component.scss'],
  standalone: false,
})
export class ApplicationToolsComponent implements OnInit {
  application: any;
  entrypoint: any;
  private baseUrl: string;
  private domain: any;
  @ViewChild('copyText', { read: ElementRef }) copyText: ElementRef;
  
  // View mode switcher
  viewMode: 'cards' | 'table' = 'table';

  constructor(
    private route: ActivatedRoute,
    private snackbarService: SnackbarService,
    private entrypointService: EntrypointService,
    private domainStore: DomainStoreService,
  ) {}

  ngOnInit() {
    this.domain = deepClone(this.domainStore.current);
    this.entrypoint = this.route.snapshot.data['entrypoint'];
    this.application = this.route.snapshot.data['application'];
    this.baseUrl = this.entrypointService.resolveBaseUrl(this.entrypoint, this.domain);
  }

  valueCopied(message: string) {
    this.snackbarService.open(message);
  }


  isMCPApplication(): boolean {
    return this.application?.type === 'MCP' || this.application?.settings?.mcp != null;
  }

  getMCPTools(): any[] {
    // Return tools from application MCP settings
    if (this.application?.settings?.mcp?.toolDefinitions) {
      return this.application.settings.mcp.toolDefinitions.map(tool => {
        let inputSchema = null;
        if (tool.inputSchema) {
          try {
            inputSchema = JSON.parse(tool.inputSchema);
          } catch (error) {
            console.warn('Failed to parse input schema for tool:', tool.name, error);
          }
        }
        
        return {
          name: tool.name,
          description: tool.description,
          requiredScopes: tool.requiredScopes || [],
          inputSchema: inputSchema
        };
      });
    }
    return [];
  }

  getMCPUrl(): string {
    return this.application?.settings?.mcp?.url || 'No MCP URL configured';
  }


  setViewMode(mode: 'cards' | 'table'): void {
    this.viewMode = mode;
  }

  isCardMode(): boolean {
    return this.viewMode === 'cards';
  }

  isTableMode(): boolean {
    return this.viewMode === 'table';
  }

}
