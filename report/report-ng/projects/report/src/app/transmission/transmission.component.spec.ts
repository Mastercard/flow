import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component, Input } from '@angular/core';
import { TransmissionComponent } from './transmission.component';
import { BasisFetchService } from '../basis-fetch.service';
import { DataDisplay, Options } from '../types';

describe('TransmissionComponent', () => {
  let component: TransmissionComponent;
  let fixture: ComponentFixture<TransmissionComponent>;
  let mockBasis;

  beforeEach(async () => {
    mockBasis = jasmine.createSpyObj(['onLoad']);
    await TestBed.configureTestingModule({
      declarations: [
        TransmissionComponent,
        StubMsgView,
      ],
      providers: [
        { provide: BasisFetchService, useValue: mockBasis },
      ],
    })
      .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(TransmissionComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

@Component({
  selector: 'app-msg-view',
  template: ''
})
class StubMsgView {
  @Input() options: Options = new Options();
  @Input() dataDisplay: DataDisplay = DataDisplay.Human;
  @Input() human?: string;
  @Input() base64?: string;
}