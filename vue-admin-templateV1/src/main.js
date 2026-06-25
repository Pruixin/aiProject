import { createApp } from "vue";
import { createPinia } from "pinia";
import ElementPlus from "element-plus";
import "element-plus/dist/index.css";
import App from "./App.vue";
import { addRoutes, router }  from "./router";
import zhCn from "element-plus/es/locale/lang/zh-cn";
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import piniaPluginPersistedstate from 'pinia-plugin-persistedstate';
import { useUserInfoStore } from "./stores/userInfo";
import { useUserTokenStore } from "./stores/user";
import './router/permission'
import "nprogress/nprogress.css"



const app = createApp(App)


for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}
const pinia =  createPinia()
pinia.use(piniaPluginPersistedstate)
app.use(pinia);

const userTokenStore = useUserTokenStore(pinia)
const userInfoStore = useUserInfoStore(pinia)
if (userTokenStore.tokenValue && userInfoStore.menus.length) {
  addRoutes(userInfoStore.menus)
}

app.use(router);

app.use(ElementPlus, {
  locale: zhCn,
});

app.mount("#app");
