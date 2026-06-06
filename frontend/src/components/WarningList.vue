<script setup>
import { AlertTriangle } from 'lucide-vue-next'

defineProps({
  title: {
    type: String,
    required: true
  },
  warnings: {
    type: Array,
    default: () => []
  },
  warningUnit: {
    type: String,
    required: true
  },
  emptyText: {
    type: String,
    required: true
  }
})
</script>

<template>
  <article class="info-block warning-block">
    <div class="info-title">
      <h3>
        <AlertTriangle :size="17" aria-hidden="true" />
        <span>{{ title }}</span>
      </h3>
      <span>{{ warnings.length }} {{ warningUnit }}</span>
    </div>
    <ul v-if="warnings.length" class="warning-list">
      <li v-for="warning in warnings" :key="warning">{{ warning }}</li>
    </ul>
    <p v-else class="empty-text">{{ emptyText }}</p>
  </article>
</template>
