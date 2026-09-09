import { createRouter, createWebHashHistory } from "vue-router";
import OverviewView from "./views/OverviewView.vue";
import { auth, loadPrincipal } from "./state/auth";

export const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    {
      path: "/",
      component: OverviewView,
      meta: { authority: "monitor:overview:read" },
    },
    {
      path: "/brokers",
      component: () => import("./views/BrokersView.vue"),
      meta: { authority: "monitor:broker:read" },
    },
    {
      path: "/gateways",
      component: () => import("./views/GatewaysView.vue"),
      meta: { authority: "monitor:gateway:read" },
    },
    {
      path: "/connections",
      component: () => import("./views/ConnectionsView.vue"),
      meta: { authority: "monitor:connection:read" },
    },
    {
      path: "/diagnostics",
      component: () => import("./views/DiagnosticsView.vue"),
      meta: { authority: "monitor:diagnostic:read" },
    },
  ],
});

router.beforeEach(async (to) => {
  if (!auth.checked) await loadPrincipal();
  if (!auth.principal) {
    window.location.assign(
      `/iam/login?continue=${encodeURIComponent(to.fullPath)}`,
    );
    return false;
  }
  const authority = to.meta.authority as string | undefined;
  return !authority || canAccess(authority) ? true : false;
});

function canAccess(authority: string) {
  return auth.principal?.authorities.includes(authority) ?? false;
}
