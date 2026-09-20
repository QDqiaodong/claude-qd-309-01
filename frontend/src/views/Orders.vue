<template>
  <div class="pane">
    <header class="hd"><h2>洗车单</h2><span class="sub">按状态分页签；「推进」把单子往前推一步，已完成单可以挂回炉</span>
      <button class="prime" @click="openNew">开单</button></header>
    <div class="tabs">
      <div class="tab" :class="{ on: tab === 'all' }" @click="tab = 'all'">全部 {{ items.length }}</div>
      <div v-for="s in STATES" :key="s" class="tab" :class="{ on: tab === s }" @click="tab = s">
        {{ s }} {{ countOf(s) }}
      </div>
    </div>
    <div class="table">
      <div class="row head"><span>单号</span><span>车牌</span><span>工位</span><span>服务</span>
        <span class="r">金额</span><span>日期</span><span>状态</span><span>卡账</span><span class="r">操作</span></div>
      <div v-for="o in shown" :key="o.id" class="row">
        <span class="mono">{{ o.orderNo }}</span>
        <span>{{ o.plateNo }}</span>
        <span>{{ bayName(o.bayId) }}</span>
        <span>{{ o.serviceType || '—' }}</span>
        <span class="r">¥{{ o.price ?? 0 }}</span>
        <span class="dim">{{ o.orderDate }}</span>
        <span>
          <span class="st">{{ o.washState }}</span>
          <el-tag v-if="o.reworkOpen" size="small" type="warning" effect="dark" class="rw">回炉未结</el-tag>
        </span>
        <span>
          <el-tag v-if="o.payState === '已支付'" size="small" type="success" effect="plain"
                  :title="'扣款流水 ' + (o.payFlowNo || '') + ' · ' + (o.payCardNo || '')">已支付</el-tag>
          <el-tag v-else-if="o.payState === '已退款'" size="small" type="info" effect="plain">已退款</el-tag>
          <span v-else class="dim">未支付</span>
        </span>
        <span class="r">
          <button v-if="nextOf(o)" class="ghost" @click="advance(o)">推进</button>
          <button v-if="o.payState === '未支付' && o.washState !== '已撤销'" class="ghost"
                  @click="openPay(o)">卡结账</button>
          <button v-if="o.washState === '已完成'" class="ghost rw-btn"
                  :disabled="!!o.reworkOpen" :title="o.reworkOpen ? '还有未验收的回炉，不能再挂' : '挂到回炉台'"
                  @click="goHang(o)">{{ o.reworkOpen ? '回炉中' : '挂回炉' }}</button>
          <button v-if="o.washState !== '已撤销'" class="ghost void-btn"
                  @click="voidIt(o)">撤单</button>
          <button class="ghost" @click="openDetail(o)">详情</button>
        </span>
      </div>
    </div>

    <el-dialog v-model="dialog" title="开一张洗车单" width="440px">
      <div class="fr"><label>单号</label><el-input v-model="form.orderNo" /></div>
      <div class="fr"><label>车牌</label><el-input v-model="form.plateNo" /></div>
      <div class="fr"><label>工位</label>
        <el-select v-model="form.bayId" style="flex:1" placeholder="停用或满位的工位排不进去">
          <el-option v-for="b in usableBays" :key="b.id"
                     :label="b.bayCode + ' ' + b.bayName + '（空 ' + freeSeats(b) + '/' + seatCount(b) + '）'"
                     :value="b.id" />
        </el-select></div>
      <div class="fr"><label>服务</label><el-input v-model="form.serviceType" /></div>
      <div class="fr"><label>金额</label><el-input v-model="form.price" /></div>
      <template #footer><el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="payDlg" title="刷卡结账" width="420px">
      <div v-if="payOrder_" class="paybox">
        <div class="d-row"><span>洗车单</span><b class="mono">{{ payOrder_.orderNo }}</b></div>
        <div class="d-row"><span>金额</span><b>¥{{ payOrder_.price ?? 0 }}（按单上写明的价扣，改不了）</b></div>
        <div class="fr"><label>会员卡</label>
          <el-select v-model="payCardId" style="flex:1" placeholder="选一张状态正常的卡">
            <el-option v-for="c in normalCards" :key="c.id"
                       :label="c.cardNo + ' ' + c.holderName + '（余 ¥' + (c.balance ?? 0) + '）'"
                       :value="c.id" />
          </el-select></div>
        <div class="paytip">扣款钉在这张单上；这单扣过一笔后，再刷会被当场挡下。</div>
      </div>
      <template #footer><el-button @click="payDlg = false">取消</el-button>
        <el-button type="primary" :disabled="!payCardId" @click="doPay">确认扣款</el-button></template>
    </el-dialog>

    <el-dialog v-model="detailDlg" title="洗车单详情" width="460px">
      <div v-if="detail" class="detail">
        <div class="d-row"><span>单号</span><b class="mono">{{ detail.orderNo }}</b></div>
        <div class="d-row"><span>车牌</span><b>{{ detail.plateNo }}</b></div>
        <div class="d-row"><span>工位</span><b>{{ bayName(detail.bayId) }}</b></div>
        <div class="d-row"><span>服务</span><b>{{ detail.serviceType || '—' }}</b></div>
        <div class="d-row"><span>金额</span><b>¥{{ detail.price ?? 0 }}</b></div>
        <div class="d-row"><span>日期</span><b>{{ detail.orderDate }}</b></div>
        <div class="d-row"><span>状态</span>
          <b><span class="st">{{ detail.washState }}</span>
            <el-tag v-if="detail.reworkOpen" size="small" type="warning" effect="dark" class="rw">回炉未结</el-tag>
          </b>
        </div>
        <div class="d-row"><span>卡账</span>
          <b v-if="detail.payState === '已支付'">已支付 · 流水 {{ detail.payFlowNo }} · 卡 {{ detail.payCardNo }}</b>
          <b v-else-if="detail.payState === '已退款'">已退款 · 原扣款流水 {{ detail.payFlowNo }} 已退回 {{ detail.payCardNo }}</b>
          <b v-else class="dim">未支付</b>
        </div>
        <div class="d-row" v-if="detail.reworkOpen"><span>未结回炉</span>
          <b class="mono">{{ detail.openReworkNo }}（验收前原单不能退回待洗/清洗中，也不能再挂回炉）</b></div>
      </div>
      <template #footer>
        <el-button @click="detailDlg = false">关上</el-button>
        <el-button v-if="detail && detail.washState === '已完成' && !detail.reworkOpen"
                   type="primary" @click="detailDlg = false; goHang(detail)">挂回炉</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { bayApi, cardApi, orderApi } from '../api'

