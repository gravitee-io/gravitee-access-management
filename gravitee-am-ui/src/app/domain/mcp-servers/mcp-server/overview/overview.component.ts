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

import { ProtectedResource, ProtectedResourceService } from '../../../../services/protected-resource.service';
import { SnackbarService } from '../../../../services/snackbar.service';

@Component({
  selector: 'app-domain-mcp-server-overview',
  templateUrl: './overview.component.html',
  styleUrl: './overview.component.scss',
  standalone: false,
})
export class DomainMcpServerOverviewComponent implements OnInit {
  domainId: string;
  protectedResource: ProtectedResource;
  @ViewChild('copyText', { read: ElementRef, static: true }) copyText: ElementRef;

  constructor(
    private route: ActivatedRoute,
    private protectedResourceService: ProtectedResourceService,
    private snackbarService: SnackbarService,
  ) {}

  ngOnInit(): void {
    this.domainId = this.route.snapshot.data['domain']?.id;
    const initialData = this.route.snapshot.data['mcpServer'];
    this.protectedResource = initialData;

    if (initialData?.id && initialData?.type) {
      this.protectedResourceService.findById(this.domainId, initialData.id, initialData.type).subscribe((resource) => {
        this.protectedResource = resource;
        this.route.snapshot.data['mcpServer'] = resource;
      });
    }
  }

  get displayTools(): string[] {
    if (!this.protectedResource?.features) {
      return [];
    }
    return [...this.protectedResource.features]
      .sort((a, b) => {
        const dateA = new Date(a.createdAt || 0).getTime();
        const dateB = new Date(b.createdAt || 0).getTime();
        return dateB - dateA;
      })
      .map((f) => f.key);
  }

  get hasTools(): boolean {
    return this.protectedResource?.features?.length > 0;
  }

  copyToClipboard(element: HTMLElement) {
    this.copyText.nativeElement.value = element.textContent;
    this.copyText.nativeElement.select();
    document.execCommand('copy');
    this.snackbarService.open('Copied to clipboard');
  }
}
