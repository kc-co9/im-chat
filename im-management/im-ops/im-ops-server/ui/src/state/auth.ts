import { reactive } from "vue";
import { iamApi, type IamPrincipal } from "../api/iam";

export const auth = reactive<{ principal?: IamPrincipal; checked: boolean }>({
  checked: false,
});

export async function loadPrincipal() {
  try {
    auth.principal = (await iamApi.currentPrincipal()).data;
  } catch {
    auth.principal = undefined;
  }
  auth.checked = true;
}

export function can(authority: string) {
  return auth.principal?.authorities.includes(authority) ?? false;
}
