import { Component, Input } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { FlowNavItemComponent } from './flow-nav-item.component';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';

describe('FlowNavItemComponent', () => {
  let component: FlowNavItemComponent;
  let fixture: ComponentFixture<FlowNavItemComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [
        FlowNavItemComponent,
        StubTag,
      ],
      imports: [
        MatListModule,
        MatIconModule,
      ],
    })
      .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(FlowNavItemComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('keeps tags outside the flow navigation link', () => {
    component.entry = {
      description: 'A flow with tags',
      tags: ['PASS', 'domain'],
      detail: 'flow',
    };
    fixture.detectChanges();

    const link: HTMLAnchorElement = fixture.nativeElement.querySelector('a');
    const metadata: HTMLElement = fixture.nativeElement.querySelector('[matListItemMeta]');

    expect(link.textContent).toContain('A flow with tags');
    expect(link.querySelectorAll('app-tag').length).toBe(0);
    expect(metadata.querySelectorAll('app-tag').length).toBe(2);
  });

  it('presents the complete flow description as normal wrapping text', () => {
    component.entry = {
      description: 'A deliberately long flow description that must remain readable when the row is narrow',
      tags: ['PASS', 'domain'],
      detail: 'flow',
    };
    fixture.nativeElement.style.display = 'block';
    fixture.nativeElement.style.width = '240px';
    fixture.detectChanges();

    const link: HTMLAnchorElement = fixture.nativeElement.querySelector('a');
    const description: HTMLElement = link.querySelector('.description')!;
    const item: HTMLElement = fixture.nativeElement.querySelector('mat-list-item');
    const linkStyle = getComputedStyle(link);

    expect(description.textContent!.trim()).toBe(component.entry.description);
    expect(linkStyle.textDecorationLine).toBe('none');
    expect(linkStyle.whiteSpace).toBe('normal');
    expect(getComputedStyle(description).overflowWrap).toBe('anywhere');
    expect(item.classList).toContain('flow-nav-row');
  });
});

@Component({
  selector: 'app-tag',
  template: '{{tag}}',
  standalone: false,
})
class StubTag {
  @Input() tag: string = '';
}
