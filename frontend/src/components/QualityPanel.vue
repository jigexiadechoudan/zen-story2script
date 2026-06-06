<script setup>
import { BadgeCheck } from 'lucide-vue-next'

defineProps({
  title: {
    type: String,
    required: true
  },
  entries: {
    type: Array,
    required: true
  },
  schemaVersion: {
    type: String,
    default: '1.0'
  },
  emptyText: {
    type: String,
    required: true
  }
})
</script>

<template>
  <article class="info-block quality-card">
    <div class="info-title">
      <h3>
        <BadgeCheck :size="17" aria-hidden="true" />
        <span>{{ title }}</span>
      </h3>
      <span>v{{ schemaVersion }}</span>
    </div>
    <dl v-if="entries.length" class="metric-list">
      <template v-for="entry in entries" :key="entry.key">
        <dt>{{ entry.label }}</dt>
        <dd>{{ entry.value }}</dd>
      </template>
    </dl>
    <p v-else class="empty-text">{{ emptyText }}</p>
  </article>
</template>
