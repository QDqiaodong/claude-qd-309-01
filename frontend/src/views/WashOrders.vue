<template>
  <div class="pg">
    <div class="hd">
      <h2>洗车单</h2>
      <span class="hint">顶上几个页签按状态分栏，切换看不同状态的。</span>
      <input class="search" v-model="kw" placeholder="搜索编号或名称" />
      <button class="btn solid" @click="openNew">新增</button>
    </div>
    <div class="tabs">
      <div class="tab" :class="{ on: tab === 'all' }" @click="tab = 'all'">全部 {{ rows.length }}</div>
      <div class="tab" v-for="v in OPTS" :key="v" :class="{ on: tab === v }" @click="tab = v">
        {{ v }} {{ countOf(v) }}
      </div>
    </div>
    <div class="tt">
      <div class="tr th"><span>编号</span><span>名称</span><span v-for="fd in BODY_FIELDS" :key="fd.k">{{ fd.l }}</span><span>操作</span></div>
      <div class="tr" v-for="it in shown" :key="it.id">
        <span class="mono">{{ it.code }}</span>
        <span>{{ it.name }}</span>
        <span v-for="fd in BODY_FIELDS" :key="fd.k">{{ it[fd.k] }}</span>
        <span><button class="btn sm" @click="openEdit(it)">改</button></span>
      </div>
      <div class="blank" v-if="!shown.length">这一类下暂时没有</div>
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
import { orderApi } from '../api'

const rows = ref([])
const show = ref(false)
const form = ref({})
const kw = ref('')
const FORM_FIELDS = [{"k":"code","l":"编号"},{"k":"plate","l":"车牌号"},{"k":"bayId","l":"使用工位"},{"k":"serviceType","l":"服务类型"},{"k":"price","l":"金额"},{"k":"status","l":"状态"}]
const BODY_FIELDS = [{"k":"plate","l":"车牌号"},{"k":"bayId","l":"使用工位"},{"k":"serviceType","l":"服务类型"},{"k":"price","l":"金额"}]
const ST = 'status'
const OPTS = ["清洗中","待洗","已完成"]

const picked = ref([])
const filtered = computed(() => {
  if (!kw.value) return rows.value
  const k = kw.value.toLowerCase()
  return rows.value.filter(r => (r.code || '').toLowerCase().includes(k) || (r.name || '').toLowerCase().includes(k))
})

async function load() { rows.value = await orderApi.list() }
function openNew() { form.value = {}; show.value = true }
function openEdit(it) { form.value = { ...it }; show.value = true }
async function save() {
  try {
    if (form.value.id) await orderApi.update(form.value.id, form.value)
    else await orderApi.create(form.value)
    show.value = false
    await load()
    ElMessage.success('已保存')
  } catch (e) { ElMessage.error(e.message) }
}
async function patch(it, key, value) {
  try {
    await orderApi.update(it.id, { [key]: value })
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
const tab = ref('all')
const shown = computed(() => tab.value === 'all' ? rows.value : rows.value.filter(r => r[ST] === tab.value))
function countOf(v) { return rows.value.filter(r => r[ST] === v).length }
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
.search { display: none; }
.tabs { display: flex; gap: 8px; margin-bottom: 12px; flex-wrap: wrap; }
.tab { padding: 6px 16px; border-radius: 18px; background: #fff; border: 1px solid #eee;
  font-size: 13px; color: #666; cursor: pointer; }
.tab.on { background: var(--el-color-primary); color: #fff; border-color: var(--el-color-primary); }
.tt { background: #fff; border: 1px solid #eee; border-radius: 10px; overflow: hidden; }
.tr { display: grid; grid-auto-flow: column; grid-auto-columns: 1fr; padding: 10px 14px;
  border-bottom: 1px solid #f7f7f7; font-size: 13px; align-items: center; }
.tr.th { background: #fafafa; color: #888; }
.mono { font-family: ui-monospace, monospace; color: #aaa; }

</style>
