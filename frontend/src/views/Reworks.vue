<template>
  <div class="pane">
    <header class="hd">
      <h2>回炉台</h2>
      <span class="sub">只能挂在已完成的洗车单上；先认原工位，原工位停用或满了才改派到别的空位。挂回炉必须先把二次领料（泡沫、毛巾）扣到账上。</span>
      <button class="prime" @click="openHang()">挂回炉</button>
    </header>
    <div class="tabs">
      <div class="tab" :class="{ on: tab === 'all' }" @click="tab = 'all'">全部 {{ items.length }}</div>
      <div v-for="s in STATES" :key="s" class="tab" :class="{ on: tab === s }" @click="tab = s">
        {{ s }} {{ countOf(s) }}
      </div>
      <div class="tab open">未结 {{ openCount }}</div>
    </div>
    <div class="table">
      <div class="row head">
        <span>回炉号</span><span>原单号</span><span>车牌</span><span>占用工位</span>
        <span>二次领料</span><span>原因</span><span>状态</span><span class="r">操作</span>
      </div>
      <div v-for="r in shown" :key="r.id" class="row" :class="{ open: r.reworkState !== '已验收' && r.reworkState !== '已作废' }">
        <span class="mono">{{ r.reworkNo }}</span>
        <span class="mono">{{ r.orderNo }}</span>
        <span>{{ r.plateNo }}</span>
        <span>
          {{ r.bayCode }}
          <el-tag v-if="r.reassigned" size="small" type="warning" effect="plain" class="tg">改派</el-tag>
        </span>
        <span class="use">
          <template v-if="r.supplyLines && r.supplyLines.length">
            <span v-for="(l, i) in r.supplyLines" :key="l.id" class="chip" :class="{ back: l.returned }">
              {{ l.supplyName }} ×{{ l.quantity }}<em v-if="l.returned">已退架</em>
            </span>
          </template>
          <span v-else class="muted">—</span>
        </span>
        <span class="reason">{{ r.reason || '—' }}</span>
        <span>
          <el-tag size="small" :type="tagType(r.reworkState)">{{ r.reworkState }}</el-tag>
        </span>
        <span class="r">
          <button v-if="r.reworkState === '待回炉'" class="ghost" @click="advance(r)">开始回炉</button>
          <button v-if="r.reworkState === '回炉中'" class="ghost" @click="advance(r)">验收通过</button>
          <button v-if="r.reworkState !== '已验收' && r.reworkState !== '已作废'" class="ghost warn" @click="abort(r)">
            验收不过·作废退料
          </button>
          <button v-if="r.reworkState !== '已验收' && r.reworkState !== '已作废'" class="ghost warn" @click="openReassign(r)">
            改派工位
          </button>
          <span v-if="r.reworkState === '已验收'" class="done">已结清，料已用掉</span>
          <span v-if="r.reworkState === '已作废'" class="backed">已作废，料已退架</span>
        </span>
      </div>
      <div class="blank" v-if="!shown.length">这一类下暂时没有回炉</div>
    </div>

    <!-- 挂回炉 -->
    <el-dialog v-model="hangDlg" title="给已完成的洗车单挂回炉" width="520px">
      <div class="fr">
        <label>原单</label>
        <el-select v-model="form.orderId" style="flex:1" placeholder="选一张已完成、未结回炉的单子"
                   @change="onOrderPick">
          <el-option v-for="o in hangableOrders" :key="o.id"
                     :label="o.orderNo + ' · ' + o.plateNo + ' · 原工位 ' + (bayCode(o.bayId) || '无')"
                     :value="o.id" />
        </el-select>
      </div>
      <div v-if="pickedOrigin" class="origin">
        <div>原工位：<b>{{ originBay ? originBay.bayCode + ' ' + originBay.bayName : '—' }}</b></div>
        <div class="why" v-if="originNote">{{ originNote }}</div>
      </div>
      <div class="fr">
        <label>回炉工位</label>
        <el-select v-model="form.bayId" style="flex:1" :placeholder="bayPlaceholder">
          <el-option v-for="b in selectableBays" :key="b.id"
                     :label="b.bayCode + ' ' + b.bayName + '（空 ' + freeSeats(b) + ' 位）'" :value="b.id" />
        </el-select>
      </div>
      <div class="rule">先认原工位；原工位停用或被占满时，才允许改派到别的空闲工位。</div>

      <!-- 二次领料：挂上时必须写清再拿的泡沫、毛巾各几件，先扣货架后挂单 -->
      <div class="pick-title">二次领料（客人挑毛病重做，得再拿一遍料）</div>
      <div class="pick-rule">仓管口径：料先从货架扣到账上才允许挂回炉；任一样现数不够，整笔领料失败、回炉也挂不上。</div>
      <div v-for="p in picks" :key="p.key" class="pick">
        <label>{{ p.label }}</label>
        <el-select v-model="form[p.supplyField]" style="flex:1" :placeholder="'选' + p.label">
          <el-option v-for="s in p.list" :key="s.id"
                     :label="s.supplyName + '（' + s.supplyCode + ' · 货架现数 ' + s.stock + ' ' + (s.unitText || '件') + '）'"
                     :value="s.id" />
        </el-select>
        <el-input-number v-model="form[p.qtyField]" :min="1" :step="1" :controls="false"
                         style="width: 92px" placeholder="件数" />
        <span class="unit">{{ unitOf(form[p.supplyField]) }}</span>
        <span class="lack" v-if="lackText(p)">货架只剩 {{ stockOf(form[p.supplyField]) }}，不够</span>
      </div>

      <div class="fr" style="margin-top:12px"><label>挑的毛病</label><el-input v-model="form.reason" type="textarea" :rows="2" /></div>
      <template #footer>
        <el-button @click="hangDlg = false">取消</el-button>
        <el-button type="primary" @click="submitHang">先扣料再挂上</el-button>
      </template>
    </el-dialog>

    <!-- 改派 -->
    <el-dialog v-model="moveDlg" :title="'改派回炉 ' + (moving ? moving.reworkNo : '')" width="440px">
      <div class="rule">回炉不会被拆掉，只能改派到没停用、还有空位的工位；选不到空位，本次改派失败。</div>
      <div class="fr">
        <label>新工位</label>
        <el-select v-model="moveBayId" style="flex:1" placeholder="选一个空闲工位">
          <el-option v-for="b in freeBays" :key="b.id"
                     :label="b.bayCode + ' ' + b.bayName + '（空 ' + freeSeats(b) + ' 位）'" :value="b.id" />
        </el-select>
      </div>
      <div v-if="!freeBays.length" class="why">现在一个空闲工位都没有，改派会失败：先腾个工位出来。</div>
      <template #footer>
        <el-button @click="moveDlg = false">取消</el-button>
        <el-button type="primary" :disabled="!moveBayId" @click="submitMove">改派</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { reworkApi, orderApi, bayApi, supplyApi } from '../api'

