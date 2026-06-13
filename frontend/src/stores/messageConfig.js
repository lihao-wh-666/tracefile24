import { defineStore } from 'pinia'
import { getMessageConfig } from '@/api/sysConfig'

const MESSAGE_DURATION_KEY = 'message_duration'
const DEFAULT_DURATION = 1500

export const useMessageConfigStore = defineStore('messageConfig', {
  state: () => ({
    messageDuration: parseInt(localStorage.getItem(MESSAGE_DURATION_KEY)) || DEFAULT_DURATION
  }),

  getters: {
    duration: (state) => state.messageDuration
  },

  actions: {
    async fetchMessageConfig() {
      try {
        const data = await getMessageConfig()
        if (data && typeof data.messageDuration === 'number') {
          this.messageDuration = data.messageDuration
          localStorage.setItem(MESSAGE_DURATION_KEY, String(data.messageDuration))
        }
        return data
      } catch (e) {
        return null
      }
    },

    setDuration(duration) {
      this.messageDuration = duration
      localStorage.setItem(MESSAGE_DURATION_KEY, String(duration))
    },

    resetToDefault() {
      this.messageDuration = DEFAULT_DURATION
      localStorage.removeItem(MESSAGE_DURATION_KEY)
    }
  }
})
