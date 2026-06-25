import { ref } from 'vue'
import { defineStore } from 'pinia'

export const useIndexStore = defineStore('indexStore', () => {
  const isCollapse = ref(false)
  const refreshTick = ref(0)

  function toggleCollapse() {
    isCollapse.value = !isCollapse.value
  }

  function setCollapse(collapse) {
    isCollapse.value = collapse
  }

  function bumpRefresh() {
    refreshTick.value += 1
  }

  return { 
    isCollapse,
    refreshTick,
    toggleCollapse,
    setCollapse,
    bumpRefresh,
  } 
})