export default {
  name: 'Reworks',
  data() {
    return {
      items: [], orders: [], bays: [], supplies: [],
      tab: 'all',
      STATES: ['待回炉', '回炉中', '已验收', '已作废'],
      hangDlg: false, form: {},
      moveDlg: false, moving: null, moveBayId: null
    }
  },
  computed: {
    shown() {
      return this.tab === 'all' ? this.items : this.items.filter((r) => r.reworkState === this.tab)
    },
    openCount() {
      return this.items.filter((r) => r.reworkState !== '已验收' && r.reworkState !== '已作废').length
    },
    /** 二次领料就认这两样：名称带「泡沫」「毛巾」。 */
    foamSupplies() {
      return this.supplies.filter((s) => (s.supplyName || '').includes('泡沫'))
    },
    towelSupplies() {
      return this.supplies.filter((s) => (s.supplyName || '').includes('毛巾'))
    },
    picks() {
      return [
        { key: 'foam', label: '泡沫', supplyField: 'foamSupplyId', qtyField: 'foamQty', list: this.foamSupplies },
        { key: 'towel', label: '毛巾', supplyField: 'towelSupplyId', qtyField: 'towelQty', list: this.towelSupplies }
      ]
    },
    /** 能挂回炉的原单：已完成 + 没有未验收回炉。从来没回炉记录的老单照样在列，不灰掉。 */
    hangableOrders() {
      return this.orders.filter((o) => o.washState === '已完成' && !o.reworkOpen)
    },
    pickedOrigin() {
      return this.orders.find((o) => o.id === this.form.orderId) || null
    },
    originBay() {
      return this.pickedOrigin ? this.bays.find((b) => b.id === this.pickedOrigin.bayId) : null
    },
    /** 原工位能不能认上：没停用、没满。 */
    originUsable() {
      const b = this.originBay
      return !!b && b.bayState !== '停用' && this.freeSeats(b) > 0
    },
    originNote() {
      const b = this.originBay
      if (!b) return '原单没有工位记录，不能挂回炉'
      if (b.bayState === '停用') return '原工位已经停用，认不上，请改派到别的空闲工位'
      if (this.freeSeats(b) <= 0) return '原工位已经被别的车占满，请改派到别的空闲工位'
      return '原工位还空着，回炉得先认原工位'
    },
    bayPlaceholder() {
      if (!this.pickedOrigin) return '先选原单'
      return this.originUsable ? '默认认原工位' : '原工位认不上，改派一个空闲工位'
    },
    selectableBays() {
      // 原工位能用时只让选原工位；认不上时列出所有空闲（没停用、有空位）的工位。
      if (this.originUsable) return this.originBay ? [this.originBay] : []
      return this.freeBays
    },
    freeBays() {
      return this.bays.filter((b) => b.bayState !== '停用' && this.freeSeats(b) > 0)
    }
  },
  methods: {
    countOf(s) {
      return this.items.filter((r) => r.reworkState === s).length
    },
    tagType(s) {
      if (s === '已验收') return 'success'
      if (s === '回炉中') return 'warning'
      if (s === '已作废') return 'danger'
      return 'info'
    },
    bayCode(id) {
      const b = this.bays.find((x) => x.id === id)
      return b ? b.bayCode : ''
    },
    seatCount(b) {
      return b.seatCount && b.seatCount > 0 ? b.seatCount : 1
    },
    freeSeats(b) {
      const used = (b.occupiedSeats ?? 0)
      return Math.max(0, this.seatCount(b) - used)
    },
    stockOf(id) {
      const s = this.supplies.find((x) => x.id === id)
      return s ? s.stock : null
    },
    unitOf(id) {
      const s = this.supplies.find((x) => x.id === id)
      return s ? (s.unitText || '件') : ''
    },
    lackText(p) {
      const id = this.form[p.supplyField]
      const qty = Number(this.form[p.qtyField] || 0)
      if (!id || qty <= 0) return false
      const stock = this.stockOf(id)
      return stock !== null && stock < qty
    },
    async load() {
      const [rw, os, bs, ss] = await Promise.all([
        reworkApi.list(), orderApi.list(), bayApi.list(), supplyApi.list()
      ])
      this.items = rw
      this.orders = os
      this.bays = bs
      this.supplies = ss
    },
    openHang(orderId) {
      this.form = {
        foamSupplyId: this.foamSupplies[0]?.id ?? null,
        towelSupplyId: this.towelSupplies[0]?.id ?? null,
        foamQty: 1,
        towelQty: 1
      }
      this.hangDlg = true
      if (orderId) {
        this.$nextTick(() => {
          this.form.orderId = orderId
          this.onOrderPick()
        })
      }
    },
    onOrderPick() {
      // 选了原单先认原工位：认不上才留给前台改派。
      this.form.bayId = this.originUsable && this.originBay ? this.originBay.id : null
    },
    async submitHang() {
      if (!this.form.orderId) { this.$message.warning('先选一张已完成的原单'); return }
      if (!this.originUsable && !this.form.bayId) {
        this.$message.warning('原工位认不上了，得改派一个空闲工位')
        return
      }
      if (!this.form.foamSupplyId || !this.form.towelSupplyId) {
        this.$message.warning('二次领料得把泡沫、毛巾都选上，各写清再拿几件')
        return
      }
      if (this.form.foamSupplyId === this.form.towelSupplyId) {
        this.$message.warning('泡沫和毛巾不能是同一样，各选各的')
        return
      }
      const foamQty = Number(this.form.foamQty || 0)
      const towelQty = Number(this.form.towelQty || 0)
      if (foamQty <= 0 || towelQty <= 0) {
        this.$message.warning('泡沫、毛巾再拿几件都得写大于 0 的数')
        return
      }
      // 前端先看一眼货架，真正扣减在后端同一事务里：不够整笔失败，不先挂后补。
      for (const p of this.picks) {
        if (this.lackText(p)) {
          this.$message.warning(p.label + '货架现数不够，整笔领料会失败、回炉也挂不上，先补货再挂')
          return
        }
      }
      const payload = {
        orderId: this.form.orderId,
        bayId: this.form.bayId,
        reason: this.form.reason,
        supplyLines: [
          { supplyId: this.form.foamSupplyId, quantity: foamQty },
          { supplyId: this.form.towelSupplyId, quantity: towelQty }
        ]
      }
      try {
        await reworkApi.create(payload)
        this.hangDlg = false
        await this.load()
        this.$message.success('二次领料已从货架扣到账上，回炉已挂上')
      } catch (e) { this.$message.error(e.message) }
    },
    async advance(r) {
      try {
        await reworkApi.advance(r.id)
        await this.load()
        this.$message.success(r.reworkState === '待回炉' ? '已开始回炉，车位算占用' : '已验收通过，二次领的料算用掉、不退架')
      } catch (e) { this.$message.error(e.message) }
    },
    async abort(r) {
      try {
        await this.$confirm(
          '客人仍不满意、这条回炉验收不过？作废后二次领的泡沫、毛巾会整笔退回货架，回炉也不能再继续。',
          '验收不过·作废退料',
          { type: 'warning', confirmButtonText: '作废并退料', cancelButtonText: '再做做看' }
        )
      } catch {
        return
      }
      try {
        await reworkApi.abort(r.id)
        await this.load()
        this.$message.success('回炉已作废，二次领料整笔退回货架')
      } catch (e) { this.$message.error(e.message) }
    },
    openReassign(r) {
      this.moving = r
      this.moveBayId = null
      this.moveDlg = true
    },
    async submitMove() {
      if (!this.moveBayId) { this.$message.warning('得选一个空闲工位'); return }
      try {
        await reworkApi.reassign(this.moving.id, this.moveBayId)
        this.moveDlg = false
        await this.load()
        this.$message.success('已改派，回炉状态没变')
      } catch (e) { this.$message.error(e.message) }
    }
  },
  mounted() {
    const preOrderId = Number(this.$route.query.orderId)
    const preTab = this.$route.query.tab
    this.load().then(() => {
      if (preTab) this.tab = String(preTab)
      if (preOrderId && this.hangableOrders.some((o) => o.id === preOrderId)) {
        this.openHang(preOrderId)
      }
    })
  }
}
</script>

