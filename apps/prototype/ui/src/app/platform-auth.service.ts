import { Injectable, signal } from '@angular/core';

export type PlatformGroup = 'user' | 'guest';

const storageKey = 'variability-engineering.platform-group';

const groupIds: Record<PlatformGroup, string> = {
  user: 'grp_d8113bc5c0ca4b6092ae13386310268f',
  guest: 'grp_0c98f1205bfe456a8517417a38de98e0',
};

@Injectable({ providedIn: 'root' })
export class PlatformAuthService {
  readonly group = signal<PlatformGroup>(this.readGroup());

  setGroup(group: PlatformGroup): void {
    localStorage.setItem(storageKey, group);
    this.group.set(group);
  }

  groupHeader(): string {
    return groupIds[this.group()];
  }

  private readGroup(): PlatformGroup {
    return localStorage.getItem(storageKey) === 'guest' ? 'guest' : 'user';
  }
}
