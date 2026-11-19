<template>
  <div class="records">
    <div class="page-heading">
      <div><div class="page-section">{{ module.group }}</div><h1>{{ module.label }}</h1></div>
      <div class="heading-actions">
        <el-button v-if="module.key === 'calendar'" :icon="calendar ? 'el-icon-tickets' : 'el-icon-date'" :aria-label="calendar ? '列表' : '日历'" @click="calendar = !calendar">{{ calendar ? '列表' : '日历' }}</el-button>
        <el-button v-if="!calendar" icon="el-icon-download" aria-label="导出" :disabled="!total" :loading="exporting" @click="exportRows">导出</el-button>
        <el-button v-if="writable" type="primary" icon="el-icon-plus" :aria-label="'新建' + entityName" @click="edit()">新建{{ entityName }}</el-button>
      </div>
    </div>
    <section v-if="calendar" class="card calendar-panel" v-loading="calendarLoading">
      <el-calendar v-model="monthDate">
        <template #dateCell="{ date, data }">
          <div class="calendar-cell" @dblclick="writable && edit(null, data.day)">
            <span>{{ date.getDate() }}</span>
            <button v-for="event in events[data.day] || []" :key="event.id" class="calendar-event" @click.stop="show(event.id)">{{ event.details.time || '' }} {{ event.name }}</button>
          </div>
        </template>
      </el-calendar>
    </section>
    <section v-else class="card records-panel">
      <div v-if="module.statuses.length" class="status-tabs">
        <button v-for="status in ['', ...module.statuses]" :key="status" :class="{ active: filter.status === status }" @click="pickStatus(status)">{{ status || '全部' }}</button>
      </div>
      <el-form class="filters" :inline="true" @submit.native.prevent="search">
        <el-form-item><el-input v-model="filter.keyword" clearable prefix-icon="el-icon-search" :placeholder="'搜索' + module.name + '、负责人…'" maxlength="100" /></el-form-item>
        <el-form-item v-if="!pool"><el-input v-model.trim="filter.owner" clearable placeholder="负责人" maxlength="80" /></el-form-item>
        <el-form-item><el-button native-type="submit" type="primary">查询</el-button><el-button @click="reset">重置</el-button></el-form-item>
      </el-form>
      <el-alert v-if="listError" :title="listError" type="error" :closable="false" show-icon />
      <el-table :data="rows" v-loading="loading" row-key="id" class="record-table" empty-text="暂无记录">
        <el-table-column :label="module.name" min-width="200" show-overflow-tooltip>
          <template slot-scope="{row}"><button class="table-link" @click="show(row.id)">{{ row.name }}</button></template>
        </el-table-column>
        <el-table-column v-if="module.related || module.key === 'approvals'" prop="relatedName" :label="relatedLabel" min-width="160" show-overflow-tooltip />
        <el-table-column v-if="module.statuses.length" label="状态" width="115"><template slot-scope="{row}"><el-tag size="small" :type="statusType(row.status)" effect="light">{{ row.status }}</el-tag></template></el-table-column>
        <el-table-column v-if="module.amount" :label="module.amount" min-width="120"><template slot-scope="{row}">{{ money(row.amount) }}</template></el-table-column>
        <el-table-column v-if="!pool" prop="owner" label="负责人" min-width="110" show-overflow-tooltip />
        <el-table-column v-if="module.date" prop="dueDate" :label="module.date" width="116" />
        <template v-for="field in previewFields">
          <el-table-column :key="field.key" :label="field.label" min-width="115" show-overflow-tooltip><template slot-scope="{row}">{{ row.details[field.key] || '—' }}</template></el-table-column>
        </template>
        <el-table-column v-if="!module.date" label="更新时间" width="116"><template slot-scope="{row}">{{ row.updatedAt.slice(0, 10) }}</template></el-table-column>
        <el-table-column label="操作" width="240">
          <template slot-scope="{row}">
            <div class="row-actions">
              <el-button type="text" @click="show(row.id)">详情</el-button>
              <el-button v-if="editable(row)" type="text" @click="edit(row)">编辑</el-button>
              <el-button v-for="action in actions(row)" :key="action" type="text"
                         :class="{ 'row-action-danger': action === 'delete' }" @click="run(action, row)">
                {{ verbs[action] }}
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination"><span>共 {{ total }} 条记录</span><el-pagination background :current-page="page" :page-size="size" :page-sizes="[10, 20, 50]" :total="total" layout="sizes, prev, pager, next" @current-change="changePage" @size-change="changeSize" /></div>
    </section>

    <el-dialog :title="(form.id ? '编辑' : '新建') + entityName" :visible.sync="formOpen" width="min(620px, 94vw)" append-to-body :close-on-click-modal="false">
      <el-form label-position="top" class="edit-form" @submit.native.prevent="save">
        <el-form-item :label="module.name" required><el-input v-model.trim="form.name" maxlength="160" /></el-form-item>
        <el-form-item v-if="!pool" label="负责人"><el-input v-model.trim="form.owner" maxlength="80" /></el-form-item>
        <el-form-item v-if="module.related" :label="relatedLabel" required>
          <el-select v-model="form.relatedId" filterable remote :remote-method="loadOptions" :loading="optionsLoading" placeholder="搜索并选择关联记录">
            <el-option v-for="item in options" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="module.statuses.length && !approvable" label="状态" required>
          <el-select v-model="form.status" :disabled="form.status === '已转化'"><el-option v-for="status in module.statuses.filter(value => value !== '已转化' || form.status === value)" :key="status" :label="status" :value="status" /></el-select>
        </el-form-item>
        <el-form-item v-if="module.date" :label="module.date" required><el-date-picker v-model="form.dueDate" type="date" value-format="yyyy-MM-dd" placeholder="选择日期" /></el-form-item>
        <el-form-item v-if="module.amount" :label="module.amount" required><el-input v-model="form.amount" inputmode="decimal" placeholder="0.00"><template slot="prepend">¥</template></el-input></el-form-item>
        <el-form-item v-for="field in module.fields" :key="field.key" :label="field.label" :class="{ 'full-width': field.type === 'textarea' }">
          <el-select v-if="field.type === 'select'" v-model="form.details[field.key]" clearable placeholder="请选择"><el-option v-for="value in field.options" :key="value" :label="value" :value="value" /></el-select>
          <el-date-picker v-else-if="field.type === 'date'" v-model="form.details[field.key]" type="date" value-format="yyyy-MM-dd" placeholder="选择日期" />
          <el-time-picker v-else-if="field.type === 'time'" v-model="form.details[field.key]" value-format="HH:mm" format="HH:mm" placeholder="选择时间" />
          <el-rate v-else-if="field.type === 'rating'" v-model="form.details[field.key]" show-score />
          <el-input v-else v-model.trim="form.details[field.key]" :type="field.type" :maxlength="field.type === 'textarea' ? 4000 : 200" :rows="3" />
        </el-form-item>
      </el-form>
      <span slot="footer"><el-button @click="formOpen = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></span>
    </el-dialog>

    <el-drawer title="记录详情" :visible.sync="drawer" size="min(510px, 95vw)" append-to-body>
      <div v-loading="detailLoading" class="detail" v-if="drawer">
        <template v-if="detail">
          <h2>{{ detail.name }}</h2>
          <el-tag v-if="detail.status" :type="statusType(detail.status)">{{ detail.status }}</el-tag>
          <dl class="detail-fields">
            <div v-if="!pool"><dt>负责人</dt><dd>{{ detail.owner }}</dd></div>
            <div v-if="detail.relatedName"><dt>{{ relatedLabel }}</dt><dd><button v-if="module.related && can(module.related, 'read')" class="table-link" @click="$emit('navigate', module.related, detail.relatedId)">{{ detail.relatedName }}</button><span v-else>{{ detail.relatedName }}</span></dd></div>
            <div v-if="module.date"><dt>{{ module.date }}</dt><dd>{{ detail.dueDate }}</dd></div>
            <div v-if="module.amount"><dt>{{ module.amount }}</dt><dd>{{ money(detail.amount) }}</dd></div>
            <div v-for="field in module.fields" :key="field.key"><dt>{{ field.label }}</dt><dd>{{ detail.details[field.key] || '—' }}</dd></div>
            <div v-if="detail.details.reviewer"><dt>审批人</dt><dd>{{ detail.details.reviewer }}</dd></div>
            <div><dt>创建时间</dt><dd>{{ detail.createdAt.replace('T', ' ').slice(0, 19) }}</dd></div>
          </dl>
          <div class="detail-actions">
            <el-button v-if="editable(detail)" type="primary" @click="edit(detail)">编辑</el-button>
            <el-button v-for="action in actions(detail)" :key="action" :type="action === 'delete' ? 'danger' : 'default'" plain @click="run(action, detail)">{{ verbs[action] }}</el-button>
          </div>
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<script>
import { api, dateString, money, statusType } from './api'

