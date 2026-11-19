<template>
  <div v-if="booting" class="boot" v-loading="true" element-loading-text="正在进入工作台"></div>
  <div v-else-if="!user" class="login">
    <section class="login-brand">
      <div class="brand">
        <span class="brand-icon" aria-hidden="true"><i class="el-icon-connection"></i></span>
        <span class="brand-words"><strong>{{ brand.name }}</strong><small>{{ brand.subtitle }}</small></span>
      </div>
      <div class="login-intro"><h1>客户管理<br />从这里开始</h1><p>在一个工作台内跟进线索、管理商机并处理客户反馈。</p></div>
      <div class="login-rings" aria-hidden="true"></div>
    </section>
    <section class="login-content">
      <el-form class="login-form" @submit.native.prevent="signIn()">
        <h2>登录{{ brand.name }}</h2><p>使用你的账号进入工作台</p>
        <el-form-item label="账号"><el-input v-model.trim="login.username" prefix-icon="el-icon-user" autocomplete="username" size="medium" placeholder="请输入账号" /></el-form-item>
        <el-form-item label="密码"><el-input v-model="login.password" prefix-icon="el-icon-lock" autocomplete="current-password" show-password size="medium" placeholder="请输入密码" /></el-form-item>
        <el-alert v-if="loginError" :title="loginError" type="error" :closable="false" show-icon />
        <el-button class="login-submit" native-type="submit" type="primary" size="medium" :loading="busy && !activeDemo" :disabled="Boolean(activeDemo)">登录</el-button>
        <div v-if="demoAccounts.length" class="demo-login">
          <p>测试账号快捷登录</p>
          <div class="demo-login-buttons">
            <el-button v-for="demo in demoAccounts" :key="demo.username" :loading="activeDemo === demo.username"
                       :disabled="busy" plain @click="signIn(demo.username)">{{ demo.displayName }}</el-button>
          </div>
        </div>
      </el-form>
    </section>
  </div>
  <div v-else class="shell" :class="{ collapsed }">
    <aside class="sidebar">
      <button class="brand" type="button" :aria-label="'返回' + brand.name + '工作台'" @click="open('home')">
        <span class="brand-icon" aria-hidden="true"><i class="el-icon-connection"></i></span>
        <span class="brand-words"><strong>{{ brand.name }}</strong><small>{{ brand.subtitle }}</small></span>
      </button>
      <nav class="nav-wrap" aria-label="业务导航">
        <button :class="['nav-link', { active: route === 'home' }]" aria-label="工作台" @click="open('home')"><i class="el-icon-s-home"></i><span>工作台</span></button>
        <div class="nav-section">业务管理</div>
        <div v-for="group in groups" :key="group.name" class="nav-group">
          <button :class="['nav-link', { selected: group.children.some(item => item.key === route) }]" :title="group.name" :aria-label="group.name"
                  :aria-expanded="String(expanded.includes(group.name))" @click="toggle(group)">
            <i :class="group.icon"></i><span>{{ group.name }}</span><i v-if="group.children.length > 1" :class="['nav-arrow', expanded.includes(group.name) ? 'el-icon-arrow-up' : 'el-icon-arrow-down']"></i>
          </button>
          <div v-if="!collapsed && expanded.includes(group.name) && group.children.length > 1" class="nav-children">
            <button v-for="item in group.children" :key="item.key" :class="{ active: route === item.key }" @click="open(item.key)">{{ item.label }}</button>
          </div>
        </div>
      </nav>
      <div class="sidebar-foot"><i class="el-icon-s-platform"></i><span>销售与客户体验</span></div>
    </aside>
    <div class="main-shell">
      <header class="topbar">
        <div class="top-left">
          <button class="icon-button" type="button" aria-label="切换侧边栏" @click="collapsed = !collapsed"><i :class="collapsed ? 'el-icon-s-unfold' : 'el-icon-s-fold'"></i></button>
          <span class="top-divider"></span><el-breadcrumb separator="/"><el-breadcrumb-item><button class="crumb" @click="open('home')">首页</button></el-breadcrumb-item><el-breadcrumb-item>{{ breadcrumb }}</el-breadcrumb-item></el-breadcrumb>
        </div>
        <div class="top-right">
          <button v-if="can('announcements', 'read')" class="icon-button" type="button" aria-label="公告" title="公告" @click="open('announcements')"><i class="el-icon-bell"></i></button>
          <span class="top-divider"></span>
          <el-dropdown trigger="click" @command="account">
            <button class="account-button"><span class="avatar"><i class="el-icon-user-solid"></i></span><span>{{ user.displayName }}</span><i class="el-icon-arrow-down"></i></button>
            <el-dropdown-menu slot="dropdown"><el-dropdown-item v-if="!isDemoUser" command="password" icon="el-icon-lock">修改密码</el-dropdown-item><el-dropdown-item command="logout" icon="el-icon-switch-button" :divided="!isDemoUser">退出登录</el-dropdown-item></el-dropdown-menu>
          </el-dropdown>
        </div>
      </header>
      <main class="content">
        <dashboard v-if="route === 'home' || route === 'reports'" :key="route" :reports="route === 'reports'" :catalog="catalog" :can="can" @navigate="open" />
        <organization-settings v-else-if="activeOrganization" :section="activeOrganization" :current-user="user.username" />
        <records v-else-if="activeModule" :key="route" :module="activeModule" :catalog="catalog" :can="can" :actor="user.displayName" :selection="selection" @navigate="open" />
      </main>
    </div>
    <el-dialog title="修改密码" :visible.sync="passwordOpen" width="min(420px, 92vw)" append-to-body>
      <el-form label-position="top" @submit.native.prevent="savePassword">
        <el-form-item label="原密码"><el-input v-model="password.currentPassword" autocomplete="current-password" show-password /></el-form-item>
        <el-form-item label="新密码"><el-input v-model="password.newPassword" placeholder="至少 8 个字符" autocomplete="new-password" show-password /></el-form-item>
        <el-button type="primary" native-type="submit" :loading="busy">保存</el-button>
      </el-form>
    </el-dialog>
  </div>
