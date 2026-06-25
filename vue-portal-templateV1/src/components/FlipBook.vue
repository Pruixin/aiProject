<template>
  <div class="flip-book-container" @mousedown="startSwipe" @touchstart="startSwipe">
    <div class="book" :style="bookStyle">
      <div
        v-for="(page, index) in pages"
        :key="index"
        class="page"
        :class="{ locked: index >= maxEnabledPages && index !== 0, flipped: index < currentPage }"
        :style="{ zIndex: pages.length - index }"
      >
        <div class="page-content front">
          <slot :name="'page-' + index" :page="page" :index="index">
            <div class="default-page">
              <h3>Day {{ index + 1 }}</h3>
              <p>{{ page.content }}</p>
            </div>
          </slot>
          <div class="page-number">{{ index + 1 }}</div>
        </div>
        <div class="page-content back">
          <!-- Back of the page can be empty or have some design -->
          <div class="page-number">{{ index + 1 }}</div>
        </div>
      </div>
    </div>

    <div v-if="showControls" class="controls">
      <el-button @click="prevPage" :disabled="currentPage === 0">上一页</el-button>
      <span>{{ currentPage + 1 }} / {{ pages.length }}</span>
      <el-button @click="nextPage" :disabled="currentPage === pages.length">下一页</el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue';

const props = defineProps({
  pages: {
    type: Array,
    default: () => []
  },
  showControls: {
    type: Boolean,
    default: false,
  },
  maxEnabledPages: {
    type: Number,
    default: 999,
  },
});

const currentPage = ref(0);

const bookStyle = computed(() => {
  return {
    // Basic book styles
  };
});

const nextPage = () => {
  const maxIndex = Math.min(props.pages.length - 1, Math.max(0, props.maxEnabledPages - 1));
  if (currentPage.value < maxIndex) {
    currentPage.value++;
  }
};

const prevPage = () => {
  if (currentPage.value > 0) {
    currentPage.value--;
  }
};

// Swipe handling
let startX = 0;
let isSwiping = false;
const startSwipe = (e) => {
  startX = e.type === 'mousedown' ? e.clientX : e.touches[0].clientX;
  isSwiping = true;
  window.addEventListener(e.type === 'mousedown' ? 'mousemove' : 'touchmove', handleSwipe);
  window.addEventListener(e.type === 'mousedown' ? 'mouseup' : 'touchend', stopSwipe);
};

const handleSwipe = (e) => {
  if (!isSwiping) return;
  const currentX = e.type === 'mousemove' ? e.clientX : e.touches[0].clientX;
  const diffX = currentX - startX;

  if (Math.abs(diffX) > 100) {
    if (diffX < 0) {
      nextPage();
    } else {
      prevPage();
    }
    stopSwipe(e);
  }
};

const stopSwipe = (e) => {
  isSwiping = false;
  window.removeEventListener('mousemove', handleSwipe);
  window.removeEventListener('touchmove', handleSwipe);
  window.removeEventListener('mouseup', stopSwipe);
  window.removeEventListener('touchend', stopSwipe);
};
</script>

<style scoped lang="scss">
.flip-book-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-start;
  perspective: 1500px;
  width: 100%;
  height: 100%;
  min-height: 82vh;
  user-select: none;
}

.book {
  position: relative;
  width: min(1280px, 96vw);
  height: min(860px, 82vh);
  transition: transform 0.5s;
  transform-style: preserve-3d;
}

.page {
  position: absolute;
  width: 100%;
  height: 100%;
  top: 0;
  left: 0;
  transform-origin: left center;
  transition: transform 0.6s cubic-bezier(0.645, 0.045, 0.355, 1);
  transform-style: preserve-3d;
  cursor: grab;

  &.flipped {
    transform: rotateY(-180deg);
  }

  &.locked {
    pointer-events: none;
    filter: grayscale(0.25) brightness(0.8);
  }
}

.page-content {
  position: absolute;
  width: 100%;
  height: 100%;
  top: 0;
  left: 0;
  backface-visibility: hidden;
  background: #1a1a1a;
  color: #fff;
  padding: 30px;
  box-shadow: inset 3px 0 10px rgba(0,0,0,0.5), 5px 5px 15px rgba(0,0,0,0.5);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border-radius: 0 12px 12px 0;

  &.front {
    z-index: 2;
  }

  &.back {
    transform: rotateY(180deg);
    background: #151515;
    z-index: 1;
    border-radius: 12px 0 0 12px;
  }
}

.page-number {
  position: absolute;
  bottom: 10px;
  right: 15px;
  font-size: 12px;
  color: #999;
}

.back .page-number {
  right: auto;
  left: 15px;
}

.controls {
  margin-top: 20px;
  display: flex;
  align-items: center;
  gap: 20px;
}

.default-page {
  text-align: center;
}
</style>
