import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { tap } from 'rxjs';
import { Meta, isMeta } from './types';

/**
 * Service for interaction with the duct report-serving application
 */
@Injectable({
  providedIn: 'root'
})
export class DuctService {
  private http = inject(HttpClient);


  /**
   * Starts the heartbeat. Every 30 seconds we'll
   * send a GET to /heartbeat. This lets duct know
   * that someone is still viewing the page so that
   * it doesn't kill itself. If any of these GET
   * requests fail, then we'll assume that either
   * duct has died or that we were never actually
   * being served by it. We won't bother making any
   * more requests after that.
   */
  startHeartbeat(): void {
    // try and hit duct's heartbeat endpoint. Duct will kill
    // itself if it thinks no-one is viewing the pages that it's serving
    let client = this.http;
    let path = "/heartbeat";
    let interval = setInterval(function () {
      client.get(path, { responseType: "text" })
        .pipe(tap(
          body => console.log(path + " yielded " + body),
          error => {
            console.error("failed to get heartbeat", error);
            // either duct is dead or we're not being served by duct.
            // Either way, there's no point continuing
            clearInterval(interval);
          }
        ))
        .subscribe(res => {
          // we don't need to do anything with the response, it's
          // just important that we make the request
        });
    },
      30000);
  }

  /**
   * Loads the index from duct
   * @param callback what to do with the index data
   */
  loadIndex(callback: (index: Report[]) => void): void {
    this.http.get('/list')
      .pipe(tap(
        body => console.log("fetched ", body),
        error => {
          console.error("failed to get /list", error);
          callback([]);
        }
      ))
      .subscribe(resData => {
        if (Array.isArray(resData)) {
          let reports = resData
            .filter(e => isReport(e))
            // most recent first
            .sort((a, b) => b.meta.timestamp - a.meta.timestamp);
          console.log("loaded " + reports.length + " reports");
          callback(reports);
        }
      });
  }
}

export interface Report {
  meta: Meta;
  counts: Counts;
  path: string;
}

function isReport(data: any): data is Report {
  return data
    && isMeta(data.meta)
    && isCounts(data.counts)
    && typeof data.path === 'string';
}

export interface Counts {
  pass: number;
  fail: number;
  skip: number;
  error: number;
}

function isCounts(data: any): data is Counts {
  return data
    && typeof data.pass === 'number'
    && typeof data.fail === 'number'
    && typeof data.skip === 'number'
    && typeof data.error === 'number';
}