export default {
  name: 'Orders',
  data() {
    return { items: [], bays: [], cards: [], tab: 'all', dialog: false, form: {},
      detailDlg: false, detail: null, payDlg: false, payOrder_: null, payCardId: null,
      STATES: ['待洗', '清洗中', '已完成', '已撤销'] }
  },
  computed: {
    shown() {
      return this.tab === 'all' ? this.items : this.items.filter((o) => o.washState === this.tab)
    },
    // 开单只能选没停用、还有空位的工位，满位的不出现。
    usableBays() {
      return this.bays.filter((b) => b.bayState !== '停用' && this.freeSeats(b) > 0)
    },
    // 停卡刷不了，结账只列状态正常的卡。
    normalCards() {
      return this.cards.filter((c) => c.cardState === '正常')
    }
  },
  methods: {
    countOf(state) {
      return this.items.filter((o) => o.washState === state).length
    },
    bayName(id) {
      const b = this.bays.find((x) => x.id === id)
      return b ? b.bayCode : '未排'
    },
    seatCount(b) {
      return b.seatCount && b.seatCount > 0 ? b.seatCount : 1
    },
    freeSeats(b) {
      return Math.max(0, this.seatCount(b) - (b.occupiedSeats ?? 0))
    },
    nextOf(o) {
      // 「推进」只走 待洗 → 清洗中 → 已完成；已撤销是终态，靠「撤单」走，不出现在推进里。
      const flow = ['待洗', '清洗中', '已完成']
      const i = flow.indexOf(o.washState)
      return i >= 0 && i < flow.length - 1 ? flow[i + 1] : null
    },
    async load() {
      const [os, bs, cs] = await Promise.all([orderApi.list(), bayApi.list(), cardApi.list()])
      this.items = os
      this.bays = bs
      this.cards = cs
    },
    openNew() {
      this.form = { bayId: this.usableBays[0] ? this.usableBays[0].id : null }
      this.dialog = true
    },
    async submit() {
      try {
        await orderApi.add(this.form)
        this.dialog = false
        await this.load()
        this.$message.success('开好了')
      } catch (e) { this.$message.error(e.message) }
    },
    async advance(o) {
      try {
        await orderApi.save(o.id, { washState: this.nextOf(o) })
        await this.load()
        this.$message.success('已推进')
      } catch (e) { this.$message.error(e.message) }
    },
    openDetail(o) {
      this.detail = o
      this.detailDlg = true
    },
    goHang(o) {
      if (o.reworkOpen) return
      this.$router.push({ path: '/reworks', query: { orderId: o.id } })
    },
    openPay(o) {
      this.payOrder_ = o
      this.payCardId = null
      this.payDlg = true
    },
    async doPay() {
      try {
        const flow = await orderApi.pay(this.payOrder_.id, this.payCardId)
        this.payDlg = false
        await this.load()
        this.$message.success('已扣 ¥' + flow.amount + '，流水 ' + flow.flowNo + ' 钉在单 ' + this.payOrder_.orderNo + ' 上')
      } catch (e) { this.$message.error(e.message) }
    },
    async voidIt(o) {
      const tip = o.payState === '已支付'
        ? `整笔撤掉 ${o.orderNo}？已扣的 ¥${o.price ?? 0} 会原数退回 ${o.payCardNo || '原卡'}`
        : `整笔撤掉 ${o.orderNo}？这单没刷过卡，不动卡账`
      try {
        await this.$confirm(tip, '撤单', { type: 'warning', confirmButtonText: '撤掉', cancelButtonText: '再想想' })
      } catch { return }
      try {
        await orderApi.void(o.id)
        await this.load()
        this.$message.success('已整笔撤掉')
      } catch (e) { this.$message.error(e.message) }
    }
  },
  mounted() { this.load() }
}
</script>

