import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component, Input } from '@angular/core';
import { IndexComponent } from './index.component';
import { RouterTestingModule } from '@angular/router/testing';
import { IndexDataService } from '../index-data.service';
import { MatToolbarModule } from '@angular/material/toolbar';
import { Entry } from '../types';

describe('IndexComponent', () => {
  let component: IndexComponent;
  let fixture: ComponentFixture<IndexComponent>;
  let mockIndexData;

  beforeEach(async () => {
    mockIndexData = jasmine.createSpyObj(['get', 'isValid', 'raw']);
    mockIndexData.get.and.returnValue({
      meta: {
        timestamp: 12345,
        modelTitle: "model title",
        testTitle: "test title",
      }
    });
    await TestBed.configureTestingModule({
      declarations: [
        IndexComponent,
        StubMenu,
        StubFlowFilter,
        StubTagSummary,
      ],
      providers: [
        { provide: IndexDataService, useValue: mockIndexData },
      ],
      imports: [
        RouterTestingModule,
        MatToolbarModule,
      ],
    })
      .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(IndexComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

@Component({
  selector: 'app-menu',
  template: ''
})
class StubMenu {
}
@Component({
  selector: 'app-flow-filter',
  template: ''
})
class StubFlowFilter {
  @Input() tags: Set<string> = new Set();
}
@Component({
  selector: 'app-tag-summary',
  template: ''
})
class StubTagSummary {
  @Input() entries: Entry[] = [];
}