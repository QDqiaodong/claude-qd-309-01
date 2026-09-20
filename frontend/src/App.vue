<template>
  <div class="shell">
    <main class="main">
      <div class="crumb">{{ currentLabel }}</div>
      <router-view />
    </main>
    <div class="fab" :class="{ open: open }" @click="open = !open">☰</div>
    <transition name="pop">
      <div class="ring" v-if="open">
        <div v-for="(m, i) in pages" :key="m.path" class="ri"
             :style="{ transform: `translateY(${-(i + 1) * 52}px)` }"
             :class="{ on: $route.path === m.path }" @click="go(m.path)">
          <span>{{ m.label }}</span>
        </div>
      </div>
    </transition>
  </div>
</template>

<script>
import { pages } from './router'

export default {
  name: 'App',
  data() {
    return { pages, open: false }
  },
  computed: {
    currentLabel() {
      const hit = pages.find((p) => p.path === this.$route.path)
      return hit ? hit.label : ''
    }
  },
  methods: {
    go(path) {
      this.open = false
      this.$router.push(path)
    }
  }
}
</script>

<style>
html, body { margin: 0; }
body { background: #f5f6f7; font-family: -apple-system, 'PingFang SC', sans-serif; }
.shell { min-height: 100vh; }
.main { padding: 20px 26px 96px; }
.crumb { font-size: 12px; color: #99a1a6; margin-bottom: 10px; }
.fab { position: fixed; right: 26px; bottom: 26px; width: 54px; height: 54px; border-radius: 50%;
  background: var(--el-color-primary); color: #fff; display: flex; align-items: center; justify-content: center;
  font-size: 22px; cursor: pointer; box-shadow: 0 6px 20px rgba(0,0,0,.22); z-index: 30; transition: .2s; }
.fab.open { transform: rotate(90deg); }
.ring { position: fixed; right: 26px; bottom: 26px; z-index: 29; }
.ri { position: absolute; right: 0; bottom: 0; width: 54px; height: 54px; display: flex;
  align-items: center; justify-content: center; }
.ri span { background: #fff; border-radius: 16px; padding: 5px 12px; font-size: 12px; white-space: nowrap;
  box-shadow: 0 3px 12px rgba(0,0,0,.14); color: #4a5559; cursor: pointer; }
.ri.on span { background: var(--el-color-primary); color: #fff; }
.pop-enter-active, .pop-leave-active { transition: opacity .2s; }
.pop-enter-from, .pop-leave-to { opacity: 0; }
</style>
