import { Injectable, inject } from '@angular/core';
import { ActivatedRoute, ParamMap } from '@angular/router';
import { Observable } from 'rxjs';
import { Entry } from './types';

@Injectable({
  providedIn: 'root'
})
export class FlowFilterService {
  private route = inject(ActivatedRoute);


  constructor() {
    const route = this.route;

    this.fromParams(route.queryParamMap);
  }

  private locked: boolean = false;
  private descMatch: string = "";
  private include: Set<string> = new Set();
  private exclude: Set<string> = new Set();
  private callbacks: (() => void)[] = [];

  toQuery(): string {
    return this.buildQuery(new URLSearchParams()).toString();
  }

  buildQuery(usp: URLSearchParams): URLSearchParams {
    if (this.locked) {
      usp.append("flck", "1");
    }
    if (this.descMatch) {
      usp.append("dsc", this.descMatch);
    }
    this.include.forEach(i => usp.append("inc", i));
    this.exclude.forEach(i => usp.append("exc", i));
    return usp;
  }

  fromParams(params: Observable<ParamMap>): void {
    params.subscribe(p => {
      this.locked = (p.get("flck") || "") === "1"
      this.descMatch = p.get("dsc") || "";
      this.include.clear();
      p.getAll("inc").forEach(i => this.include.add(i));
      this.exclude.clear();
      p.getAll("exc").forEach(i => this.exclude.add(i));

      this.callbacks.forEach(cb => cb());
    });
  }

  /**
   * @param entry An index entry
   * @returns true if the entry passes the filters
   */
  passes(entry: Entry): boolean {
    let dsc;
    try {
      dsc = this.descMatch.length == 0
        || entry.description.search(new RegExp(this.descMatch)) != -1;
    }
    catch (e) {
      // failed to compile regex, just look for text match
      dsc = this.descMatch.length == 0
        || entry.description.includes(this.descMatch);
    }

    let included = this.include.size == 0
      || Array.from(this.include).every(inc => entry.tags.includes(inc));
    let excluded = this.exclude.size != 0
      && Array.from(this.exclude).some(exc => entry.tags.includes(exc));

    return dsc && included && !excluded;
  }

  /**
   * Call this to clear all filters
   */
  clear(): void {
    if (this.locked) {
      return;
    }
    this.descMatch = "";
    this.include.clear();
    this.exclude.clear();

    this.callbacks.forEach(cb => cb());
  }

  lock(l: boolean): void {
    this.locked = l;
    this.callbacks.forEach(cb => cb());
  }

  isLocked(): boolean {
    return this.locked;
  }

  isEmpty(): boolean {
    return this.descMatch.length === 0
      && this.include.size === 0
      && this.exclude.size === 0;
  }

  /**
   * @returns  all filtered values
   */
  all(): Set<string> {
    let all: Set<string> = new Set(this.include);
    this.exclude.forEach(e => all.add(e));
    return all;
  }

  /**
   * Call this to update the description match
   * @param dm the new regex
   */
  setDescriptionMatch(dm: string): void {
    if (this.locked) {
      return;
    }
    this.descMatch = dm;
    this.callbacks.forEach(cb => cb());
  }

  /**
   * @returns  The current description match
   */
  getDescriptionMatch(): string {
    return this.descMatch;
  }

  /**
   * Adds an include filter
   * @param tag the filtered tag
   */
  addInclude(tag: string): void {
    if (this.locked) {
      return;
    }
    this.include.add(tag);
    this.callbacks.forEach(cb => cb());
  }

  /**
   * @param tag Call this to switch a tag from include to exclude and vice versa
   */
  flip(tag: string) {
    if (this.include.delete(tag)) {
      this.exclude.add(tag);
    }
    else if (this.exclude.delete(tag)) {
      this.include.add(tag);
    }
    this.callbacks.forEach(cb => cb());
  }

  /**
   * Call this to update the filters
   * @param type  The filter type
   * @param values The new filter values
   */
  set(type: Type, values: Set<string>): void {
    if (this.locked) {
      return;
    }
    if (type === Type.Include) {
      this.include = values;
    }
    else if (type === Type.Exclude) {
      this.exclude = values;
    }
    this.callbacks.forEach(cb => cb());
  }

  /**
   * @param type The filter type
   * @returns The filter values
   */
  get(type: Type): Set<string> {
    if (type === Type.Include) {
      return this.include;
    }
    else if (type === Type.Exclude) {
      return this.exclude;
    }
    return new Set();
  }

  /**
   * Call this to register interest in the state of the filters
   * @param callback called when the filters are updated
   */
  onUpdate(callback: () => void) {
    this.callbacks.push(callback);
  }
}

export enum Type {
  Include = "Include",
  Exclude = "Exclude",
}
