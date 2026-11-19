<template>
  <div class="dashboard" v-loading="loading">
    <div class="page-heading dashboard-heading">
      <div><h1>{{ reports ? '数据报表' : '工作台' }}</h1><p>{{ reports ? '查看销售进度、合同回款和客户体验数据。' : '跟进业务动态，处理今天的工作。' }}</p></div>
      <div v-if="!reports" class="date-chip"><i class="el-icon-date"></i>{{ today }}</div>
      <el-select v-else v-model="year" aria-label="统计年份" class="year-select"><el-option v-for="value in years" :key="value" :label="value + ' 年'" :value="value" /></el-select>
    </div>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <el-alert v-else-if="!catalog.length" title="暂无可查看的业务，请联系管理员分配角色权限。" type="info" show-icon :closable="false" />
    <div v-if="metrics.length" class="metric-grid">
      <button v-for="metric in metrics" :key="metric.key" class="card metric" @click="$emit('navigate', metric.key)">
        <span :class="['metric-icon', metric.color]"><i :class="metric.icon"></i></span>
        <span class="metric-name">{{ metric.label }}</span>
        <strong>{{ metric.value }}</strong>
        <span class="metric-note">{{ metric.note }}</span>
      </button>
    </div>
    <div v-if="trendTitle || canRead('opportunities')" class="dashboard-grid" :class="{ 'dashboard-grid-single': !trendTitle || !canRead('opportunities') }">
      <section v-if="trendTitle" class="card chart-card">
        <div class="card-heading">
          <div><h2>{{ trendTitle }}趋势</h2><p>{{ year }} 年已审批的{{ trendTitle }}金额</p></div>
          <div class="legend"><span v-if="canRead('contracts')"><i></i>合同金额</span><span v-if="canRead('payments')"><i class="payment-legend"></i>回款金额</span></div>
        </div>
        <svg class="trend" viewBox="0 0 680 226" role="img" :aria-label="'每月' + trendTitle + '金额折线图'">
          <defs><linearGradient id="trend-fill" x1="0" x2="0" y1="0" y2="1"><stop offset="0%" stop-color="#3964ff" stop-opacity=".2"/><stop offset="100%" stop-color="#3964ff" stop-opacity="0"/></linearGradient></defs>
          <g v-for="(tick, i) in scale" :key="i"><line x1="65" x2="655" :y1="22 + i * 41" :y2="22 + i * 41" stroke="#edf0f6" stroke-dasharray="4 4"/><text x="53" :y="26 + i * 41" text-anchor="end">{{ tick ? (tick / 10000).toLocaleString() + '万' : '0' }}</text></g>
          <path v-if="canRead('contracts')" :d="area" fill="url(#trend-fill)"/>
          <path v-if="canRead('contracts')" :d="lines.contracts" fill="none" stroke="#3e64ff" stroke-width="3" stroke-linejoin="round"/>
          <path v-if="canRead('payments')" :d="lines.payments" fill="none" stroke="#2fbc9e" stroke-width="3" stroke-linejoin="round"/>
          <g v-for="(value, i) in trend.contracts" :key="'c' + i"><circle v-if="value" :cx="x(i)" :cy="y(value)" r="4" stroke="#3e64ff" stroke-width="2" fill="white"><title>{{ i + 1 }} 月合同 {{ money(value) }}</title></circle></g>
          <g v-for="(value, i) in trend.payments" :key="'p' + i"><circle v-if="value" :cx="x(i)" :cy="y(value)" r="4" stroke="#2fbc9e" stroke-width="2" fill="white"><title>{{ i + 1 }} 月回款 {{ money(value) }}</title></circle><text :x="x(i)" y="210" text-anchor="middle">{{ i + 1 }}月</text></g>
        </svg>
      </section>
      <section v-if="canRead('opportunities')" class="card funnel-card">
        <div class="card-heading"><div><h2>商机阶段</h2><p>当前商机的阶段分布</p></div><button class="text-button" @click="$emit('navigate', 'opportunities')">查看商机<i class="el-icon-arrow-right"></i></button></div>
        <div v-for="(stage, index) in stages" :key="stage.name" class="stage-row">
          <span>{{ stage.name }}</span><div class="stage-track"><div :style="{ width: stage.width + '%', background: stageColors[index] }"></div></div><b>{{ stage.total }}</b>
        </div>
      </section>
    </div>
    <div v-if="catalog.length" class="dashboard-grid lower-grid"
         :class="{ 'dashboard-grid-single': (reports && !trendTitle) || !(canRead('feedback') || (!reports && canRead('tasks'))) }">
      <section class="card recent-card" v-if="!reports">
        <div class="card-heading"><div><h2>最新业务动态</h2><p>最近更新的业务记录</p></div></div>
        <el-table :data="data.recent || []" size="small" empty-text="暂无业务动态">
          <el-table-column label="业务记录" min-width="190" show-overflow-tooltip><template slot-scope="{row}"><button class="table-link" @click="$emit('navigate', row.kind, row.id)">{{ row.name }}</button></template></el-table-column>
          <el-table-column label="类型" width="108"><template slot-scope="{row}">{{ label(row.kind) }}</template></el-table-column>
          <el-table-column prop="owner" label="负责人" width="105" />
          <el-table-column label="更新日期" width="115"><template slot-scope="{row}">{{ row.updatedAt.slice(0, 10) }}</template></el-table-column>
        </el-table>
      </section>
      <section class="card recent-card" v-else-if="trendTitle">
        <div class="card-heading"><div><h2>月度{{ trendTitle }}明细</h2><p>按业务日期和审批结果统计</p></div></div>
        <el-table :data="monthly" size="small">
          <el-table-column prop="month" label="月份" />
          <el-table-column v-if="canRead('contracts')" label="合同金额"><template slot-scope="{row}">{{ money(row.contracts) }}</template></el-table-column>
          <el-table-column v-if="canRead('payments')" label="回款金额"><template slot-scope="{row}">{{ money(row.payments) }}</template></el-table-column>
        </el-table>
      </section>
      <div v-if="canRead('feedback') || (!reports && canRead('tasks'))" class="dashboard-side">
        <section v-if="!reports && canRead('tasks')" class="card todo-card">
          <div class="card-heading"><h2>待办事项 <span class="count-pill">{{ total('tasks', '待处理') }}</span></h2><button class="text-button" @click="$emit('navigate', 'tasks')">全部待办<i class="el-icon-arrow-right"></i></button></div>
          <p v-if="!data.tasks?.length" class="card-empty">暂无待办事项</p>
          <div v-for="task in data.tasks" :key="task.id" class="todo-row">
            <button v-if="can('tasks', 'operate')" class="todo-check" :aria-label="'完成 ' + task.name" @click="complete(task)"><i class="el-icon-check"></i></button>
            <span v-else aria-hidden="true"></span>
            <button class="todo-name" @click="$emit('navigate', 'tasks', task.id)">{{ task.name }}</button><span>{{ task.dueDate?.slice(5) }}</span>
          </div>
        </section>
        <section v-if="canRead('feedback')" class="card experience-card">
          <div class="card-heading"><h2>客户体验</h2><button class="text-button" @click="$emit('navigate', 'feedback')">客户反馈<i class="el-icon-arrow-right"></i></button></div>
          <div class="experience-stats">
            <div><strong>{{ data.experience?.score == null ? '—' : Number(data.experience.score).toFixed(1) }}</strong><span>平均满意度 / 5</span></div>
            <div><strong>{{ total('feedback') }}</strong><span>反馈总数</span></div>
            <div><strong>{{ total('feedback', '已解决') }}</strong><span>已解决</span></div>
          </div>
        </section>
      </div>
    </div>
  </div>
