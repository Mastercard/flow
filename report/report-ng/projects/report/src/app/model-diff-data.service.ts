import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Subscription } from 'rxjs';
import { tap } from 'rxjs/operators';
import { IndexDataService } from './index-data.service';
import { Entry, Flow, Index, isFlow, isIndex } from './types';

/**
 * Handles loading model data
 */
@Injectable({
  providedIn: 'root'
})
export class ModelDiffDataService {
  private readonly start = "// START_JSON_DATA";
  private readonly end = "// END_JSON_DATA";

  private indices: Map<string, { path: string, index: Index | null, status: string }> = new Map();
  private indexSubs: Map<string, Subscription> = new Map();
  private flows: Map<string, Map<string, Flow>> = new Map();
  private flowSubs: Map<string, Set<Subscription>> = new Map();

  private indexListeners: Map<string, ((label: string) => void)[]> = new Map();
  private flowListeners: Map<string, ((label: string, entry: Entry, flow: Flow) => void)[]> = new Map();

  constructor(
    private thisIndex: IndexDataService,
    private http: HttpClient
  ) { }

  /**
   * Call this when a new path is entered by the user
   * @param label A label for the index (probably either 'from' or 'to')
   * @param path The path itself
   */
  path(label: string, path: string): void {
    this.indices.delete(label);
    this.flows.delete(label);
    // let the listeners know that a new index is on the way
    this.notifyIndexListeners(label);

    if (path.length === 0) {
      this.indices.set(label,
        {
          path: path,
          index: this.thisIndex.get(),
          status: ""
        });
      this.notifyIndexListeners(label);
      this.loadFlows(label);
    }
    else {
      this.indexSubs.get(label)?.unsubscribe();
      this.indexSubs.delete(label);

      const sub = this.http.get(path, { responseType: "text" })
        .pipe(tap(
          body => console.log("fetched " + body.length + " bytes of index from " + path),
          error => {
            console.error("failed to get " + path, error);
            this.indices.set(label, {
              path: path, index: null, status: "Request failed"
            });
            this.notifyIndexListeners(label);
          }
        ))
        .subscribe(page => {
          let s = page.indexOf(this.start);
          let e = page.indexOf(this.end, s);
          if (s != undefined && e != undefined) {
            // parse it
            let json = page.substring(s + this.start.length, e);
            try {
              let data = JSON.parse(json);
              if (isIndex(data)) {
                this.indices.set(label, {
                  path: path, index: data, status: "success"
                });
              }
              else {
                console.error("Not an index ", data);
                this.indices.set(label, {
                  path: path, index: null, status: "Not an index"
                });
              }
            }
            catch (e) {
              console.error("Failed to parse " + json, e);
              this.indices.set(label, {
                path: path, index: null, status: "Failed to parse"
              });
            }
          }
          else {
            console.error("No json content found at " + path);
            this.indices.set(label, {
              path: path, index: null, status: "No data"
            });
          }
          sub.unsubscribe();
          this.indexSubs.set(label, sub);
          this.notifyIndexListeners(label);
          this.loadFlows(label);
        });
      this.indexSubs.set(label, sub);
    }
  }

  private notifyIndexListeners(label: string): void {
    (this.indexListeners.get(label) ?? []).forEach(l => l(label));
  }

  /**
   * Index data access
   * @param label The label for the index (probably either 'from' or 'to')
   * @returns The index data, or null
   */
  index(label: string): { path: string, index: Index | null, status: string } | null {
    return this.indices.get(label) ?? null;
  }

  /**
   * Call this to be notified when index data changes
   * @param label A label for the index (probably either 'from' or 'to') to listen to
   * @param callback Will be supplied with the label of the updated index when changes happen
   */
  onIndex(label: string, callback: (label: string) => void): void {
    let listeners = this.indexListeners.get(label) ?? [];
    listeners.push(callback);
    this.indexListeners.set(label, listeners);

    // if we already have an index, supply it now!
    if (this.indices.has(label)) {
      callback(label);
    }
  }

  private loadFlows(label: string): void {
    // cancel existing loads
    let loads: Set<Subscription> = this.flowSubs.get(label) ?? new Set();
    loads.forEach(s => s.unsubscribe());

    let indexData = this.index(label);
    if (indexData == null || indexData.index == null) {
      // nothing to get yet
      return;
    }
    // start a new set of loads!
    loads = new Set();
    this.flowSubs.set(label, loads);

    let flowData: Map<string, Flow> = new Map();
    this.flows.set(label, flowData);
    indexData.index.entries.forEach((entry, idx) => {
      let path = indexData!.path.replace("index.html", "");
      if (path.length !== 0) {
        path += "/";
      }
      path += "detail/" + entry.detail + ".html";

      const sub = this.http.get(path, { responseType: "text" })
        .pipe(tap(
          body => console.log("fetched " + body.length + " bytes of flow content from " + path),
          error => console.error("Failed to get " + path, error)
        ))
        .subscribe(page => {
          // extract the json content
          let s = page.indexOf(this.start);
          let e = page.indexOf(this.end, s);
          if (s != undefined && e != undefined) {
            // parse it
            let json = page.substring(s + this.start.length, e);
            let data = JSON.parse(json);
            if (isFlow(data)) {
              if (indexData?.path === this.index(label)?.path) {
                // save it
                flowData.set(entry.detail, data);
                this.notifyFlowListeners(label, entry, data);
              }
              else {
                // it's an old request from a previous index. Ignore it.
              }
            }
            else {
              console.error("This isn't a flow at " + path, data);
            }
          }
          else {
            console.error("No json content found at " + path);
          }
          loads.delete(sub);
          sub.unsubscribe();
        });
      loads.add(sub);
    });
  }

  private notifyFlowListeners(label: string, entry: Entry, flow: Flow): void {
    (this.flowListeners.get(label) ?? []).forEach(l => l(label, entry, flow));
  }

  /**
   * 
   * @param label 
   * @returns How many flows have been loaded, as a percentage
   */
  flowLoadProgress(label: string): number {
    let total = this.indices.get(label)?.index?.entries.length ?? 0;
    let loaded = this.flows.get(label)?.size ?? 0;
    return total > 0 ? 100 * loaded / total : 0;
  }

  /**
   * Call this to be supplied with flow data when it changes
   * @param label A label for the index (probably either 'from' or 'to')
   * @param callback Will be supplied with the ordinal of the flow 
   *                 in the index, and the flow data itself
   */
  onFlow(label: string, callback: (label: string, entry: Entry, flow: Flow) => void): void {
    let listeners = this.flowListeners.get(label) ?? [];
    listeners.push(callback);
    this.flowListeners.set(label, listeners);

    // if we already have flows, supply them now!
    this.indices.get(label)?.index?.entries
      .forEach(entry => {
        let flow = this.flowFor(label, entry);
        if (flow !== null) {
          callback(label, entry, flow);
        }
      });
  }

  flowFor(label: string, entry: Entry): Flow | null {
    return this.flows.get(label)?.get(entry.detail) ?? null;
  }
}
