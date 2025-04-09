import { Location } from '@angular/common';
import { Injectable, inject } from '@angular/core';

/**
 * Manages the URL query in the detail page - allows
 * deep-linking to a particular view of a particular message
 */
@Injectable({
  providedIn: 'root'
})
export class QueryService {
  private location = inject(Location);


  private params: Map<string, string> = new Map();

  constructor() {
    const location = this.location;

    const i = location.path().indexOf('?');
    if (i >= 0) {
      const usp = new URLSearchParams(location.path().substr(i));
      usp.forEach((value, key) => this.params.set(key, value));
    }
  }

  /**
   * Updates the URL query
   * @param key The parameter to set
   * @param value  The value
   */
  set(key: string, value: string): void {
    this.params.set(key, value);
    this.updateQuery();
  }

  /**
   * Updates the URL query
   * @param key The parameter to remove
   */
  delete(key: string) {
    this.params.delete(key);
    this.updateQuery();
  }

  private updateQuery() {
    let path = this.location.path();
    const i = path.indexOf('?');
    if (i >= 0) {
      path = path.substr(0, i);
    }

    try {
      const usp: URLSearchParams = new URLSearchParams();
      this.params.forEach((value, key) => usp.append(key, value));
      this.location.replaceState(path, usp.toString());
    } catch (e) {
      // url params are a nice to have, but they don't work when serving from file system
    }
  }

  /**
   * @param key The parameter to retrieve
   * @param defaultValue  The value to return if the named parameter is not available
   * @returns The value of the named parameter, or the supplied default
   */
  get(key: string, defaultValue: string): string {
    return this.params.get(key) ?? defaultValue;
  }

}
