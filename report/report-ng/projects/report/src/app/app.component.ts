import { Component } from '@angular/core';
import { DuctFlag, Flow, Index, isDuctFlag, isFlow, isIndex } from './types';

// global variable defined by inline script element in index.html
// declaration makes it available to typescript code
declare var data: any;

/**
 * This is the report app entrypoint. All it does is determine
 * what sort of data has been embedded in the index.html file
 * and then present the apropriate component (or an error message)
 */
@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent {
  flow?: Flow;
  index?: Index;
  duct?: DuctFlag;
  error: boolean = false;
  error_data?: any;

  ngOnInit(): void {
    if (isIndex(data)) {
      this.index = data;
    }
    else if (isFlow(data)) {
      this.flow = data;
    }
    else if (isDuctFlag(data)) {
      this.duct = data;
    }
    else {
      this.error = true;
      this.error_data = data;
    }
  }
}
