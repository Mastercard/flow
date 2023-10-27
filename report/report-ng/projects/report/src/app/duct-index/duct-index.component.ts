import { Component, OnInit } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { DuctService, Report } from '../duct.service';

@Component({
  selector: 'app-duct-index',
  templateUrl: './duct-index.component.html',
  styleUrls: ['./duct-index.component.css']
})
export class DuctIndexComponent implements OnInit {

  reports: Report[] = [];

  constructor(
    private ductService: DuctService,
    private title: Title) {
  }

  ngOnInit(): void {
    this.title.setTitle("Duct");
    this.ductService.loadIndex(loaded => this.reports = loaded);
  }
}

