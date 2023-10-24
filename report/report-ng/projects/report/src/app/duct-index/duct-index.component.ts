import { Component, AfterViewInit, ViewChild } from '@angular/core';
import { Meta, isMeta } from '../types';

@Component({
  selector: 'app-duct-index',
  templateUrl: './duct-index.component.html',
  styleUrls: ['./duct-index.component.css']
})
export class DuctIndexComponent {

  reports: Report[] = [
    {
      meta: { modelTitle: "abc", testTitle: "def", timestamp: 12345 },
      counts: { pass: 1, fail: 2, skip: 3, error: 4 },
      path: "link/to/first/report"
    },
    {
      meta: { modelTitle: "ghi", testTitle: "jkl", timestamp: 24680 },
      counts: { pass: 5, fail: 0, skip: 7, error: 8 },
      path: "link/to/second/report"
    },
    {
      meta: { modelTitle: "abc", testTitle: "def", timestamp: 1456216437826 },
      counts: { pass: 1, fail: 2, skip: 3, error: 0 },
      path: "link/to/third/report"
    },
  ];

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
    && data.path && typeof data.path === 'string';
}

interface Counts {
  pass: number;
  fail: number;
  skip: number;
  error: number;
}

function isCounts(data: any): data is Counts {
  return data
    && data.pass && typeof data.pass === 'number'
    && data.fail && typeof data.fail === 'number'
    && data.skip && typeof data.skip === 'number'
    && data.error && typeof data.error === 'number';
}
