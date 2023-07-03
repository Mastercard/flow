import { Component, OnInit } from '@angular/core';

/**
 * One of the primary index views (along with the index itself and
 * the model diff tool) - this view loads flow data in the current
 * model and generates a system diagram showing actor interactions
 */
@Component({
  selector: 'app-system-diagram',
  templateUrl: './system-diagram.component.html',
  styleUrls: ['./system-diagram.component.css']
})
export class SystemDiagramComponent implements OnInit {

  constructor() { }

  ngOnInit(): void {
  }

}
