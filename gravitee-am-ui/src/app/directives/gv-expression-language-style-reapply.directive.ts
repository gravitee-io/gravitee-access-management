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
import { Directive, ElementRef, Renderer2, OnDestroy, AfterViewChecked } from '@angular/core';

@Directive({
  selector: '[styleReapply]',
})
export class GvExpressionLanguageStyleReapplyDirective implements OnDestroy, AfterViewChecked {
  private observer: MutationObserver;
  private style: string;
  private init: boolean = false;
  constructor(
    private el: ElementRef,
    private renderer: Renderer2,
  ) {}

  ngAfterViewChecked(): void {
    if (!this.init) {
      this.applyStyles();
      this.observer = new MutationObserver(() => this.applyStyles());
      this.observer.observe(this.el.nativeElement, { childList: true, subtree: true });
    }
  }

  ngOnDestroy(): void {
    if (this.observer) {
      this.observer.disconnect();
    }
  }

  private applyStyles(): void {
    const shadowRoot = this.el.nativeElement.shadowRoot;
    if (shadowRoot) {
      const styleElement = this.renderer.createElement('style');
      styleElement.textContent = this.createGvExpressionLanguageStyle();
      this.renderer.appendChild(shadowRoot, styleElement);
      this.init = true;
    }
  }

  private createGvExpressionLanguageStyle() {
    return `
      .cm-tooltip {
        border: 1px solid rgb(187, 187, 187);
        background-color: rgb(245, 245, 245);
        z-index: 100
      }

      .cm-content {
        outline: none;
      }
      
      .cm-matchingBracket {
        background-color: rgba(50, 140, 130, 0.32);
      }
      
      .cm-nonmatchingBracket {
        background-color: rgba(187, 85, 85, 0.267);
      }

      .cm-tooltip-autocomplete > ul {
        font-family: monospace;
        white-space: nowrap;
        overflow: hidden auto;
        max-width: min(700px, 95vw);
        min-width: 250px;
        max-height: 10em;
        list-style: none;
        margin: 0px;
        padding: 0px;
      }

      .cm-tooltip-autocomplete > ul > li {
        overflow-x: hidden;
        text-overflow: ellipsis;
        cursor: pointer;
        padding: 1px 3px;
        line-height: 1.2;
      }

      .cm-tooltip-autocomplete ul li[aria-selected] {
        background: rgb(17, 119, 204);
        color: white;
      }

      .cm-completionIcon-variable::after {
        content: "𝑥"
      }
      
      .cm-completionIcon {
        font-size: 90%;
        width: 0.8em;
        display: inline-block;
        text-align: center;
        padding-right: 0.6em;
        opacity: 0.6;
      }

      .cm-scroller {
        font-family: monospace;
        line-height: 1.4;
        height: 100%;
        overflow-x: auto;
        position: relative;
        z-index: 0;
        display: flex !important;
        align-items: flex-start !important;
      }

      .cm-line {
        display: block;
        padding: 0px 2px 0px 4px;
      }
    `;
  }
}
