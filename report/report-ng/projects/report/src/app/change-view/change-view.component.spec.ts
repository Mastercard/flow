import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { ChangeViewComponent } from './change-view.component';
import { FlowDiffService } from '../flow-diff.service';
import { ModelDiffDataService } from '../model-diff-data.service';
import { FlowFilterService } from '../flow-filter.service';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatRippleModule } from '@angular/material/core';
import { MatListModule } from '@angular/material/list';
import { FormsModule } from '@angular/forms';
import { MatSidenavModule } from '@angular/material/sidenav';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { MatIconModule } from '@angular/material/icon';
import { MatDividerModule } from '@angular/material/divider';

describe('ChangeViewComponent', () => {
  let component: ChangeViewComponent;
  let fixture: ComponentFixture<ChangeViewComponent>;
  let mockFds;
  let mockMdds;
  let mockFilter;

  beforeEach(async () => {
    mockFds = jasmine.createSpyObj(['onPairing', 'onFlowData']);
    mockMdds = jasmine.createSpyObj(['index']);
    mockFilter = jasmine.createSpyObj(['onUpdate', 'passes', 'isEmpty'])
    await TestBed.configureTestingModule({
      declarations: [
        ChangeViewComponent,
      ],
      providers: [
        { provide: FlowDiffService, useValue: mockFds },
        { provide: ModelDiffDataService, useValue: mockMdds },
        { provide: FlowFilterService, useValue: mockFilter },
      ],
      imports: [
        RouterTestingModule,
        MatRippleModule,
        MatButtonToggleModule,
        FormsModule,
        BrowserAnimationsModule,
        MatListModule,
        MatIconModule,
        MatDividerModule,
        MatSidenavModule,
      ]
    })
      .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(ChangeViewComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
