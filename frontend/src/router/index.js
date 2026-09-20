import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/', redirect: '/bays' },
  { path: '/bays', component: () => import('../views/Bays.vue'), meta: { label: '工位' } },
  { path: '/orders', component: () => import('../views/Orders.vue'), meta: { label: '洗车单' } },
  { path: '/reworks', component: () => import('../views/Reworks.vue'), meta: { label: '回炉台' } },
  { path: '/supplies', component: () => import('../views/Supplies.vue'), meta: { label: '耗材' } },
  { path: '/cards', component: () => import('../views/Cards.vue'), meta: { label: '会员卡' } }
]

export const pages = routes.filter((r) => r.meta).map((r) => ({ path: r.path, label: r.meta.label }))

export default createRouter({ history: createWebHistory(), routes })
