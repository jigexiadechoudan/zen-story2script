<script setup>
import { Film, KeyRound, LogIn, Mail, UserRound } from 'lucide-vue-next'
import { motion } from 'motion-v'

defineProps({
  authForm: {
    type: Object,
    required: true
  },
  authText: {
    type: Object,
    required: true
  },
  authMode: {
    type: String,
    required: true
  },
  isRegistering: {
    type: Boolean,
    required: true
  },
  authLoading: {
    type: Boolean,
    required: true
  },
  authSubmitLabel: {
    type: String,
    required: true
  },
  authErrorMessage: {
    type: String,
    default: ''
  },
  authActionMessage: {
    type: String,
    default: ''
  },
  checking: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['submit', 'switch-mode'])
</script>

<template>
  <section v-if="checking" class="auth-stage auth-stage-compact" role="status">
    <motion.div
      class="studio-panel auth-panel auth-panel-compact"
      :initial="{ opacity: 0, y: 14 }"
      :animate="{ opacity: 1, y: 0 }"
    >
      <p class="section-kicker">{{ authText.kicker }}</p>
      <h2>{{ authText.checking }}</h2>
    </motion.div>
  </section>

  <section v-else class="auth-stage" aria-labelledby="auth-title">
    <motion.div
      class="auth-visual"
      aria-hidden="true"
      :initial="{ opacity: 0, x: -18 }"
      :animate="{ opacity: 1, x: 0 }"
      :transition="{ duration: 0.65, ease: [0.22, 1, 0.36, 1] }"
    >
      <div class="auth-mark">
        <span>S2S</span>
      </div>
      <div class="auth-flow-card auth-flow-card-one">
        <Film :size="18" />
        <span>{{ authText.flowNovel }}</span>
      </div>
      <div class="auth-flow-card auth-flow-card-two">
        <span>{{ authText.flowAgent }}</span>
      </div>
      <div class="auth-frame-stack">
        <span></span>
        <span></span>
        <span></span>
        <span></span>
      </div>
      <div class="auth-index">
        <span></span>
        <span></span>
        <span></span>
      </div>
    </motion.div>

    <motion.div
      class="studio-panel auth-panel"
      :initial="{ opacity: 0, x: 20 }"
      :animate="{ opacity: 1, x: 0 }"
      :transition="{ duration: 0.65, delay: 0.08, ease: [0.22, 1, 0.36, 1] }"
    >
      <div class="panel-heading auth-heading">
        <div>
          <p class="section-kicker">{{ authText.kicker }}</p>
          <h2 id="auth-title">{{ authText.title }}</h2>
        </div>
        <div class="auth-tabs" role="tablist" aria-label="Authentication mode">
          <button
            type="button"
            :class="{ active: authMode === 'login' }"
            @click="emit('switch-mode', 'login')"
          >
            {{ authText.loginTab }}
          </button>
          <button
            type="button"
            :class="{ active: authMode === 'register' }"
            @click="emit('switch-mode', 'register')"
          >
            {{ authText.registerTab }}
          </button>
        </div>
      </div>

      <form class="auth-form" @submit.prevent="emit('submit')">
        <label class="field with-icon">
          <span>{{ authText.emailLabel }}</span>
          <Mail :size="17" aria-hidden="true" />
          <input v-model="authForm.email" type="text" :placeholder="authText.emailPlaceholder" autocomplete="email" />
        </label>

        <label v-if="isRegistering" class="field with-icon">
          <span>{{ authText.displayNameLabel }}</span>
          <UserRound :size="17" aria-hidden="true" />
          <input v-model="authForm.displayName" type="text" :placeholder="authText.displayNamePlaceholder" autocomplete="name" />
        </label>

        <label class="field with-icon">
          <span>{{ authText.passwordLabel }}</span>
          <KeyRound :size="17" aria-hidden="true" />
          <input v-model="authForm.password" type="password" :placeholder="authText.passwordPlaceholder" autocomplete="current-password" />
        </label>

        <label v-if="isRegistering" class="field with-icon">
          <span>{{ authText.inviteCodeLabel }}</span>
          <KeyRound :size="17" aria-hidden="true" />
          <input v-model="authForm.inviteCode" type="text" :placeholder="authText.inviteCodePlaceholder" autocomplete="off" />
        </label>

        <div v-if="authErrorMessage" class="message error" role="alert">
          {{ authErrorMessage }}
        </div>

        <div v-if="authActionMessage" class="message success" role="status">
          {{ authActionMessage }}
        </div>

        <button class="primary-button" type="submit" :disabled="authLoading">
          <span>{{ authSubmitLabel }}</span>
          <LogIn :size="17" aria-hidden="true" />
        </button>
      </form>
    </motion.div>
  </section>
</template>
