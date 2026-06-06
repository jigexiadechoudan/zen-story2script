<script setup>
import { Check, CircleDotDashed } from 'lucide-vue-next'
import { motion } from 'motion-v'

defineProps({
  title: {
    type: String,
    required: true
  },
  steps: {
    type: Array,
    required: true
  },
  emptyText: {
    type: String,
    required: true
  },
  stepUnit: {
    type: String,
    required: true
  },
  loading: {
    type: Boolean,
    required: true
  }
})
</script>

<template>
  <article class="info-block progress-card">
    <div class="info-title">
      <h3>{{ title }}</h3>
      <span>{{ steps.length }} {{ stepUnit }}</span>
    </div>
    <ol v-if="steps.length" class="trace-list">
      <motion.li
        v-for="(step, index) in steps"
        :key="`${index}-${step}`"
        :class="{ current: loading && index === steps.length - 1 }"
        :initial="{ opacity: 0, x: -10 }"
        :animate="{ opacity: 1, x: 0 }"
        :transition="{ duration: 0.28, delay: Math.min(index * 0.025, 0.16) }"
      >
        <span class="timeline-icon">
          <CircleDotDashed v-if="loading && index === steps.length - 1" :size="15" aria-hidden="true" />
          <Check v-else :size="15" aria-hidden="true" />
        </span>
        <span>{{ step }}</span>
      </motion.li>
    </ol>
    <p v-else class="empty-text">{{ emptyText }}</p>
  </article>
</template>