<style scoped>
.hd { display: flex; align-items: center; gap: 14px; margin-bottom: 14px; }
.hd h2 { margin: 0; font-size: 20px; }
.sub { flex: 1; color: #99a1a6; font-size: 12px; }
.prime { background: var(--el-color-primary); color: #fff; border: none; border-radius: 8px;
  padding: 8px 18px; font-size: 13px; cursor: pointer; }
.tabs { display: flex; gap: 8px; margin-bottom: 12px; }
.tab { padding: 6px 16px; border-radius: 16px; background: #fff; border: 1px solid #e9edef;
  font-size: 12px; color: #647077; cursor: pointer; }
.tab.on { background: var(--el-color-primary); color: #fff; border-color: var(--el-color-primary); }
.table { background: #fff; border: 1px solid #e9edef; border-radius: 12px; overflow: hidden; }
.row { display: grid; grid-template-columns: 88px 92px 64px 88px 60px 96px 118px 76px 1fr; gap: 8px;
  align-items: center; padding: 11px 14px; border-bottom: 1px solid #f3f6f7; font-size: 13px; }
.row.head { background: #f7f9fa; color: #99a1a6; font-size: 12px; }
.mono { font-family: ui-monospace, Menlo, monospace; color: #99a1a6; font-size: 12px; }
.r { text-align: right; }
.dim { color: #99a1a6; font-size: 12px; }
.st { color: var(--el-color-primary-dark-2); font-size: 12px; }
.rw { margin-left: 6px; }
.ghost { background: #fff; border: 1px solid var(--el-color-primary-light-7); color: var(--el-color-primary-dark-2);
  border-radius: 6px; padding: 3px 10px; font-size: 12px; cursor: pointer; margin-left: 6px; }
.ghost:disabled { color: #bbb; border-color: #e4e7eb; cursor: not-allowed; }
.rw-btn { border-color: #fdba74; color: #c2610c; }
.rw-btn:disabled { border-color: #f0dcc2; color: #c8b8a2; background: #fff7ed; }
.void-btn { border-color: #f3b0b0; color: #b34040; }
.paybox .d-row { display: flex; gap: 14px; padding: 8px 4px; border-bottom: 1px dashed #f0f2f4; font-size: 13px; }
.paybox .d-row span { width: 56px; color: #99a1a6; flex-shrink: 0; }
.paybox .fr { margin-top: 14px; }
.paytip { margin-top: 10px; font-size: 12px; color: #99a1a6; }
.fr { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.fr label { width: 62px; text-align: right; font-size: 13px; color: #647077; }
.detail .d-row { display: flex; gap: 14px; padding: 8px 4px; border-bottom: 1px dashed #f0f2f4; font-size: 13px; }
.detail .d-row span { width: 80px; color: #99a1a6; flex-shrink: 0; }
</style>
