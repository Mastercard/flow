import { Component, Input, OnInit, inject } from '@angular/core';
import { IconEmbedService } from '../icon-embed.service';
import { Report } from '../duct.service';

@Component({
  selector: 'app-duct-index-item',
  templateUrl: './duct-index-item.component.html',
  styleUrls: ['./duct-index-item.component.css']
})
export class DuctIndexItemComponent implements OnInit {

  @Input() report: Report = {
    meta: { modelTitle: "", testTitle: "", timestamp: 0 },
    counts: { pass: 0, fail: 0, skip: 0, error: 0 },
    path: ""
  };
  time: string = "";

  constructor() {
    const icons = inject(IconEmbedService);

    icons.register("check_circle_outline", "error_outline", "help_outline", "new_releases");
  }

  ngOnInit(): void {
    this.time = new Date(this.report.meta.timestamp).toLocaleString();
  }

}