const approvable = ['contracts', 'payments', 'invoices', 'expenses']
const verbs = { complete: '标记完成', convert: '转为客户', claim: '领取', release: '退回公海', submit: '提交审批',
  approve: '同意', reject: '驳回', delete: '删除' }
const initialFilter = () => ({ status: '', keyword: '', owner: '' })

export default {
  props: { module: Object, catalog: Array, actor: String, can: Function, selection: Number },
  data: () => ({
    rows: [], total: 0, filter: initialFilter(), applied: initialFilter(), page: 1, size: 10,
    loading: false, listError: '', queryId: 0, detailId: 0, detailLoading: false, exporting: false,
    formOpen: false, form: { details: {} }, saving: false, options: [], optionsLoading: false, optionId: 0,
    drawer: false, detail: null, actionBusy: false, verbs, calendar: false, calendarLoading: false,
    monthDate: new Date(), events: {}
  }),
  computed: {
    endpoint() { return '/records/' + this.module.key },
    pool() { return this.module.key.endsWith('-pool') },
    approvable() { return approvable.includes(this.module.key) },
    writable() { return this.module.key !== 'approvals' && this.can(this.module.key, 'write') },
    entityName() { return this.module.name.replace(/名称|姓名|标题|主题|抬头$/, '') },
    relatedLabel() { return this.catalog.find(item => item.key === this.module.related)?.name.replace(/名称$/, '') || '关联单据' },
    previewFields() {
      return this.module.fields.filter(field => field.type !== 'textarea').slice(0, this.module.related || this.module.amount ? 1 : 2)
    }
  },
  watch: {
    selection(id) { if (id) this.show(id); else this.drawer = false },
    calendar(value) { if (value) this.loadCalendar() }
  },
  created() { this.load(); if (this.selection) this.show(this.selection) },
  methods: {
    money, statusType,
    query(page = this.page, size = this.size) {
      return this.endpoint + '?' + new URLSearchParams({ ...this.applied, page, size })
    },
    async load() {
      const request = ++this.queryId
      this.loading = true; this.listError = ''
      try {
        const data = await api(this.query())
        if (request !== this.queryId) return
        this.rows = data.rows; this.total = data.total
        if (!data.rows.length && this.page > 1) { this.page = Math.max(1, Math.ceil(data.total / this.size)); return this.load() }
      } catch (error) { if (request === this.queryId) this.listError = error.message }
      finally { if (request === this.queryId) this.loading = false }
    },
    search() { this.applied = { ...this.filter }; this.page = 1; this.load() },
    pickStatus(status) { this.filter.status = status; this.search() },
    reset() { this.filter = initialFilter(); this.search() },
    changePage(page) { this.page = page; this.load() },
    changeSize(size) { this.size = size; this.page = 1; this.load() },
    async show(id) {
      const request = ++this.detailId
      this.detail = null; this.drawer = true; this.detailLoading = true
      try { const row = await api(this.endpoint + '/' + id); if (request === this.detailId) this.detail = row }
      catch (error) { if (request === this.detailId) { this.$message.error(error.message); this.drawer = false } }
      finally { if (request === this.detailId) this.detailLoading = false }
    },
    edit(row, day) {
      const defaults = Object.fromEntries(this.module.fields.map(field => [field.key, field.type === 'rating' ? 0 : '']))
      this.form = row ? { ...row, details: { ...defaults, ...row.details } } : {
        name: '', owner: this.actor, status: this.module.statuses[0] || '', relatedId: null,
        dueDate: this.module.date ? day || dateString() : null, amount: null, details: defaults
      }
      this.options = row?.relatedId ? [{ id: row.relatedId, name: row.relatedName }] : []
      this.formOpen = true
      if (this.module.related) this.loadOptions('')
    },
    async loadOptions(keyword) {
      const request = ++this.optionId
      this.optionsLoading = true
      try {
        const { rows } = await api('/records/' + this.module.related + '?' + new URLSearchParams({ keyword, size: 50 }))
        if (request !== this.optionId) return
        const current = this.options.find(option => option.id === this.form.relatedId)
        this.options = current && !rows.some(row => row.id === current.id) ? [current, ...rows] : rows
      } catch (error) { if (request === this.optionId) this.$message.error(error.message) }
      finally { if (request === this.optionId) this.optionsLoading = false }
    },
    async save() {
      if (this.saving) return
      const { id, name, owner, status, relatedId, dueDate, amount, details } = this.form
      if (!name) return this.$message.warning('请填写' + this.module.name)
      if (this.module.related && !relatedId) return this.$message.warning('请选择' + this.relatedLabel)
      if (this.module.amount && !/^\d+(\.\d{1,2})?$/.test(String(amount ?? '').trim())) return this.$message.warning('请填写合法金额，最多两位小数')
      const fields = { ...details }
      if (fields.score === 0) delete fields.score
      this.saving = true
      try {
        const row = await api(this.endpoint + (id ? '/' + id : ''), id ? 'PUT' : 'POST', {
          name, owner, status, relatedId, dueDate, amount: this.module.amount ? String(amount).trim() : null, details: fields
        })
        this.$message.success('保存成功'); this.formOpen = false
        if (this.drawer && this.detail?.id === row.id) this.detail = row
        if (!id) this.page = 1
        this.refresh()
      } catch (error) { this.$message.error(error.message) }
      finally { this.saving = false }
    },
    editable(row) { return this.writable && this.mutable(row) },
    mutable(row) { return !(this.approvable && ['审核中', '已通过'].includes(row.status)) },
    actions(row) {
      const operate = this.can(this.module.key, 'operate')
      if (this.module.key === 'approvals') return operate && row.status === '待审批' ? ['approve', 'reject'] : []
      const result = []
      if (operate) {
        if (this.pool) result.push('claim')
        if (this.module.key === 'leads' && row.status !== '已转化') result.push('convert', 'release')
        if (this.module.key === 'customers') result.push('release')
        if (this.approvable && this.mutable(row)) result.push('submit')
        if (['tasks', 'calendar'].includes(this.module.key) && row.status !== '已完成') result.push('complete')
      }
      if (this.can(this.module.key, 'delete') && this.mutable(row)) result.push('delete')
      return result
    },
    async run(action, row) {
      if (this.actionBusy) return
      this.actionBusy = true
      try {
        let comment
        if (action === 'reject' || action === 'approve') {
          const result = await this.$prompt(action === 'reject' ? '请输入驳回原因' : '可填写审批意见', verbs[action], {
            inputType: 'textarea', inputValidator: value => action !== 'reject' || Boolean(value?.trim()) || '请输入驳回原因'
          })
          comment = result.value
        } else if (action !== 'complete') {
          await this.$confirm('确认' + verbs[action] + '「' + row.name + '」？', '确认操作', { type: 'warning' })
        }
        const result = action === 'delete' ? await api(this.endpoint + '/' + row.id, 'DELETE') :
          await api(this.endpoint + '/' + row.id + '/actions', 'POST', { action, comment })
        if (this.detail?.id === row.id) {
          if (result?.kind === this.module.key) this.detail = result
          else this.drawer = false
        }
        this.$message.success('操作成功'); this.refresh()
      } catch (error) { if (error?.message) this.$message.error(error.message) }
      finally { this.actionBusy = false }
    },
    refresh() { this.load(); if (this.calendar) this.loadCalendar() },
    async fetchAll(query) {
      const first = await api(query(1, 500))
      const result = first.rows
      for (let page = 2; page <= Math.ceil(first.total / 500); page++) result.push(...(await api(query(page, 500))).rows)
      return result
    },
    async loadCalendar() {
      this.calendarLoading = true
      try {
        const rows = await this.fetchAll((page, size) => this.endpoint + '?' + new URLSearchParams({ page, size }))
        this.events = rows.reduce((events, row) => {
          if (row.dueDate) (events[row.dueDate] ||= []).push(row)
          return events
        }, {})
      } catch (error) { this.$message.error(error.message) }
      finally { this.calendarLoading = false }
    },
    async exportRows() {
      this.exporting = true
      try {
        const rows = await this.fetchAll(this.query)
        const labels = [this.module.name, '状态', '负责人', this.relatedLabel, this.module.amount || '金额', this.module.date || '业务日期', ...this.module.fields.map(field => field.label)]
        const lines = [labels, ...rows.map(row => [row.name, row.status, row.owner, row.relatedName, row.amount, row.dueDate, ...this.module.fields.map(field => row.details[field.key])])]
        const cell = value => {
          const text = String(value ?? '')
          return '"' + (/^[\s]*[=+@-]/.test(text) || /^[\t\r\n]/.test(text) ? "'" : '') + text.replace(/"/g, '""') + '"'
        }
        const url = URL.createObjectURL(new Blob(['\uFEFF', lines.map(line => line.map(cell).join(',')).join('\r\n')], { type: 'text/csv;charset=utf-8' }))
        const link = document.createElement('a'); link.href = url; link.download = this.module.label + '-' + dateString() + '.csv'; link.click()
        setTimeout(() => URL.revokeObjectURL(url), 1000)
      } catch (error) { this.$message.error(error.message) }
      finally { this.exporting = false }
    }
  }
}
</script>
