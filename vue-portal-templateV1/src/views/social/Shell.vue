<template>
  <div class="social-shell">
    <div class="hero">
      <div class="hero-copy">
        <div class="h1">悦动社交</div>
        <div class="h2">聊天、找搭子、晒打卡，一站式连接同城同目标运动伙伴</div>
      </div>
    </div>

    <div v-if="isFallbackMode" class="fallback-banner">
      {{ fallbackMessage }}
    </div>

    <div class="sub-nav">
      <RouterLink class="sub-link" to="/social/chat">聊天</RouterLink>
      <RouterLink class="sub-link" to="/social/match">搭子匹配</RouterLink>
      <RouterLink class="sub-link" to="/social/plaza">悦吧</RouterLink>
    </div>

    <RouterView />
  </div>
</template>

<script setup>
import { watch } from 'vue'
import { RouterLink, RouterView } from 'vue-router'
import { useRoute } from 'vue-router'
import { useSocialState } from './useSocialState'

const route = useRoute()
const { isFallbackMode, fallbackMessage, initSocialView } = useSocialState()

watch(
  () => route.path,
  async (path) => {
    await initSocialView(path)
  },
  { immediate: true },
)
</script>

<style scoped lang="scss">
.social-shell {
  width: 100%;
}

.hero {
  display: block;
  margin-bottom: 18px;
}

.hero-copy,
.sub-nav {
  border-radius: 22px;
  padding: 18px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.08);
}

.hero-copy {
  flex: 1;
}

.h1 {
  font-size: 30px;
  font-weight: 900;
}

.h2 {
  margin-top: 10px;
  color: rgba(255, 255, 255, 0.68);
}

.fallback-banner {
  margin-bottom: 18px;
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(255, 196, 61, 0.12);
  border: 1px solid rgba(255, 196, 61, 0.26);
  color: rgba(255, 238, 184, 0.96);
  font-size: 13px;
  line-height: 1.7;
}

.sub-nav {
  display: flex;
  gap: 12px;
  margin-bottom: 18px;
}

.sub-link {
  padding: 10px 16px;
  border-radius: 999px;
  color: rgba(255, 255, 255, 0.76);
  text-decoration: none;
  font-weight: 800;
}

.sub-link.router-link-active {
  color: #0a0a0a;
  background: linear-gradient(90deg, #24cf5f, #6dff9a);
}

@media (max-width: 1200px) {
  .hero {
    display: block;
  }
}
</style>
