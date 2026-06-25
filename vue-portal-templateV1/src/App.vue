<script setup>
import { RouterView } from 'vue-router'
</script>

<template>
  <div id="app-container">
    <RouterView />
  </div>
</template>

<style>
body {
  margin: 0;
  padding: 0;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

#app-container {
  width: 100%;
  min-height: 100vh;
}
</style>
