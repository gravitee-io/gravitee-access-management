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
import { ActivatedRoute } from '@angular/router';

import { ProtectedResource, ProtectedResourceFeature } from '../../../../services/protected-resource.service';
import { McpTool } from '../../../components/mcp-tools-table/mcp-tools-table.component';

/**
 * Extended type for ProtectedResourceFeature that includes scopes.
 * The API returns features with scopes for MCP tools, but the base type doesn't include it.
 */
interface ProtectedResourceFeatureWithScopes extends ProtectedResourceFeature {
  scopes?: string[];
}

@Component({
  selector: 'app-domain-mcp-server-tools',
  templateUrl: './tools.component.html',
  styleUrls: ['./tools.component.scss'],
  standalone: false,
})
export class DomainMcpServerToolsComponent implements OnInit {
  domainId: string;
  protectedResource: ProtectedResource;
  features: McpTool[];

  constructor(private route: ActivatedRoute) {}

  ngOnInit(): void {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.protectedResource = this.route.snapshot.data['mcpServer'];
    this.features = this.mapFeaturesToTools(this.protectedResource.features ?? []);
  }

  private mapFeaturesToTools(features: ProtectedResourceFeature[]): McpTool[] {
    return features.map((feature) => {
      const featureWithScopes = feature as ProtectedResourceFeatureWithScopes;
      return {
        key: feature.key,
        description: feature.description,
        scopes: featureWithScopes.scopes ?? [],
      };
    });
  }
}
