<script setup>
import { WandSparkles } from 'lucide-vue-next'

defineProps({
  loading: {
    type: Boolean,
    required: true
  },
  disabled: {
    type: Boolean,
    default: false
  },
  idleLabel: {
    type: String,
    required: true
  },
  loadingLabel: {
    type: String,
    required: true
  }
})
</script>

<template>
  <button class="primary-button generate-button" type="submit" :class="{ loading }" :disabled="disabled || loading">
    <span class="generate-orbit" aria-hidden="true">
      <i></i>
      <WandSparkles :size="18" />
    </span>
    <span>{{ loading ? loadingLabel : idleLabel }}</span>
  </button>
</template>
