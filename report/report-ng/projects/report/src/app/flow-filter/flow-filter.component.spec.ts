import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component, Input } from '@angular/core';
import { FlowFilterComponent } from './flow-filter.component';
import { RouterTestingModule } from '@angular/router/testing';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { Type } from '../flow-filter.service';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatExpansionModule, MatExpansionPanel, MatExpansionPanelDescription, MatExpansionPanelHeader, MatExpansionPanelTitle } from '@angular/material/expansion';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { FormsModule } from '@angular/forms';
import { MatInputModule } from '@angular/material/input';
import { MatRippleModule } from '@angular/material/core';
import { MatButtonModule } from '@angular/material/button';

describe('FlowFilterComponent', () => {
  let component: FlowFilterComponent;
  let fixture: ComponentFixture<FlowFilterComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [
        FlowFilterComponent,
        StubTagFilter,
      ],
      imports: [
        RouterTestingModule,
        BrowserAnimationsModule,
        FormsModule,
        MatRippleModule,
        MatExpansionModule,
        MatInputModule,
        MatFormFieldModule,
        MatButtonModule,
        MatButtonToggleModule,
        MatIconModule,
      ]
    })
      .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(FlowFilterComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});


@Component({
  selector: 'app-tag-filter',
  template: ''
})
class StubTagFilter {
  @Input() type: Type = Type.Include;
  @Input() tags: Set<string> = new Set();
  @Input() disabled: boolean = false;
}