import { inject } from "@angular/core";
import { CanActivateFn, Router } from "@angular/router";
import { AuthService } from "./auth.service";

export const authGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isLoggedIn()) {
    return true;
  }

  router.navigate(["/login"]);
  return false;
};
export const adminGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  const token = authService.getToken();
  if (!token) {
    router.navigate(["/login"]);
    return false;
  }

  try {
    const payload = JSON.parse(atob(token.split(".")[1]));
    const role = payload.role ?? payload.roles ?? "";

    if (role === "ADMIN" || role === "ROLE_ADMIN") {
      return true;
    }
  } catch {
    // invalid token
  }

  router.navigate(["/dashboard"]);
  return false;
};
