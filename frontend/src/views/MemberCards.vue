<template>
  <div class="pg">
    <div class="hd">
      <h2>会员卡</h2>
      <span class="hint">每行右边一条迷你走势，一眼看出高低。</span>
      <input class="search" v-model="kw" placeholder="搜索编号或名称" />
      <button class="btn solid" @click="openNew">新增</button>
    </div>
    <div class="ls">
      <div class="li" v-for="it in filtered" :key="it.id">
        <div class="l-main">
          <div class="l-t">{{ it.name }}</div>
          <div class="l-s">{{ it.code }} · {{ it[ST] }}</div>
        </div>
        <svg class="spark" viewBox="0 0 120 34" preserveAspectRatio="none">
          <polyline :points="spark(it)" fill="none" stroke="var(--el-color-primary)" stroke-width="2" />
        </svg>
        <div class="l-v">{{ it[NK] ?? '-' }}</div>
        <button class="btn sm" @click="openEdit(it)">改</button>
      </div>
    </div>
  </div>

    <el-dialog v-model="show" :title="form.id ? '修改' : '新增'" width="440px">
      <div class="frm">
        <div class="fr" v-for="fd in FORM_FIELDS" :key="fd.k">
          <label>{{ fd.l }}</label>
          <el-input v-model="form[fd.k]" :placeholder="'请填写' + fd.l" />
        </div>
      </div>
      <template #footer>
        <el-button @click="show = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { cardApi } from '../api'

const rows = ref([])
const show = ref(false)
const form = ref({})
const kw = ref('')
const FORM_FIELDS = [{"k":"code","l":"编号"},{"k":"holder","l":"持卡人"},{"k":"phone","l":"手机号"},{"k":"balance","l":"余额"},{"k":"level","l":"卡等级"},{"k":"status","l":"状态"}]
const BODY_FIELDS = [{"k":"holder","l":"持卡人"},{"k":"phone","l":"手机号"},{"k":"balance","l":"余额"},{"k":"level","l":"卡等级"}]
const ST = 'status'
const OPTS = ["正常","已停用"]

const picked = ref([])
const filtered = computed(() => {
  if (!kw.value) return rows.value
  const k = kw.value.toLowerCase()
  return rows.value.filter(r => (r.code || '').toLowerCase().includes(k) || (r.name || '').toLowerCase().includes(k))
})

async function load() { rows.value = await cardApi.list() }
function openNew() { form.value = {}; show.value = true }
function openEdit(it) { form.value = { ...it }; show.value = true }
async function save() {
  try {
    if (form.value.id) await cardApi.update(form.value.id, form.value)
    else await cardApi.create(form.value)
    show.value = false
    await load()
    ElMessage.success('已保存')
  } catch (e) { ElMessage.error(e.message) }
}
async function patch(it, key, value) {
  try {
    await cardApi.update(it.id, { [key]: value })
    await load()
    ElMessage.success('已更新')
  } catch (e) { ElMessage.error(e.message); await load() }
}
function togglePick(id) {
  const i = picked.value.indexOf(id)
  if (i >= 0) picked.value.splice(i, 1)
  else picked.value.push(id)
}
onMounted(load)
const NK = (BODY_FIELDS.find(f => /库存|数量|余额|可租|容量|台数/.test(f.l)) || BODY_FIELDS[0] || {}).k
function spark(it) {
  const base = Number(it[NK] || 0)
  if (!base) return '0,30 120,30'
  const pts = [0.6, 0.9, 0.75, 1, 0.85, 1.05, 0.95].map((r, i) => {
    const y = 32 - Math.min(30, base * r * 2.2)
    return (i * 20) + ',' + y.toFixed(1)
  })
  return pts.join(' ')
}
</script>
<style scoped>
.pg { padding: 4px 2px 40px; color: #303133; }
.pg h2 { margin: 0; font-size: 19px; }
.hd { display: flex; align-items: center; gap: 14px; margin-bottom: 16px; flex-wrap: wrap; }
.hd .hint { color: #888; font-size: 13px; flex: 1; }
.btn { border: 1px solid var(--el-color-primary); background: #fff; color: var(--el-color-primary);
  border-radius: 6px; padding: 6px 14px; cursor: pointer; font-size: 13px; }
.btn:hover { background: var(--el-color-primary-light-9); }
.btn.solid { background: var(--el-color-primary); color: #fff; }
.btn.sm { padding: 3px 10px; font-size: 12px; }
.frm .fr { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.frm .fr label { width: 88px; text-align: right; color: #666; font-size: 13px; }
.blank { color: #bbb; padding: 30px; text-align: center; }
.search { border: 1px solid #e3e3e3; border-radius: 6px; padding: 6px 12px; font-size: 13px; width: 160px; }
.ls { background: #fff; border: 1px solid #eee; border-radius: 12px; padding: 6px 16px; }
.li { display: grid; grid-template-columns: 1fr 140px 80px 56px; gap: 16px; align-items: center;
  padding: 12px 0; border-bottom: 1px solid #f7f7f7; }
.l-t { font-size: 14px; font-weight: 500; }
.l-s { font-size: 12px; color: #999; margin-top: 3px; }
.spark { width: 120px; height: 34px; }
.l-v { text-align: right; font-weight: 700; color: var(--el-color-primary-dark-2); font-size: 16px; }

</style>
