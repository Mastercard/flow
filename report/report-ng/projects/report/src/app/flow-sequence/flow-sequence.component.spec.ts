import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component } from '@angular/core';
import { FlowSequenceComponent } from './flow-sequence.component';

describe('FlowSequenceComponent', () => {
  let component: FlowSequenceComponent;
  let fixture: ComponentFixture<FlowSequenceComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [
        FlowSequenceComponent,
        StubMsgSearchInput,
      ],
    })
      .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(FlowSequenceComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

@Component({
  selector: 'app-msg-search-input',
  template: ''
})
class StubMsgSearchInput {
}
