import { Component, OnInit } from '@angular/core';
import { Meta, isMeta } from '../types';
import { HttpClient } from '@angular/common/http';
import { tap } from 'rxjs';
import { Title } from '@angular/platform-browser';

@Component({
  selector: 'app-duct-index',
  templateUrl: './duct-index.component.html',
  styleUrls: ['./duct-index.component.css']
})
export class DuctIndexComponent implements OnInit {

  reports: Report[] = [];

  constructor(private http: HttpClient,
    private title: Title) {
  }

  ngOnInit(): void {

    this.title.setTitle("Duct");

    // load the report list
    this.http.get('/list')
      .pipe(tap(
        body => console.log("fetched ", body),
        error => {
          console.error("failed to get /list", error);
          this.reports = [];
        }
      ))
      .subscribe(resData => {
        if (Array.isArray(resData)) {
          this.reports = resData
            .filter(e => isReport(e))
            // most recent first
            .sort((a, b) => b.meta.timestamp - a.meta.timestamp);
          console.log("loaded ", this.reports);
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

interface Counts {
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
