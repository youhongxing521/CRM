export async function api(path, method = 'GET', body) {
  const response = await fetch('/api' + path, {
    method,
    headers: { 'X-Cem-Request': '1', ...(body === undefined ? {} : { 'Content-Type': 'application/json' }) },
    body: body === undefined ? undefined : JSON.stringify(body)
  })
  if (response.status === 204) return null
  const data = await response.json().catch(() => ({}))
  if (!response.ok) {
    if (response.status === 401 && path !== '/login') window.dispatchEvent(new Event('cem-session-expired'))
    throw Object.assign(new Error(data.message || ('请求失败 (' + response.status + ')')), { status: response.status })
  }
  return data
}

export function dateString(date = new Date()) {
  return [date.getFullYear(), String(date.getMonth() + 1).padStart(2, '0'),
    String(date.getDate()).padStart(2, '0')].join('-')
}

export function money(amount) {
  return '¥' + Number(amount || 0).toLocaleString('zh-CN', { maximumFractionDigits: 2 })
}

export function statusType(status) {
  if (['已通过', '已完成', '已成交', '赢单', '有效', '启用', '在售', '已发布', '已解决'].includes(status)) return 'success'
  if (['审核中', '待审批', '待处理', '待跟进', '商务谈判'].includes(status)) return 'warning'
  if (['已驳回', '已流失', '输单', '停售', '停用'].includes(status)) return 'danger'
  if (['草稿', '潜在客户'].includes(status)) return 'info'
  return ''
}
