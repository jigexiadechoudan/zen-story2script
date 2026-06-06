<script setup>
import { AlertTriangle, CheckCircle2, Info, Wifi, WifiOff } from 'lucide-vue-next'

const props = defineProps({
  tone: {
    type: String,
    default: 'neutral'
  },
  label: {
    type: String,
    required: true
  },
  detail: {
    type: String,
    default: ''
  },
  icon: {
    type: String,
    default: 'info'
  }
})

const icons = {
  info: Info,
  success: CheckCircle2,
  warning: AlertTriangle,
  online: Wifi,
  offline: WifiOff
}
</script>

<template>
  <span class="status-badge" :class="`status-badge-${props.tone}`">
    <component :is="icons[props.icon] || Info" :size="15" aria-hidden="true" />
    <span>{{ props.label }}</span>
    <small v-if="props.detail">{{ props.detail }}</small>
  </span>
</template>
