import { createApp } from 'vue'
import './style.css'
import App from './App.vue'
import Varlet from '@varlet/ui'
import '@varlet/ui/es/style'
import 'element-plus/dist/index.css'
import ElementPlus from 'element-plus'

const app = createApp(App)
app.use(ElementPlus)
app.use(Varlet).mount('#app')
