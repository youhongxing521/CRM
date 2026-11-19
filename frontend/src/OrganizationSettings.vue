<template>
  <div class="organization">
    <div class="page-heading">
      <div><div class="page-section">设置</div><h1>{{ section.label }}</h1><p>{{ sectionDescription }}</p></div>
      <el-button type="primary" icon="el-icon-plus" :aria-label="createLabel" @click="create">{{ createLabel }}</el-button>
    </div>
    <el-alert v-if="loadError" :title="loadError" type="error" show-icon :closable="false" />

    <section class="card organization-panel" v-loading="loading">
      <template v-if="section.key === 'employees'">
        <div class="organization-filters">
          <el-input v-model.trim="keyword" clearable prefix-icon="el-icon-search" placeholder="搜索员工姓名或账号" label="搜索员工姓名或账号" />
          <el-select v-model="departmentFilter" clearable placeholder="全部部门" aria-label="按部门筛选">
            <el-option v-for="department in snapshot.departments" :key="department.id" :label="department.name" :value="department.id" />
          </el-select>
          <el-select v-model="statusFilter" clearable placeholder="全部状态" aria-label="按状态筛选">
            <el-option label="启用" value="enabled" /><el-option label="停用" value="disabled" />
          </el-select>
        </div>
        <el-table key="employees" :data="employees" row-key="username" empty-text="暂无员工">
          <el-table-column label="员工姓名" prop="displayName" min-width="130" show-overflow-tooltip />
          <el-table-column label="登录账号" prop="username" min-width="150" show-overflow-tooltip />
          <el-table-column label="所属部门" min-width="135">
            <template slot-scope="{row}">{{ departmentsById[row.departmentId]?.name || '未分配' }}</template>
          </el-table-column>
          <el-table-column label="角色" min-width="185">
            <template slot-scope="{row}">
              <div v-if="row.roleIds.length" class="organization-tags">
                <el-tag v-for="id in row.roleIds" :key="id" size="mini" type="info">{{ rolesById[id]?.name }}</el-tag>
              </div>
              <span v-else>未分配</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="95">
            <template slot-scope="{row}"><el-tag size="mini" :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag></template>
          </el-table-column>
          <el-table-column label="操作" width="170">
            <template slot-scope="{row}">
              <div v-if="!row.protectedAccount && row.username !== currentUser" class="row-actions">
                <el-button type="text" @click="editEmployee(row)">编辑</el-button>
                <el-button v-if="!row.demo" type="text" @click="resetPassword(row)">重置密码</el-button>
              </div>
              <span v-else class="organization-muted">{{ row.username === currentUser ? '当前账号' : '内置管理员' }}</span>
            </template>
          </el-table-column>
        </el-table>
      </template>

      <el-table v-else-if="section.key === 'departments'" key="departments" :data="snapshot.departments" row-key="id" empty-text="暂无部门">
        <el-table-column label="部门名称" prop="name" min-width="160" show-overflow-tooltip />
        <el-table-column label="部门说明" prop="description" min-width="270" show-overflow-tooltip />
        <el-table-column label="员工人数" prop="employeeCount" width="110" />
        <el-table-column label="操作" width="155">
          <template slot-scope="{row}">
            <div class="row-actions">
              <el-button type="text" @click="editDepartment(row)">编辑</el-button>
              <el-button type="text" class="row-action-danger" @click="remove('departments', row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <el-table v-else key="roles" :data="snapshot.roles" row-key="id" empty-text="暂无角色">
        <el-table-column label="角色名称" min-width="155">
          <template slot-scope="{row}">{{ row.name }} <el-tag v-if="row.builtIn" size="mini" type="info">内置</el-tag></template>
        </el-table-column>
        <el-table-column label="角色说明" prop="description" min-width="265" show-overflow-tooltip />
        <el-table-column label="权限" width="105">
          <template slot-scope="{row}">{{ row.builtIn ? '全部' : row.permissions.length + ' 项' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="165">
          <template slot-scope="{row}">
            <div class="row-actions">
              <el-button type="text" @click="editRole(row)">{{ row.builtIn ? '查看权限' : '配置权限' }}</el-button>
              <el-button v-if="!row.builtIn" type="text" class="row-action-danger" @click="remove('roles', row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog :title="dialogTitle" :visible.sync="dialog" :width="mode === 'role' ? 'min(780px, 94vw)' : 'min(530px, 94vw)'" append-to-body :close-on-click-modal="false">
      <el-form v-if="dialog" label-position="top" :disabled="readOnly" @submit.native.prevent="save">
        <template v-if="mode === 'employee'">
          <div class="edit-form">
            <el-form-item label="登录账号" required><el-input v-model.trim="form.username" :disabled="Boolean(editId)" maxlength="64" autocomplete="off" label="登录账号" /></el-form-item>
            <el-form-item label="员工姓名" required><el-input v-model.trim="form.displayName" maxlength="80" label="员工姓名" /></el-form-item>
            <el-form-item v-if="!editId" label="初始密码" required>
              <el-input v-model="form.password" show-password autocomplete="new-password" placeholder="至少 8 个字符" label="初始密码" />
            </el-form-item>
            <el-form-item label="所属部门">
              <el-select v-model="form.departmentId" clearable filterable placeholder="请选择部门" aria-label="所属部门">
                <el-option v-for="department in snapshot.departments" :key="department.id" :label="department.name" :value="department.id" />
              </el-select>
            </el-form-item>
            <el-form-item class="full-width" label="分配角色">
              <el-select v-model="form.roleIds" multiple filterable placeholder="可选择多个角色" aria-label="分配角色">
                <el-option v-for="role in selectableRoles" :key="role.id" :label="role.name" :value="role.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="账号状态"><el-switch v-model="form.enabled" active-text="启用" inactive-text="停用" aria-label="账号状态" /></el-form-item>
          </div>
        </template>
        <template v-else-if="mode === 'password'">
          <el-form-item label="新密码" required><el-input v-model="form.password" show-password autocomplete="new-password" placeholder="至少 8 个字符" label="新密码" /></el-form-item>
          <p class="organization-hint">保存后，该员工需要使用新密码重新登录。</p>
        </template>
        <template v-else>
          <el-form-item :label="mode === 'role' ? '角色名称' : '部门名称'" required>
            <el-input v-model.trim="form.name" maxlength="80" :label="mode === 'role' ? '角色名称' : '部门名称'" />
          </el-form-item>
          <el-form-item label="说明"><el-input v-model.trim="form.description" type="textarea" :rows="2" maxlength="400" label="说明" /></el-form-item>
          <template v-if="mode === 'role'">
            <div class="permission-heading"><h3>业务权限</h3><span>{{ readOnly ? '拥有全部业务及系统管理权限' : '已选 ' + form.permissions.length + ' 项' }}</span></div>
            <p v-if="!readOnly" class="organization-hint">勾选操作时会同时授予模块查看权限。编辑关联单据也需要查看关联模块。</p>
            <div class="permission-list">
              <div v-for="module in snapshot.permissionModules" :key="module.key" class="permission-row">
                <span class="permission-module"><small>{{ module.group }}</small>{{ module.label }}</span>
                <div class="permission-options">
                  <el-checkbox v-for="action in module.actions" :key="action.code" :value="form.permissions.includes(action.code)"
                               :disabled="readOnly || (action.code.endsWith(':read') && needsRead(module.key))"
                               :aria-label="module.label + ' ' + action.label"
                               @change="checked => changePermission(module, action.code, checked)">{{ action.label }}</el-checkbox>
                </div>
              </div>
            </div>
          </template>
        </template>
      </el-form>
      <span slot="footer">
        <el-button @click="dialog = false">{{ readOnly ? '关闭' : '取消' }}</el-button>
        <el-button v-if="!readOnly" type="primary" :loading="saving" @click="save">保存</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import { api } from './api'

const description = {
  employees: '创建登录账号，设置所属部门、角色和账号状态。',
  departments: '管理公司部门及员工归属。',
  roles: '按业务模块为角色配置查看和操作权限。'
}
const entities = { employees: '员工', departments: '部门', roles: '角色' }

export default {
  props: { section: Object, currentUser: String },
  data: () => ({
    snapshot: { employees: [], departments: [], roles: [], permissionModules: [] },
    keyword: '', departmentFilter: '', statusFilter: '', loading: false, loadError: '',
    dialog: false, mode: '', editId: null, readOnly: false, saving: false, form: {}
  }),
  computed: {
    sectionDescription() { return description[this.section.key] },
    createLabel() { return '新建' + entities[this.section.key] },
    departmentsById() { return Object.fromEntries(this.snapshot.departments.map(item => [item.id, item])) },
    rolesById() { return Object.fromEntries(this.snapshot.roles.map(item => [item.id, item])) },
    employees() {
      const keyword = this.keyword.toLowerCase()
      return this.snapshot.employees.filter(employee =>
        (!keyword || employee.username.toLowerCase().includes(keyword) || employee.displayName.toLowerCase().includes(keyword)) &&
        (!this.departmentFilter || employee.departmentId === this.departmentFilter) &&
        (!this.statusFilter || employee.enabled === (this.statusFilter === 'enabled')))
    },
    selectableRoles() { return this.snapshot.roles.filter(role => !this.form.demo || !role.builtIn) },
    dialogTitle() {
      if (this.mode === 'password') return '重置密码 · ' + this.form.displayName
      if (this.readOnly) return '查看角色权限'
      return (this.editId ? '编辑' : '新建') + entities[this.section.key]
    }
  },
  created() { this.load() },
  methods: {
    async load() {
      this.loading = true; this.loadError = ''
      try { this.snapshot = await api('/organization') }
      catch (error) { this.loadError = error.message }
      finally { this.loading = false }
    },
    create() {
      if (this.section.key === 'employees') this.editEmployee()
      if (this.section.key === 'departments') this.editDepartment()
      if (this.section.key === 'roles') this.editRole()
    },
    open(mode, id, form, readOnly = false) {
      this.mode = mode; this.editId = id; this.form = form; this.readOnly = readOnly; this.dialog = true
    },
    editEmployee(row) {
      this.open('employee', row?.username, row ? { ...row, roleIds: [...row.roleIds] } :
        { username: '', displayName: '', password: '', departmentId: null, enabled: true, roleIds: [], demo: false })
    },
    editDepartment(row) {
      this.open('department', row?.id, { name: row?.name || '', description: row?.description || '' })
    },
    editRole(row) {
      this.open('role', row?.id, { name: row?.name || '', description: row?.description || '',
        permissions: [...(row?.permissions || [])] }, Boolean(row?.builtIn))
    },
    resetPassword(row) {
      this.open('password', row.username, { displayName: row.displayName, password: '' })
    },
    needsRead(key) {
      return this.snapshot.permissionModules.some(module => module.related === key &&
        this.form.permissions.includes(module.key + ':write'))
    },
    changePermission(module, code, checked) {
      const permissions = new Set(this.form.permissions)
      if (checked) {
        permissions.add(code)
        permissions.add(module.key + ':read')
        if (code.endsWith(':write') && module.related) permissions.add(module.related + ':read')
      } else if (code.endsWith(':read')) {
        for (const action of module.actions) permissions.delete(action.code)
      } else {
        permissions.delete(code)
      }
      this.form.permissions = [...permissions]
    },
    async save() {
      if (this.saving || this.readOnly) return
      this.saving = true
      try {
        const path = '/organization/' + this.section.key + (this.editId ? '/' + encodeURIComponent(this.editId) : '')
        if (this.mode === 'password') {
          await api(path + '/password', 'PUT', { password: this.form.password })
        } else {
          const { username, displayName, departmentId, enabled, roleIds, password } = this.form
          const body = this.mode === 'employee'
            ? { username, displayName, departmentId: departmentId || null, enabled, roleIds, password }
            : this.form
          await api(path, this.editId ? 'PUT' : 'POST', body)
        }
        this.dialog = false
        this.$message.success('保存成功')
        await this.load()
      } catch (error) { this.$message.error(error.message) }
      finally { this.saving = false }
    },
    async remove(collection, row) {
      try {
        await this.$confirm('确认删除「' + row.name + '」？', '确认删除', { type: 'warning' })
        await api('/organization/' + collection + '/' + row.id, 'DELETE')
        this.$message.success('已删除')
        await this.load()
      } catch (error) { if (error?.message) this.$message.error(error.message) }
    }
  }
}
</script>
