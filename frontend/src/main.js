import Vue from 'vue'
import ElementUI from 'element-ui'
import 'element-ui/lib/theme-chalk/index.css'
import App from './App.vue'
import './style.css'

Vue.use(ElementUI, { size: 'small' })
new Vue({ render: create => create(App) }).$mount('#app')