</template>

<script>
import Dashboard from './Dashboard.vue'
import OrganizationSettings from './OrganizationSettings.vue'
import Records from './Records.vue'
import { api } from './api'

const brand = Object.freeze({ name: '百工集', subtitle: '汽修耗材 CRM' })
const organizationMenus = [
  { key: 'employees', label: '员工管理' },
  { key: 'departments', label: '部门管理' },
  { key: 'roles', label: '角色与权限' }
]

export default {
  components: { Dashboard, OrganizationSettings, Records },
  data: () => ({
    brand, booting: true, user: null, catalog: [], route: 'home', selection: null, expanded: [],
    collapsed: window.matchMedia('(max-width: 560px)').matches, busy: false, login: { username: '', password: '' }, loginError: '',
    demoAccounts: [], activeDemo: '', passwordOpen: false, password: { currentPassword: '', newPassword: '' }
  }),
  computed: {
    isDemoUser() { return this.demoAccounts.some(demo => demo.username === this.user?.username) },
    groups() {
      const order = ['工作台', '线索', '客户', '商机', '订单', '审批', '数据', '日志', '体验', '设置']
      return order.map(name => {
        const children = this.catalog.filter(item => item.group === name)
        if (name === '数据' && this.can('reports', 'read')) children.push({ key: 'reports', label: '数据报表', icon: 'el-icon-s-data' })
        if (name === '设置' && this.user?.administrator) children.unshift(...organizationMenus)
        return { name: name === '工作台' ? '待办与日程' : name, icon: name === '设置' ? 'el-icon-setting' : children[0]?.icon, children }
      }).filter(group => group.children.length)
    },
    activeModule() { return this.catalog.find(item => item.key === this.route) },
    activeOrganization() { return this.user?.administrator && organizationMenus.find(item => item.key === this.route) },
    breadcrumb() {
      if (this.route === 'home') return '工作台'
      return this.groups.flatMap(group => group.children).find(item => item.key === this.route)?.label || '工作台'
    }
  },
  async created() {
    window.addEventListener('hashchange', this.readRoute)
    window.addEventListener('cem-session-expired', this.clearSession)
    api('/demo-accounts').then(accounts => { this.demoAccounts = accounts }).catch(() => {})
    try { await this.start(await api('/me')) }
    catch (error) { if (error.status !== 401) this.loginError = error.message }
    finally { this.booting = false }
  },
  beforeDestroy() {
    window.removeEventListener('hashchange', this.readRoute)
    window.removeEventListener('cem-session-expired', this.clearSession)
  },
  methods: {
    can(kind, operation) {
      return Boolean(this.user?.administrator || this.user?.permissions?.includes(kind + ':' + operation))
    },
    async start(user) { this.catalog = await api('/modules'); this.user = user; this.readRoute() },
    async signIn(demoUsername = '') {
      if (this.busy) return
      this.busy = true; this.loginError = ''
      this.activeDemo = demoUsername
      try {
        const user = demoUsername
          ? await api('/demo-accounts/login', 'POST', { username: demoUsername })
          : await api('/login', 'POST', this.login)
        await this.start(user)
        this.login.password = ''
      }
      catch (error) { this.loginError = error.message }
      finally { this.busy = false; this.activeDemo = '' }
    },
    readRoute() {
      if (!this.user) return
      const [key, id] = location.hash.slice(1).split('/')
      const nextGroup = this.groups.find(item => item.children.some(child => child.key === key))
      this.route = nextGroup ? key : 'home'
      this.selection = nextGroup && id && /^\d+$/.test(id) ? Number(id) : null
      if (key && key !== this.route) history.replaceState(null, '', '#home')
      if (nextGroup && !this.expanded.includes(nextGroup.name)) this.expanded.push(nextGroup.name)
      document.title = this.breadcrumb + ' · ' + this.brand.name
    },
    open(key, id) {
      location.hash = key + (id ? '/' + id : '')
      this.readRoute()
      if (window.matchMedia('(max-width: 560px)').matches) this.collapsed = true
    },
    toggle(group) {
      if (group.children.length === 1) { this.open(group.children[0].key); return }
      if (this.collapsed) {
        this.collapsed = false
        if (!this.expanded.includes(group.name)) this.expanded.push(group.name)
        return
      }
      this.expanded = this.expanded.includes(group.name) ? this.expanded.filter(name => name !== group.name) : [...this.expanded, group.name]
    },
    clearSession() { this.user = null; this.passwordOpen = false; document.title = this.brand.name },
    async account(command) {
      if (command === 'password') {
        this.password = { currentPassword: '', newPassword: '' }; this.passwordOpen = true
      } else {
        try { await api('/logout', 'POST'); this.clearSession() }
        catch (error) { this.$message.error(error.message) }
      }
    },
    async savePassword() {
      this.busy = true
      try { await api('/password', 'POST', this.password); this.passwordOpen = false; this.$message.success('密码已更新') }
      catch (error) { this.$message.error(error.message) }
      finally { this.busy = false }
    }
  }
}
</script>
