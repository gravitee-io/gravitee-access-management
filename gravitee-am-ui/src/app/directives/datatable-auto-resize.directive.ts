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
import { AfterViewInit, Directive, NgZone, OnDestroy } from '@angular/core';
import { DatatableComponent } from '@swimlane/ngx-datatable';

/**
 * Keeps an ngx-datatable's column widths in sync with its container.
 *
 * ngx-datatable only recalculates on a window resize, so its columns go stale
 * whenever the container changes width without one - e.g. when the side
 * navigation is collapsed/expanded - leaving wasted space or truncated columns.
 *
 * This observes the datatable's own element (the element it measures in
 * recalculateDims) and calls recalculate() on any width change. It must be the
 * datatable element and not a component host: a component host defaults to
 * display:inline, and a ResizeObserver on an inline element does not fire on
 * width changes.
 *
 * Applied globally to every <ngx-datatable> via the element selector.
 */
@Directive({
  selector: 'ngx-datatable',
  standalone: false,
})
export class DatatableAutoResizeDirective implements AfterViewInit, OnDestroy {
  private resizeObserver: ResizeObserver;
  private rafHandle: number | null = null;

  constructor(
    private table: DatatableComponent,
    private ngZone: NgZone,
  ) {}

  ngAfterViewInit(): void {
    if (typeof ResizeObserver === 'undefined' || !this.table?.element) {
      return;
    }
    // Run outside Angular: the observer fires on every animation frame while
    // the sidenav transitions, so we coalesce to a single recalculate() per
    // frame (which also avoids ResizeObserver feedback-loop warnings), then
    // re-enter the zone so change detection paints the new widths.
    this.ngZone.runOutsideAngular(() => {
      this.resizeObserver = new ResizeObserver(() => {
        if (this.rafHandle !== null) {
          return;
        }
        this.rafHandle = requestAnimationFrame(() => {
          this.rafHandle = null;
          this.ngZone.run(() => this.table.recalculate());
        });
      });
      this.resizeObserver.observe(this.table.element);
    });
  }

  ngOnDestroy(): void {
    this.resizeObserver?.disconnect();
    if (this.rafHandle !== null) {
      cancelAnimationFrame(this.rafHandle);
    }
  }
}
