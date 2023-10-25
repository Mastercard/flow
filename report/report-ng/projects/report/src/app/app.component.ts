import { Component } from '@angular/core';
import { DuctFlag, Flow, Index, isDuctFlag, isFlow, isIndex } from './types';
import { HttpClient } from '@angular/common/http';
import { tap } from 'rxjs';

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

  constructor(private http: HttpClient) { }

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

}
