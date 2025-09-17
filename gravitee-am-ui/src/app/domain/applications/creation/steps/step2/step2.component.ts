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
import { MatSnackBar } from '@angular/material/snack-bar';

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

  constructor(private route: ActivatedRoute, private cdr: ChangeDetectorRef, private snackBar: MatSnackBar) {}

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

  private ensureMCPSettingsExist(): void {
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
      // Don't recreate the array if it already exists - this preserves the reference
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

  addTool(event?: Event): void {
    console.log('addTool called - current array:', this.application.settings?.mcp?.toolDefinitions);
    
    if (event) {
      event.preventDefault();
      event.stopPropagation();
    }
    
    if (!this.application.settings) {
      this.application.settings = {};
    }
    if (!this.application.settings.mcp) {
      this.application.settings.mcp = {};
    }
    if (!this.application.settings.mcp.toolDefinitions) {
      this.application.settings.mcp.toolDefinitions = [];
    }
    
    const newTool = {
      name: '',
      description: '',
      requiredScopes: [],
      requiredScopesText: '',
      inputSchema: '{\n  "type": "object",\n  "properties": {}\n}'
    };
    
    console.log('About to push to array:', this.application.settings.mcp.toolDefinitions);
    this.application.settings.mcp.toolDefinitions.push(newTool);
    console.log('After push - array length:', this.application.settings.mcp.toolDefinitions.length);
    console.log('After push - array:', this.application.settings.mcp.toolDefinitions);
  }

  removeTool(index: number): void {
    if (this.application.settings?.mcp?.toolDefinitions && index >= 0 && index < this.application.settings.mcp.toolDefinitions.length) {
      this.application.settings.mcp.toolDefinitions.splice(index, 1);
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


  // File import functionality
  onFileSelected(event: any): void {
    const file = event.target.files[0];
    if (file) {
      this.processFile(file);
    }
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    
    // Check if files are being dragged
    if (event.dataTransfer?.types.includes('Files')) {
      // Add visual feedback for drag over
      const dropZone = event.currentTarget as HTMLElement;
      dropZone.style.backgroundColor = '#e3f2fd';
      dropZone.style.borderColor = '#1976d2';
      dropZone.style.borderStyle = 'solid';
    }
  }

  onDragLeave(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    
    // Only remove visual feedback if we're actually leaving the drop zone
    const dropZone = event.currentTarget as HTMLElement;
    const rect = dropZone.getBoundingClientRect();
    const x = event.clientX;
    const y = event.clientY;
    
    if (x < rect.left || x > rect.right || y < rect.top || y > rect.bottom) {
      dropZone.style.backgroundColor = '#fafafa';
      dropZone.style.borderColor = '#ccc';
      dropZone.style.borderStyle = 'dashed';
    }
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    
    // Remove visual feedback
    const dropZone = event.currentTarget as HTMLElement;
    dropZone.style.backgroundColor = '#fafafa';
    dropZone.style.borderColor = '#ccc';
    dropZone.style.borderStyle = 'dashed';
    
    const files = event.dataTransfer?.files;
    if (files && files.length > 0) {
      this.processFile(files[0]);
    }
  }

  onDropZoneClick(event: MouseEvent): void {
    // Only trigger file input if clicking on the drop zone itself, not on buttons
    const target = event.target as HTMLElement;
    if (target.closest('button')) {
      return; // Don't trigger file input if clicking on a button
    }
    
    const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement;
    if (fileInput) {
      fileInput.click();
    }
  }

  private processFile(file: File): void {
    if (!file.name.toLowerCase().endsWith('.json')) {
      this.snackBar.open('Please select a JSON file', 'Close', { duration: 3000 });
      return;
    }

    const reader = new FileReader();
    reader.onload = (e) => {
      try {
        const content = e.target?.result as string;
        const jsonData = JSON.parse(content);
        this.importToolsFromJson(jsonData);
      } catch (error) {
        this.snackBar.open('Invalid JSON file format', 'Close', { duration: 3000 });
        console.error('Error parsing JSON:', error);
      }
    };
    reader.readAsText(file);
  }

  private importToolsFromJson(jsonData: any): void {
    try {
      // Validate JSON structure
      if (!jsonData.tools || !Array.isArray(jsonData.tools)) {
        this.snackBar.open('Invalid JSON format: "tools" array not found', 'Close', { duration: 3000 });
        return;
      }

      // Ensure MCP settings exist without recreating array references
      this.ensureMCPSettingsExist();

      // Store the original array reference
      const originalArray = this.application.settings.mcp.toolDefinitions;
      console.log('Original array reference before import:', originalArray);

      // Clear existing tools if user confirms
      if (this.application.settings.mcp.toolDefinitions.length > 0) {
        if (!confirm('This will replace all existing tool definitions. Continue?')) {
          return;
        }
      }

      // Convert imported tools to our format
      const importedTools = jsonData.tools.map((tool: any) => {
        // Extract required scopes from parameters if available
        const requiredScopes = this.extractScopesFromTool(tool);
        
        return {
          name: tool.name || '',
          description: tool.description || '',
          requiredScopes: requiredScopes,
          requiredScopesText: requiredScopes.join(', '),
          inputSchema: tool.parameters ? JSON.stringify(tool.parameters, null, 2) : '{\n  "type": "object",\n  "properties": {}\n}'
        };
      });

      // Clear existing tools and add new ones using array methods
      this.application.settings.mcp.toolDefinitions.length = 0; // Clear array
      this.application.settings.mcp.toolDefinitions.push(...importedTools); // Add new tools
      
      console.log('Array reference after import:', this.application.settings.mcp.toolDefinitions);
      console.log('Array reference preserved?', originalArray === this.application.settings.mcp.toolDefinitions);
      
      this.snackBar.open(`Successfully imported ${importedTools.length} tool(s)`, 'Close', { duration: 3000 });
    } catch (error) {
      this.snackBar.open('Error importing tools from JSON', 'Close', { duration: 3000 });
      console.error('Error importing tools:', error);
    }
  }

  private extractScopesFromTool(tool: any): string[] {
    // Try to extract scopes from various possible locations
    if (tool.requiredScopes && Array.isArray(tool.requiredScopes)) {
      return tool.requiredScopes;
    }
    
    if (tool.scopes && Array.isArray(tool.scopes)) {
      return tool.scopes;
    }

    // Generate default scopes based on tool name
    if (tool.name) {
      const toolName = tool.name.toLowerCase();
      return [`${toolName}:execute`];
    }

    return [];
  }

  downloadTemplate(): void {
    const template = {
      type: "list_tools",
      tools: [
        {
          name: "example_tool",
          description: "An example tool for demonstration purposes",
          parameters: {
            type: "object",
            properties: {
              param1: {
                type: "string",
                description: "Example parameter"
              }
            },
            required: ["param1"]
          }
        }
      ]
    };

    const blob = new Blob([JSON.stringify(template, null, 2)], { type: 'application/json' });
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'mcp-tools-template.json';
    link.click();
    window.URL.revokeObjectURL(url);
    
    this.snackBar.open('Template downloaded', 'Close', { duration: 2000 });
  }
}
