import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { PlatformAuthService } from './platform-auth.service';

const groupHeader = 'X-Platform-Principal-Group-Ids';

export const platformAuthInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(PlatformAuthService);
  return next(request.clone({ setHeaders: { [groupHeader]: auth.groupHeader() } }));
};
