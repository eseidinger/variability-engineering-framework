import { HttpInterceptorFn } from '@angular/common/http';

const csrfCookie = '__Host-platform-csrf';
const csrfHeader = 'X-Platform-CSRF-Token';
const writeMethods = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

export const platformCsrfInterceptor: HttpInterceptorFn = (request, next) => {
  if (!writeMethods.has(request.method)) {
    return next(request);
  }

  const token = document.cookie.split('; ')
    .find((cookie) => cookie.startsWith(`${csrfCookie}=`))
    ?.slice(csrfCookie.length + 1);
  return next(token ? request.clone({ setHeaders: { [csrfHeader]: decodeURIComponent(token) } }) : request);
};
