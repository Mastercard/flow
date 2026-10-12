import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
    name: 'enumIterate',
    standalone: false
})
export class EnumIteratePipe implements PipeTransform {

  transform<O extends object, K extends keyof O = keyof O>(obj: O): K[] {
    return Object.keys(obj).filter(k => Number.isNaN(+k)) as K[];
  }

}

