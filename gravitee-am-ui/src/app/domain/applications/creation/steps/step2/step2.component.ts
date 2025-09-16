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
import { Component, Input, OnInit, OnChanges, ViewChild, ChangeDetectorRef } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { find } from 'lodash';

@Component({
  selector: 'application-creation-step2',
  templateUrl: './step2.component.html',
  styleUrls: ['./step2.component.scss'],
  standalone: false,
})
export class ApplicationCreationStep2Component implements OnInit, OnChanges {
  @Input() application: any;
  @ViewChild('appForm') form: any;
  domain: any;
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
      icon: 'folder_shared',
      type: 'RESOURCE_SERVER',
    },
    {
      icon: 'folder_shared',
      type: 'MCP',
    }
  ];

  constructor(private route: ActivatedRoute, private cdr: ChangeDetectorRef) {}

  ngOnInit(): void {
    this.domain = this.route.snapshot.data['domain'];
    this.initializeMCPSettings();
  }

  private initializeMCPSettings(): void {
    if (this.application && this.application.type === 'MCP') {
      if (!this.application.settings) {
        this.application.settings = {};
      }
      if (!this.application.settings.mcp) {
        this.application.settings.mcp = {
          url: '',
          toolDefinitions: []
        };
      }
      if (!this.application.settings.mcp.toolDefinitions) {
        this.application.settings.mcp.toolDefinitions = [];
      }
    }
  }

  icon(app) {
    return find(this.applicationTypes, function (a) {
      return a.type === app.type;
    }).icon;
  }

  displayRedirectUri(): boolean {
    return this.application.type !== 'SERVICE';
  }

  elRedirectUriEnabled(): boolean {
    return this.domain?.oidc?.clientRegistrationSettings?.allowRedirectUriParamsExpressionLanguage;
  }

  addTool(): void {
    if (!this.application.settings) {
      this.application.settings = {};
    }
    if (!this.application.settings.mcp) {
      this.application.settings.mcp = {};
    }
    if (!this.application.settings.mcp.toolDefinitions) {
      this.application.settings.mcp.toolDefinitions = [];
    }
    
    this.application.settings.mcp.toolDefinitions.push({
      name: '',
      description: '',
      requiredScopes: [],
      requiredScopesText: '',
      inputSchema: '{\n  "type": "object",\n  "properties": {}\n}'
    });
    
    // Force change detection to update the UI
    this.cdr.detectChanges();
  }

  removeTool(index: number): void {
    if (this.application.settings?.mcp?.toolDefinitions) {
      this.application.settings.mcp.toolDefinitions.splice(index, 1);
      // Force change detection to update the UI
      this.cdr.detectChanges();
    }
  }

  updateRequiredScopes(tool: any, scopesText: string): void {
    if (scopesText && scopesText.trim()) {
      tool.requiredScopes = scopesText.split(',').map((scope: string) => scope.trim()).filter((scope: string) => scope.length > 0);
    } else {
      tool.requiredScopes = [];
    }
  }

  ngOnChanges(): void {
    this.initializeMCPSettings();
  }

  trackByIndex(index: number, item: any): number {
    return index;
  }
}
