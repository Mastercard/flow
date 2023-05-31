import { ComponentFixture, TestBed } from '@angular/core/testing';

import { MsgSearchInputComponent } from './msg-search-input.component';
import { MatIconModule } from '@angular/material/icon';

describe('MsgSearchInputComponent', () => {
  let component: MsgSearchInputComponent;
  let fixture: ComponentFixture<MsgSearchInputComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [MsgSearchInputComponent],
      imports: [
        MatIconModule
      ],
    })
      .compileComponents();

    fixture = TestBed.createComponent(MsgSearchInputComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