</template>

<script>
import { api, dateString, money } from './api'
const now = new Date()

export default {
  props: { catalog: Array, reports: Boolean, can: Function },
  data: () => ({
    data: {}, error: '', loading: false, year: now.getFullYear(),
    today: dateString(now) + ' ' + now.toLocaleDateString('zh-CN', { weekday: 'long' }),
    years: Array.from({ length: 6 }, (_, i) => now.getFullYear() - i),
    stageColors: ['#a2b3ff', '#8198ff', '#5d7df9', '#3e64ec', '#2cb89d', '#c1c9d9']
  }),
  computed: {
    metrics() {
      return [
        { key: 'leads', label: '销售线索', value: this.total('leads'), note: this.total('leads', '待跟进') + ' 条待跟进', icon: 'el-icon-guide', color: 'blue' },
        { key: 'customers', label: '客户总数', value: this.total('customers'), note: this.total('customers', '已成交') + ' 家已成交', icon: 'el-icon-user-solid', color: 'violet' },
        { key: 'opportunities', label: '进行中商机', value: this.total('opportunities') - this.total('opportunities', '赢单') - this.total('opportunities', '输单'), note: this.total('opportunities', '赢单') + ' 个商机已赢单', icon: 'el-icon-s-opportunity', color: 'amber' },
        { key: 'payments', label: '累计回款', value: money(this.total('payments', '已通过', 'amount')), note: this.total('payments', '已通过') + ' 笔已审批回款', icon: 'el-icon-wallet', color: 'green' }
      ].filter(metric => this.canRead(metric.key))
    },
    trendTitle() {
      return [this.canRead('contracts') && '合同', this.canRead('payments') && '回款'].filter(Boolean).join('与')
    },
    stages() {
      const names = this.catalog.find(module => module.key === 'opportunities')?.statuses || []
      const max = Math.max(1, ...names.map(name => this.total('opportunities', name)))
      return names.map(name => ({ name, total: this.total('opportunities', name), width: this.total('opportunities', name) / max * 100 }))
    },
    trend() {
      const result = { contracts: Array(12).fill(0), payments: Array(12).fill(0) }
      for (const row of this.data.trend || []) result[row.kind][Number(row.month.slice(-2)) - 1] = Number(row.amount)
      return result
    },
    max() {
      const value = Math.max(40000, ...this.trend.contracts, ...this.trend.payments)
      const unit = 10 ** Math.floor(Math.log10(value)) / 2
      return Math.ceil(value / unit) * unit
    },
    scale() { return [1, .75, .5, .25, 0].map(factor => this.max * factor) },
    lines() {
      return Object.fromEntries(Object.entries(this.trend).map(([kind, values]) =>
        [kind, values.map((value, i) => (i ? 'L' : 'M') + this.x(i) + ' ' + this.y(value)).join(' ')]))
    },
    area() { return this.lines.contracts + ' L655 186 L65 186 Z' },
    monthly() { return this.trend.contracts.map((contracts, i) => ({ month: (i + 1) + ' 月', contracts, payments: this.trend.payments[i] })) }
  },
  watch: { year: 'load' },
  created() { this.load() },
  methods: {
    money,
    canRead(kind) { return this.can(kind, 'read') },
    label(kind) { return this.catalog.find(item => item.key === kind)?.label || kind },
    x(index) { return 65 + index * 590 / 11 },
    y(value) { return 186 - value / this.max * 164 },
    total(kind, status, column = 'total') {
      return (this.data.totals || []).filter(row => row.kind === kind && (!status || status === row.status))
        .reduce((sum, row) => sum + Number(row[column]), 0)
    },
    async load() {
      const year = this.year
      this.loading = true; this.error = ''
      try { const data = await api((this.reports ? '/reports' : '/dashboard') + '?year=' + year); if (this.year === year) this.data = data }
      catch (error) { if (this.year === year) this.error = error.message }
      finally { if (this.year === year) this.loading = false }
    },
    async complete(task) {
      try { await api('/records/tasks/' + task.id + '/actions', 'POST', { action: 'complete' }); this.$message.success('待办已完成'); this.load() }
      catch (error) { this.$message.error(error.message) }
    }
  }
}
</script>
