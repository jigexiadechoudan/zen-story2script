<script setup>
import { LogOut, Sparkles } from 'lucide-vue-next'
import { motion } from 'motion-v'
import StatusBadge from './StatusBadge.vue'

defineProps({
  currentUser: {
    type: Object,
    default: null
  },
  locale: {
    type: String,
    required: true
  },
  localeOptions: {
    type: Array,
    required: true
  },
  authText: {
    type: Object,
    required: true
  },
  text: {
    type: Object,
    required: true
  },
  statusBadges: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['update:locale', 'logout'])
</script>

<template>
  <motion.header
    class="app-header"
    :initial="{ opacity: 0, y: -18 }"
    :animate="{ opacity: 1, y: 0 }"
    :transition="{ duration: 0.55, ease: [0.22, 1, 0.36, 1] }"
  >
    <div class="brand-lockup">
      <span class="brand-sigil" aria-hidden="true">
        <Sparkles :size="18" />
      </span>
      <div>
        <p class="eyebrow">Zen Story2Script</p>
        <h1>{{ text.productName }}</h1>
        <p class="header-subtitle">{{ text.productSubtitle }}</p>
      </div>
    </div>

    <div class="header-tools">
      <div v-if="currentUser" class="status-row" :aria-label="text.connectionStatus">
        <StatusBadge
          v-for="badge in statusBadges"
          :key="`${badge.tone}-${badge.label}`"
          :tone="badge.tone"
          :label="badge.label"
          :detail="badge.detail"
          :icon="badge.icon"
        />
      </div>

      <div class="language-toggle" aria-label="Language">
        <button
          v-for="option in localeOptions"
          :key="option.value"
          type="button"
          :class="{ active: locale === option.value }"
          @click="emit('update:locale', option.value)"
        >
          {{ option.label }}
        </button>
      </div>

      <div v-if="currentUser" class="account-strip" :aria-label="authText.signedInAs">
        <span>{{ authText.signedInAs }}</span>
        <strong>{{ currentUser.displayName || currentUser.email }}</strong>
        <button type="button" class="icon-text-button compact" @click="emit('logout')">
          <LogOut :size="15" aria-hidden="true" />
          <span>{{ authText.logoutButton }}</span>
        </button>
      </div>
    </div>
  </motion.header>
</template>
