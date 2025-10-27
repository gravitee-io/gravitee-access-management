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
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface PaginationState<T> {
  items: T[];
  continuationToken: string | null;
  tokenHistory: string[];
  prefetchedData: T[] | null;
  prefetchedToken: string | null;
  hasMorePages: boolean;
  isLoading: boolean;
}

export interface PaginationResponse<T> {
  data: T[];
  info?: {
    continuationToken?: string;
  };
}

@Injectable()
export class PaginationService<T> {
  private state: PaginationState<T> = {
    items: [],
    continuationToken: null,
    tokenHistory: [],
    prefetchedData: null,
    prefetchedToken: null,
    hasMorePages: false,
    isLoading: false,
  };

  constructor() {}

  getState(): PaginationState<T> {
    return { ...this.state };
  }

  setLoading(loading: boolean): void {
    this.state.isLoading = loading;
  }

  setItems(items: T[], continuationToken: string | null, pageSize: number): void {
    this.state.items = items;
    this.state.continuationToken = continuationToken;

    // Prefetch next page if current page is full
    if (items.length === pageSize && continuationToken) {
      this.state.hasMorePages = true;
    } else if (items.length < pageSize) {
      this.state.hasMorePages = false;
      this.state.prefetchedData = null;
      this.state.prefetchedToken = null;
    }
  }

  setPrefetchedData(data: T[], token: string | null): void {
    this.state.prefetchedData = data;
    this.state.prefetchedToken = token;
    this.state.hasMorePages = data.length > 0;
  }

  nextPage(pageSize: number, loadFunction: (token?: string) => Observable<PaginationResponse<T>>): Observable<PaginationResponse<T>> {
    if (!this.state.hasMorePages) {
      throw new Error('No more pages available');
    }

    // Use prefetched data if available
    if (this.state.prefetchedData !== null) {
      this.state.tokenHistory.push(this.state.continuationToken!);
      this.state.items = this.state.prefetchedData;
      this.state.continuationToken = this.state.prefetchedToken;

      const nextToken = this.state.prefetchedToken;
      this.state.prefetchedData = null;
      this.state.prefetchedToken = null;

      // Prefetch next page if current page is full
      this.state.hasMorePages = this.state.items.length === pageSize && nextToken !== null;

      return new Observable((observer) => {
        observer.next({
          data: this.state.items,
          info: { continuationToken: this.state.continuationToken },
        });
        observer.complete();
      });
    } else if (this.state.continuationToken) {
      this.state.tokenHistory.push(this.state.continuationToken);
      return loadFunction(this.state.continuationToken);
    }

    throw new Error('No continuation token available');
  }

  previousPage(loadFunction: (token?: string) => Observable<PaginationResponse<T>>): Observable<PaginationResponse<T>> {
    this.state.tokenHistory.pop();
    const previousToken = this.state.tokenHistory.length > 0 ? this.state.tokenHistory[this.state.tokenHistory.length - 1] : undefined;

    // Clear prefetch when going back
    this.state.prefetchedData = null;
    this.state.prefetchedToken = null;

    return loadFunction(previousToken);
  }

  hasNextPage(): boolean {
    return this.state.hasMorePages;
  }

  hasPreviousPage(): boolean {
    return this.state.tokenHistory.length > 0;
  }

  reset(): void {
    this.state = {
      items: [],
      continuationToken: null,
      tokenHistory: [],
      prefetchedData: null,
      prefetchedToken: null,
      hasMorePages: false,
      isLoading: false,
    };
  }
}
