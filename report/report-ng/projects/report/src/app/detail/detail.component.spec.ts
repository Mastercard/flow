import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component, Input } from '@angular/core';
import { DetailComponent } from './detail.component';
import { BasisFetchService } from '../basis-fetch.service';
import { MatMenuModule } from '@angular/material/menu';
import { MatListModule } from '@angular/material/list';
import { DiffType, Display, LogEvent, Options } from '../types';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatTabsModule } from '@angular/material/tabs';
import { MatIconModule } from '@angular/material/icon';
import { MatToolbarModule } from '@angular/material/toolbar';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { SequenceData } from '../flow-sequence/flow-sequence.component';
import { DiffDisplay } from '../text-diff/text-diff.component';

describe('DetailComponent', () => {
  let component: DetailComponent;
  let fixture: ComponentFixture<DetailComponent>;
  let mockBasisFetch;

  beforeEach(async () => {
    mockBasisFetch = jasmine.createSpyObj(['get']);
    await TestBed.configureTestingModule({
      declarations: [
        DetailComponent,

        StubLogView,
        StubFlowSequence,
        StubTransmission,
        StubMarkdown,
        StubViewOptions,
      ],
      providers: [
        { provide: BasisFetchService, useValue: mockBasisFetch },
      ],
      imports: [
        MatMenuModule,
        BrowserAnimationsModule,
        MatSidenavModule,
        MatIconModule,
        MatListModule,
        MatTabsModule,
        MatToolbarModule,
      ],
    })
      .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(DetailComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

@Component({
  selector: 'app-log-view',
  template: ''
})
class StubLogView {
  @Input() logs: LogEvent[] = [];
}

@Component({
  selector: 'app-flow-sequence',
  template: ''
})
class StubFlowSequence {
  @Input() sequence: SequenceData = { entity: [], item: [] };
}

@Component({
  selector: 'app-view-options',
  template: ''
})
class StubViewOptions {
  @Input() options: Options = new Options();
}

@Component({
  selector: 'app-transmission',
  template: ''
})
class StubTransmission {
  @Input() options: Options = new Options();
  @Input() display: Display = Display.Actual;
  @Input() diffType: DiffType = DiffType.Asserted;
  @Input() diffFormat: DiffDisplay = 'unified';
}

@Component({
  selector: 'markdown',
  template: ''
})
class StubMarkdown {
}