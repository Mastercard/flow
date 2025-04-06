import { Component, OnInit, inject } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { DuctService, Report } from '../duct.service';

@Component({
  selector: 'app-duct-index',
  templateUrl: './duct-index.component.html',
  styleUrls: ['./duct-index.component.css']
})
export class DuctIndexComponent implements OnInit {
  private ductService = inject(DuctService);
  private title = inject(Title);


  reports: Report[] = [];

  ngOnInit(): void {
    this.title.setTitle("Duct");
    this.ductService.loadIndex(loaded => this.reports = loaded);
  }
}

