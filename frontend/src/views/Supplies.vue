<template>
  <div class="pane">
    <header class="hd"><h2>耗材</h2><span class="sub">水位条上的虚线是预警线；点「用掉 1 件」直接出库，见底会标红</span>
      <button class="prime" @click="openNew">登记耗材</button></header>
    <div class="levels">
      <div v-for="s in items" :key="s.id" class="lv" :class="tone(s)">
        <div class="lv-top"><b>{{ s.supplyName }}</b><span class="code">{{ s.supplyCode }} · {{ s.unitText || '件' }}</span>
          <span class="state">{{ s.supplyState }}</span></div>
        <div class="bar"><div class="fill" :style="{ width: pct(s) + '%' }"></div>
          <div v-if="s.warnLine" class="warnline" :style="{ left: warnPct(s) + '%' }"></div></div>
        <div class="lv-foot"><span>现有 {{ s.stock }}</span><span>预警线 {{ s.warnLine ?? '未设' }}</span>
          <button class="ghost" @click="use(s)">用掉 1 件</button>
          <button class="ghost" @click="openEdit(s)">改</button></div>
      </div>
    </div>
    <el-dialog v-model="dialog" :title="form.id ? '修改耗材' : '登记耗材'" width="420px">
      <div class="fr"><label>编号</label><el-input v-model="form.supplyCode" /></div>
      <div class="fr"><label>名称</label><el-input v-model="form.supplyName" /></div>
      <div class="fr"><label>单位</label><el-input v-model="form.unitText" placeholder="桶 / 条 / 瓶" /></div>
      <div class="fr"><label>库存</label><el-input v-model="form.stock" /></div>
      <div class="fr"><label>预警线</label><el-input v-model="form.warnLine" /></div>
      <template #footer><el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<script>
import { supplyApi } from '../api'

export default {
  name: 'Supplies',
  data() {
    return { items: [], dialog: false, form: {}, MAX: 60 }
  },
  methods: {
    pct(s) {
      return Math.min(100, Math.round((Number(s.stock || 0) * 100) / this.MAX))
    },
    warnPct(s) {
      return Math.min(100, Math.round((Number(s.warnLine || 0) * 100) / this.MAX))
    },
    tone(s) {
      if (s.supplyState === '已用完') return 'out'
      return s.supplyState === '不足' ? 'low' : ''
    },
    async load() {
      this.items = await supplyApi.list()
    },
    openNew() {
      this.form = { supplyState: '正常' }
      this.dialog = true
    },
    openEdit(row) {
      this.form = { ...row }
      this.dialog = true
    },
    async submit() {
      try {
        if (this.form.id) await supplyApi.save(this.form.id, this.form)
        else await supplyApi.add(this.form)
        this.dialog = false
        await this.load()
        this.$message.success('保存好了')
      } catch (e) { this.$message.error(e.message) }
    },
    async use(s) {
      try {
        await supplyApi.consume(s.id, 1)
        await this.load()
        this.$message.success('出库 1 ' + (s.unitText || '件'))
      } catch (e) { this.$message.error(e.message) }
    }
  },
  mounted() { this.load() }
}
</script>

<style scoped>
.hd { display: flex; align-items: center; gap: 14px; margin-bottom: 18px; }
.hd h2 { margin: 0; font-size: 20px; }
.sub { flex: 1; color: #99a1a6; font-size: 12px; }
.prime { background: var(--el-color-primary); color: #fff; border: none; border-radius: 8px;
  padding: 8px 18px; font-size: 13px; cursor: pointer; }
.levels { background: #fff; border: 1px solid #e9edef; border-radius: 12px; padding: 6px 18px; }
.lv { padding: 14px 0; border-bottom: 1px solid #f3f6f7; }
.lv-top { display: flex; align-items: center; gap: 12px; margin-bottom: 10px; }
.lv-top b { font-size: 14px; }
.code { color: #99a1a6; font-size: 12px; }
.state { margin-left: auto; font-size: 12px; color: var(--el-color-primary-dark-2); }
.lv.low .state { color: #b4761f; }
.lv.out .state { color: #c0392b; }
.bar { position: relative; height: 14px; background: #f2f5f6; border-radius: 7px; overflow: hidden; }
.fill { height: 100%; background: var(--el-color-primary); border-radius: 7px; transition: width .3s; }
.lv.low .fill { background: #e6a23c; }
.lv.out .fill { background: #d9534f; }
.warnline { position: absolute; top: 0; bottom: 0; width: 0; border-left: 2px dashed #b9c2c6; }
.lv-foot { display: flex; align-items: center; gap: 16px; margin-top: 8px; font-size: 12px; color: #99a1a6; }
.lv-foot .ghost:first-of-type { margin-left: auto; }
.ghost { background: #fff; border: 1px solid var(--el-color-primary-light-7); color: var(--el-color-primary-dark-2);
  border-radius: 6px; padding: 4px 12px; font-size: 12px; cursor: pointer; }
.fr { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.fr label { width: 62px; text-align: right; font-size: 13px; color: #647077; }
</style>
