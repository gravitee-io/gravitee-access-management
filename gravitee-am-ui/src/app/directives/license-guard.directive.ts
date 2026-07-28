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
import { Directive, ElementRef, Input, OnChanges, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { GioLicenseService, LicenseOptions } from '@gravitee/ui-particles-angular';

import { AmFeature } from '../components/gio-license/gio-license-data';

/**
 * Reactive licensing guard for an action control (e.g. a SAVE button or a plugin creation card).
 *
 * When the bound feature is not granted by the effective license, a capture-phase click handler
 * swallows the click and opens the Gravitee upgrade dialog instead of letting the component's own
 * handler run (which would hit the backend and 403). When the feature is granted — or no feature is
 * required — the control behaves normally.
 *
 * Improvements over the library's {@code gioLicense} directive:
 * - it re-evaluates on input changes, so it works when the {@link LicenseOptions} are resolved
 *   asynchronously (the common case here — the feature is looked up from the plugin catalog after
 *   the view has initialised);
 * - it tolerates a feature with no dedicated {@code FeatureInfoData} entry (e.g. a newer/third-party
 *   plugin): {@code gioLicense}/{@code getFeatureInfo} throw in that case, which silently breaks the
 *   interception; here we fall back to a generic upgrade dialog instead.
 */
@Directive({
  selector: '[licenseGuard]',
  standalone: false,
})
export class LicenseGuardDirective implements OnInit, OnChanges, OnDestroy {
  @Input('licenseGuard') licenseOptions?: LicenseOptions;

  private missingFeature = false;
  private subscription?: Subscription;
  private readonly onClick = (event: Event): void => {
    if (!this.missingFeature) {
      return;
    }
    // Block the host's own (click) handler (which would hit the backend and 403) and upsell instead.
    event.preventDefault();
    event.stopImmediatePropagation();
    try {
      this.licenseService.openDialog(this.licenseOptions, event);
    } catch {
      // getFeatureInfo throws when the plugin's feature has no dedicated FeatureInfoData entry;
      // fall back to a generic Enterprise upgrade dialog so the upsell still shows.
      this.licenseService.openDialog({ feature: AmFeature.AM_ENTERPRISE }, event);
    }
  };

  constructor(
    private readonly licenseService: GioLicenseService,
    private readonly elementRef: ElementRef,
  ) {}

  ngOnInit(): void {
    // Capture phase so we intercept before the host's own (click) handler.
    this.elementRef.nativeElement.addEventListener('click', this.onClick, true);
  }

  ngOnChanges(): void {
    this.subscription?.unsubscribe();
    this.subscription = this.licenseService
      .isMissingFeature$(this.licenseOptions?.feature)
      .subscribe((missingFeature) => (this.missingFeature = missingFeature));
  }

  ngOnDestroy(): void {
    this.elementRef.nativeElement.removeEventListener('click', this.onClick, true);
    this.subscription?.unsubscribe();
  }
}
