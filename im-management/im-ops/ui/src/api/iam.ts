import { http } from "./http";

export interface IamPrincipal {
  administratorId: number;
  username: string;
  appKey: string;
  authorities: string[];
}

export const iamApi = {
  currentPrincipal: () => http.get<IamPrincipal>("/iam/me"),
  logout: () => http.post("/iam/logout"),
};
