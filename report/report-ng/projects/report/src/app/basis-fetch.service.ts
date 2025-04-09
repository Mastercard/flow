import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { tap } from 'rxjs/operators';
import { toSequence } from './detail/detail.component';
import { SequenceData } from './flow-sequence/flow-sequence.component';
import { Action, isAction } from './seq-action/seq-action.component';
import { isFlow } from './types';

@Injectable({
  providedIn: 'root'
})
export class BasisFetchService {
  private http = inject(HttpClient);


  readonly start = "// START_JSON_DATA";
  readonly end = "// END_JSON_DATA";

  private basis?: SequenceData = undefined;

  private callbacks: (() => void)[] = [];

  /**
   * Invoked when the detail json is decoded
   * @param detail The address of the basis flow
   */
  get(detail: string | undefined): void {
    if (detail != undefined) {
      let resp = this.http.get(detail + ".html",
        { responseType: "text" })
        .pipe(tap(
          body => console.log("fetched " + body.length + " bytes of basis content"),
          error => console.error("Failed to get ", error)
        ));

      resp.subscribe(page => {
        // extract the json content
        let s = page.indexOf(this.start);
        let e = page.indexOf(this.end, s);
        if (s != -1 && e != -1) {
          try {
            // parse it
            let json = page.substring(s + this.start.length, e);
            let data = JSON.parse(json);
            if (isFlow(data)) {
              // save the flow
              this.basis = toSequence(data);
              // notify listeners
              this.callbacks.forEach(cb => cb());
            }
            else {
              console.error("This isn't a flow!", data);
            }
          } catch (e) {
            console.error("Failed to parse!", e);
          }
        }
        else {
          console.error("No json content found!");
        }
      });
    }
  }

  /**
   * @param callback This will be called when the basis data is available
   */
  onLoad(callback: () => void) {
    this.callbacks.push(callback);
  }

  /**
   * Invoked when a message is displayed
   * @param action The selected message in the current flow
   * @returns  The human-readable text of the corresponding message in the basis
   *           flow, or null if there is no such message
   */
  message(action: Action): string | null {
    // find the actions from the basis that are between the same actors
    // and that are of the same type (i.e.: request or response)
    let possibles = this.basis?.item
      .filter(isAction)
      .filter(a => action.fromName === a.fromName
        && action.toName === a.toName
        && action.label === a.label)
      ?? [];

    // oh there were none? Nevermind then!
    if (possibles.length == 0) {
      return null;
    }

    // now try and find the closest tag match
    let bestIdx = -1;
    let bestTagMatch = 2;
    for (let idx = 0; idx < possibles.length; idx++) {
      const e = possibles[idx];
      let tagMatch = setDistance(action.tags, e.tags);
      if (tagMatch < bestTagMatch) {
        bestIdx = idx;
        bestTagMatch = tagMatch;
      }
    }

    return possibles[bestIdx]?.transmission?.full.expect ?? null;
  }

}

/**
 * Computes a normalised distance metric between two sets
 * @param a The first set
 * @param b  The second set
 * @returns A distance metric, ranging from 0 if the sets are identical to 1 if they are disjoint
 */
export function setDistance(a: string[], b: string[]) {
  let union = new Set(a);
  b.forEach(e => union.add(e));

  if (union.size == 0) {
    return 0;
  }

  let left = new Set(a);
  b.forEach(e => left.delete(e));
  let right = new Set(b);
  a.forEach(e => right.delete(e));

  let diffSize = left.size + right.size;
  return diffSize / union.size;
}
