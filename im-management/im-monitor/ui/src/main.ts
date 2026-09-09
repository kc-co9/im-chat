import { createApp } from "vue";
import ElementPlus from "element-plus";
import "element-plus/dist/index.css";
import App from "./App.vue";
import { router } from "./router";
import "./style.css";
import { auth } from "./state/auth";
import "./styles/tokens.css";
import "./styles/console.css";

window.addEventListener("iam:unauthorized", () => {
  auth.principal = undefined;
  auth.checked = true;
  location.assign("/iam/login");
});

createApp(App).use(ElementPlus).use(router).mount("#app");
