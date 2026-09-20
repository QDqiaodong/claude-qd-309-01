<template>
  <div class="pane">
    <header class="hd"><h2>工位</h2>
      <span class="sub">占用按统一口径：没洗完的洗车单 + 回炉中的车一起占座；满位接不了新单和新回炉</span>
      <button class="prime" @click="openNew">新增工位</button></header>
    <div class="layout">
      <section class="board">
        <div class="b-title">当前占用</div>
        <div class="b-num">{{ occupiedBays }} <small>个工位有车</small></div>
        <div class="b-line">洗车中 {{ washingTotal }} 辆 · 回炉中 {{ reworkingTotal }} 辆</div>
        <div class="b-line free">还空着 {{ freeBays }} 个工位</div>
      </section>
      <section class="queue">
        <div class="q-row head"><span>编号</span><span>名称</span><span>占用 / 容纳</span><span>状态</span><span>操作</span></div>
        <div v-for="b in sorted" :key="b.id" class="q-row" :class="{ stopped: b.bayState === '停用' }">
          <span class="mono">{{ b.bayCode }}</span>
          <span>{{ b.bayName }}</span>
          <span>
            <b :class="{ full: isFull(b) }">{{ b.occupiedSeats ?? 0 }} / {{ seatCount(b) }}</b>
            <span class="occ" v-if="(b.occupiedSeats ?? 0) > 0">
              （洗 {{ b.washingCars ?? 0 }}<template v-if="(b.reworkingCars ?? 0) > 0"> · 回炉 {{ b.reworkingCars }}</template>）
            </span>
          </span>
          <span>
            <span class="st">{{ b.bayState }}</span>
            <el-tag v-if="isFull(b) && b.bayState !== '停用'" size="small" type="danger" effect="plain" class="tg">已满</el-tag>
            <el-tag v-if="(b.reworkingCars ?? 0) > 0 && b.bayState === '停用'" size="small" type="danger" effect="dark" class="tg">
              回炉中待改派
            </el-tag>
          </span>
          <span><button class="ghost" @click="openEdit(b)">改</button></span>
        </div>
      </section>
    </div>
    <el-dialog v-model="dialog" :title="form.id ? '修改工位' : '新增工位'" width="420px">
      <div class="fr"><label>编号</label><el-input v-model="form.bayCode" /></div>
      <div class="fr"><label>名称</label><el-input v-model="form.bayName" /></div>
      <div class="fr"><label>同时容纳</label><el-input v-model="form.seatCount" /></div>
      <div class="fr"><label>状态</label><el-input v-model="form.bayState" placeholder="空闲 / 占用 / 停用" /></div>
      <div class="tip" v-if="form.id">还有没洗完的洗车单时停用会被拦；回炉中的车不拦停用，但要去回炉台把它改派到空位。</div>
      <template #footer><el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<script>
import { bayApi } from '../api'

export default {
  name: 'Bays',
  data() {
    return { items: [], dialog: false, form: {} }
  },
  computed: {
    occupiedBays() {
      return this.items.filter((b) => (b.occupiedSeats ?? 0) > 0).length
    },
    washingTotal() {
      return this.items.reduce((s, b) => s + (b.washingCars ?? 0), 0)
    },
    reworkingTotal() {
      return this.items.reduce((s, b) => s + (b.reworkingCars ?? 0), 0)
    },
    freeBays() {
      return this.items.filter((b) => b.bayState !== '停用' && !this.isFull(b)).length
    },
    sorted() {
      const order = { 占用: 0, 空闲: 1, 停用: 2 }
      return [...this.items].sort((a, b) => (order[a.bayState] ?? 9) - (order[b.bayState] ?? 9))
    }
  },
  methods: {
    seatCount(b) {
      return b.seatCount && b.seatCount > 0 ? b.seatCount : 1
    },
    isFull(b) {
      return (b.occupiedSeats ?? 0) >= this.seatCount(b)
    },
    async load() {
      this.items = await bayApi.list()
    },
    openNew() {
      this.form = { bayState: '空闲' }
      this.dialog = true
    },
    openEdit(row) {
      this.form = { ...row }
      this.dialog = true
    },
    async submit() {
      try {
        if (this.form.id) await bayApi.save(this.form.id, this.form)
        else await bayApi.add(this.form)
        this.dialog = false
        await this.load()
        this.$message.success('保存好了')
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
.layout { display: grid; grid-template-columns: 280px 1fr; gap: 16px; }
.board { background: linear-gradient(150deg, var(--el-color-primary), #263238); color: #fff;
  border-radius: 16px; padding: 24px; text-align: center; height: fit-content; }
.b-title { font-size: 12px; opacity: .8; }
.b-num { font-size: 40px; font-weight: 800; margin: 8px 0; }
.b-num small { font-size: 13px; font-weight: 400; opacity: .8; }
.b-line { font-size: 12px; opacity: .85; margin-top: 6px; }
.b-line.free { opacity: .7; }
.queue { background: #fff; border: 1px solid #e9edef; border-radius: 12px; overflow: hidden; }
.q-row { display: grid; grid-template-columns: 90px 1fr 150px 170px 66px; gap: 8px; align-items: center;
  padding: 11px 14px; border-bottom: 1px solid #f3f6f7; font-size: 13px; }
.q-row.head { background: #f7f9fa; color: #99a1a6; font-size: 12px; }
.q-row.stopped { background: #fafafa; }
.mono { font-family: ui-monospace, Menlo, monospace; color: #99a1a6; }
.occ { color: #99a1a6; font-size: 12px; margin-left: 4px; }
b.full { color: #e04c4c; }
.st { color: var(--el-color-primary-dark-2); }
.tg { margin-left: 6px; }
.ghost { background: #fff; border: 1px solid var(--el-color-primary-light-7); color: var(--el-color-primary-dark-2);
  border-radius: 7px; padding: 4px 12px; font-size: 12px; cursor: pointer; }
.fr { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.fr label { width: 72px; text-align: right; font-size: 13px; color: #647077; }
.tip { background: #fff7ed; color: #c2610c; font-size: 12px; border-radius: 8px; padding: 8px 10px; }
</style>
