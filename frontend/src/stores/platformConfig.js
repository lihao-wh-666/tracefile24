import { defineStore } from 'pinia'
import { getPlatformConfigs, enablePlatform, disablePlatform } from '@/api/multiCrawler'

const PLATFORM_CONFIG_KEY = 'platform_configs'

export const usePlatformConfigStore = defineStore('platformConfig', {
  state: () => ({
    platforms: [],
    loading: false
  }),

  getters: {
    enabledPlatforms: (state) => state.platforms.filter(p => p.enabled),
    enabledPlatformCodes: (state) => state.platforms.filter(p => p.enabled).map(p => p.code),
    isPlatformEnabled: (state) => (code) => {
      const platform = state.platforms.find(p => p.code === code)
      return platform ? platform.enabled : false
    },
    getPlatformName: (state) => (code) => {
      const platform = state.platforms.find(p => p.code === code)
      return platform ? platform.name : code
    }
  },

  actions: {
    async fetchPlatformConfigs() {
      this.loading = true
      try {
        const data = await getPlatformConfigs()
        this.platforms = data || []
        localStorage.setItem(PLATFORM_CONFIG_KEY, JSON.stringify(this.platforms))
        return this.platforms
      } catch (error) {
        const cached = localStorage.getItem(PLATFORM_CONFIG_KEY)
        if (cached) {
          try {
            this.platforms = JSON.parse(cached)
          } catch (e) {
            console.error('Failed to parse cached platform configs', e)
          }
        }
        return null
      } finally {
        this.loading = false
      }
    },

    async enablePlatform(code) {
      try {
        await enablePlatform(code)
        const platform = this.platforms.find(p => p.code === code)
        if (platform) {
          platform.enabled = true
        }
        localStorage.setItem(PLATFORM_CONFIG_KEY, JSON.stringify(this.platforms))
        return true
      } catch (error) {
        return false
      }
    },

    async disablePlatform(code) {
      try {
        await disablePlatform(code)
        const platform = this.platforms.find(p => p.code === code)
        if (platform) {
          platform.enabled = false
        }
        localStorage.setItem(PLATFORM_CONFIG_KEY, JSON.stringify(this.platforms))
        return true
      } catch (error) {
        return false
      }
    },

    setPlatformEnabled(code, enabled) {
      const platform = this.platforms.find(p => p.code === code)
      if (platform) {
        platform.enabled = enabled
        localStorage.setItem(PLATFORM_CONFIG_KEY, JSON.stringify(this.platforms))
      }
    },

    loadFromCache() {
      const cached = localStorage.getItem(PLATFORM_CONFIG_KEY)
      if (cached) {
        try {
          this.platforms = JSON.parse(cached)
        } catch (e) {
          console.error('Failed to parse cached platform configs', e)
        }
      }
    }
  }
})
