import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
// 引入顺序有意义：先加载 Element Plus 自带样式，再用 tokens.css 覆盖它的 CSS 变量。
// 顺序颠倒会让组件回退到默认蓝色主题，出现"新页面旧组件"的割裂。
import 'element-plus/dist/index.css'
import './styles/tokens.css'
import './styles/base.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'

const app = createApp(App)

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })
app.mount('#app')
