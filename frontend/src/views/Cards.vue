<template>
  <div class="pane">
    <header class="hd"><h2>会员卡</h2><span class="sub">余额条按 600 元封顶画；停卡的整张发灰</span>
      <button class="prime" @click="openNew">办卡</button></header>
    <div class="cards">
      <article v-for="c in items" :key="c.id" class="mc" :class="{ off: c.cardState !== '正常' }">
        <div class="mc-top"><span class="mc-no">{{ c.cardNo }}</span><span class="mc-level">{{ c.cardLevel || '普通卡' }}</span></div>
        <div class="mc-name">{{ c.holderName }}</div>
        <div class="mc-phone">{{ c.phone || '未留电话' }}</div>
        <div class="mc-balance">¥{{ c.balance ?? 0 }}</div>
        <div class="mc-bar"><div class="mc-fill" :style="{ width: balancePct(c) + '%' }"></div></div>
        <div class="mc-foot"><span>{{ c.cardState }}</span>
          <span>
            <button class="ghost" @click="openFlows(c)">流水</button>
            <button class="ghost" @click="openEdit(c)">修改</button>
          </span></div>
      </article>
    </div>
    <el-dialog v-model="flowDlg" :title="flowCard ? '卡流水 · ' + flowCard.cardNo + '（' + flowCard.holderName + '）' : '卡流水'" width="560px">
      <div class="flowtip">每动一次余额落一行，钉着是哪张洗车单；晚班对账按单号找单。</div>
      <div class="flowtab" v-if="flows.length">
        <div class="frow head"><span>流水号</span><span>时间</span><span>类型</span>
          <span class="r">金额</span><span class="r">走后余额</span><span>洗车单</span></div>
        <div class="frow" v-for="f in flows" :key="f.id">
          <span class="mono">{{ f.flowNo }}</span>
          <span class="dim">{{ fmtTime(f.createdAt) }}</span>
          <span><el-tag size="small" :type="f.flowType === '扣款' ? 'danger' : 'success'" effect="plain">{{ f.flowType }}</el-tag></span>
          <span class="r">{{ f.flowType === '扣款' ? '-' : '+' }}¥{{ f.amount }}</span>
          <span class="r">¥{{ f.balanceAfter }}</span>
          <span class="mono">{{ f.orderNo || '—' }}</span>
        </div>
      </div>
      <div v-else class="flowtip">这张卡还没有动过账</div>
      <template #footer><el-button @click="flowDlg = false">关上</el-button></template>
    </el-dialog>
    <el-dialog v-model="dialog" :title="form.id ? '修改会员卡' : '办一张会员卡'" width="430px">
      <div class="fr"><label>卡号</label><el-input v-model="form.cardNo" /></div>
      <div class="fr"><label>持卡人</label><el-input v-model="form.holderName" /></div>
      <div class="fr"><label>手机号</label><el-input v-model="form.phone" /></div>
      <div class="fr"><label>余额</label><el-input v-model="form.balance" /></div>
      <div class="fr"><label>卡等级</label><el-input v-model="form.cardLevel" placeholder="普通卡 / 银卡 / 金卡" /></div>
      <div class="fr"><label>状态</label><el-input v-model="form.cardState" placeholder="正常 / 已停卡" /></div>
      <template #footer><el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<script>
import { cardApi } from '../api'

export default {
  name: 'Cards',
  data() {
    return { items: [], dialog: false, form: {}, TOP: 600, flowDlg: false, flowCard: null, flows: [] }
  },
  methods: {
    balancePct(c) {
      return Math.min(100, Math.round((Number(c.balance || 0) * 100) / this.TOP))
    },
    fmtTime(t) {
      return t ? String(t).replace('T', ' ').slice(5, 16) : ''
    },
    async load() {
      this.items = await cardApi.list()
    },
    async openFlows(c) {
      this.flowCard = c
      this.flows = await cardApi.flows(c.id)
      this.flowDlg = true
    },
    openNew() {
      this.form = { cardState: '正常' }
      this.dialog = true
    },
    openEdit(row) {
      this.form = { ...row }
      this.dialog = true
    },
    async submit() {
      try {
        if (this.form.id) await cardApi.save(this.form.id, this.form)
        else await cardApi.add(this.form)
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
.cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(236px, 1fr)); gap: 14px; }
.mc { background: #fff; border: 1px solid #e9edef; border-radius: 12px; padding: 16px; }
.mc.off { opacity: .6; background: #fafbfb; }
.mc-top { display: flex; justify-content: space-between; font-size: 12px; }
.mc-no { color: #99a1a6; }
.mc-level { color: var(--el-color-primary-dark-2); }
.mc-name { font-size: 16px; font-weight: 600; margin: 8px 0 3px; }
.mc-phone { font-size: 12px; color: #99a1a6; }
.mc-balance { font-size: 24px; font-weight: 700; color: var(--el-color-primary-dark-2); margin: 12px 0 8px; }
.mc-bar { height: 8px; background: #f2f5f6; border-radius: 4px; overflow: hidden; }
.mc-fill { height: 100%; background: var(--el-color-primary); border-radius: 4px; }
.mc-foot { display: flex; justify-content: space-between; align-items: center; margin-top: 12px;
  font-size: 12px; color: #99a1a6; }
.ghost { background: #fff; border: 1px solid var(--el-color-primary-light-7); color: var(--el-color-primary-dark-2);
  border-radius: 6px; padding: 4px 12px; font-size: 12px; cursor: pointer; }
.fr { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.fr label { width: 66px; text-align: right; font-size: 13px; color: #647077; }
.flowtip { font-size: 12px; color: #99a1a6; margin-bottom: 10px; }
.flowtab { border: 1px solid #eef1f2; border-radius: 8px; overflow: hidden; }
.frow { display: grid; grid-template-columns: 76px 96px 56px 72px 76px 1fr; gap: 8px;
  padding: 8px 12px; font-size: 12px; border-bottom: 1px solid #f3f6f7; align-items: center; }
.frow.head { background: #f7f9fa; color: #99a1a6; }
.frow .r { text-align: right; }
.mono { font-family: ui-monospace, Menlo, monospace; color: #99a1a6; }
.dim { color: #99a1a6; }
</style>
