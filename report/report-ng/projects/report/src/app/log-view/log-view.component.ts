import { Component, OnInit, Input, inject } from '@angular/core';
import { MatTableDataSource } from '@angular/material/table';
import { QueryService } from '../query.service';
import { LogEvent } from '../types';
import { IconEmbedService } from '../icon-embed.service';

@Component({
  selector: 'app-log-view',
  templateUrl: './log-view.component.html',
  styleUrls: ['./log-view.component.css']
})
export class LogViewComponent implements OnInit {
  private query = inject(QueryService);
  private icons = inject(IconEmbedService);


  displayedColumns: string[] = ['time', 'level', 'source', 'message'];
  @Input() logs: LogEvent[] = [];
  dataSource = new MatTableDataSource(this.logs);

  allLevels: Set<string> = new Set();
  levels: string[];
  sourcefilter: string;
  messagefilter: string;
  firstIdx: number = 0;

  constructor() {
    const query = this.query;
    const icons = this.icons;

    this.sourcefilter = query.get("sf", "");
    this.messagefilter = query.get("mf", "");
    this.levels = query.get("lv", "").split(",");
    icons.register("close");
  }

  ngOnInit(): void {
    this.dataSource = new MatTableDataSource(this.logs);
    this.logs.forEach(e => this.allLevels.add(e.level));
    this.firstIdx = this.logs.findIndex(e => this.isParseable(e.time));

    this.levels = this.levels.filter(l => this.allLevels.has(l));

    this.dataSource.filterPredicate = i => this.eventFilter(i);
    this.applyFilters();
  }

  isParseable(time: string): boolean {
    return !isNaN(Date.parse(time));
  }

  startDelta(index: number): number {
    let start = Date.parse(this.logs[0].time)
    let now = Date.parse(this.dataSource.filteredData[index].time);
    return ((now - start) / 1000);
  }

  prevDelta(index: number): number {
    let previous = Date.parse(this.dataSource.filteredData[index > 0 ? index - 1 : 0].time);
    let now = Date.parse(this.dataSource.filteredData[index].time);
    return (now - previous);
  }

  shortSource(source: string): string {
    let i = source.lastIndexOf('.');
    if (i >= 0) {
      source = source.substr(i + 1);
    }
    return source;
  }

  applyFilters() {
    // showing zero levels? show them all instead!
    if (this.levels.length == 0) {
      this.allLevels.forEach(l => this.levels.push(l));
    }

    if (this.sourcefilter.length != 0) {
      this.query.set("sf", this.sourcefilter);
    }
    else {
      this.query.delete("sf");
    }
    if (this.messagefilter.length != 0) {
      this.query.set("mf", this.messagefilter);
    }
    else {
      this.query.delete("mf");
    }
    if (this.levels.length !== 0 && this.levels.length !== this.allLevels.size) {
      this.query.set("lv", this.levels.join(","));
    }
    else {
      this.query.delete("lv");
    }

    // poke the table to refilter. Don't give it the *actual* filter
    // values, as the table won't bother filtering at all if the value 
    // is empty
    this.dataSource.filter = 'ignore';
  }

  eventFilter(event: LogEvent): boolean {
    let sf = this.sourcefilter.toLowerCase();
    let mf = this.messagefilter.toLowerCase();
    return this.levels.includes(event.level)
      && event.message.toLowerCase().includes(mf)
      && event.source.toLowerCase().includes(sf);
  }

}