<style scoped>
.hd { display: flex; align-items: center; gap: 14px; margin-bottom: 14px; }
.hd h2 { margin: 0; font-size: 20px; }
.sub { flex: 1; color: #99a1a6; font-size: 12px; }
.prime { background: var(--el-color-primary); color: #fff; border: none; border-radius: 8px;
  padding: 8px 18px; font-size: 13px; cursor: pointer; }
.tabs { display: flex; gap: 8px; margin-bottom: 12px; flex-wrap: wrap; }
.tab { padding: 6px 16px; border-radius: 16px; background: #fff; border: 1px solid #e9edef;
  font-size: 12px; color: #647077; cursor: pointer; }
.tab.on { background: var(--el-color-primary); color: #fff; border-color: var(--el-color-primary); }
.tab.open { background: #fff7ed; border-color: #fdba74; color: #c2610c; }
.table { background: #fff; border: 1px solid #e9edef; border-radius: 12px; overflow: hidden; }
.row { display: grid; grid-template-columns: 84px 88px 92px 120px 200px 1fr 84px 230px; gap: 8px;
  align-items: center; padding: 11px 14px; border-bottom: 1px solid #f3f6f7; font-size: 13px; }
.row.head { background: #f7f9fa; color: #99a1a6; font-size: 12px; }
.row.open { background: #fffaf3; }
.mono { font-family: ui-monospace, Menlo, monospace; color: #99a1a6; font-size: 12px; }
.reason { color: #647077; }
.r { text-align: right; }
.tg { margin-left: 4px; }
.done { color: #8fbf95; font-size: 12px; }
.backed { color: #c0392b; font-size: 12px; }
.muted { color: #bbb; }
.use { display: flex; flex-wrap: wrap; gap: 4px; }
.chip { display: inline-flex; align-items: center; gap: 3px; background: #eef4ff; color: #30568f;
  border-radius: 10px; padding: 2px 8px; font-size: 12px; white-space: nowrap; }
.chip em { font-style: normal; color: #c0392b; margin-left: 2px; }
.chip.back { background: #fdecec; color: #c0392b; text-decoration: line-through; }
.blank { color: #bbb; padding: 30px; text-align: center; }
.ghost { background: #fff; border: 1px solid var(--el-color-primary-light-7); color: var(--el-color-primary-dark-2);
  border-radius: 6px; padding: 3px 10px; font-size: 12px; cursor: pointer; margin-left: 6px; }
.ghost.warn { border-color: #fdba74; color: #c2610c; }
.fr { display: flex; align-items: flex-start; gap: 10px; margin-bottom: 12px; }
.fr label { width: 70px; text-align: right; font-size: 13px; color: #647077; padding-top: 6px; flex-shrink: 0; }
.origin { background: #f7f9fa; border-radius: 8px; padding: 10px 12px; margin: 0 0 12px 80px;
  font-size: 13px; color: #647077; }
.origin .why { color: #c2610c; margin-top: 4px; font-size: 12px; }
.rule { background: #fff7ed; color: #c2610c; font-size: 12px; border-radius: 8px;
  padding: 8px 12px; margin-bottom: 12px; }
.pick-title { font-size: 13px; font-weight: 600; color: #4a5559; margin: 4px 0 6px; }
.pick-rule { background: #fff7ed; color: #c2610c; font-size: 12px; border-radius: 8px;
  padding: 8px 12px; margin-bottom: 12px; }
.pick { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; }
.pick label { width: 44px; text-align: right; font-size: 13px; color: #647077; flex-shrink: 0; }
.pick .unit { font-size: 12px; color: #99a1a6; width: 28px; }
.pick .lack { font-size: 12px; color: #c0392b; }
</style>
