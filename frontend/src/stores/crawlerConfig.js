import { defineStore } from 'pinia'
import { getCrawlIntervalConfig } from '@/api/sysConfig'

const CRAWL_INTERVAL_KEY = 'crawl_interval_minutes'
const DEFAULT_CRAWL_INTERVAL = 30

export const useCrawlerConfigStore = defineStore('crawlerConfig', {
  state: () => ({
    crawlIntervalMinutes: parseInt(localStorage.getItem(CRAWL_INTERVAL_KEY)) || DEFAULT_CRAWL_INTERVAL
  }),

  getters: {
    interval: (state) => state.crawlIntervalMinutes
  },

  actions: {
    async fetchCrawlIntervalConfig() {
      try {
        const data = await getCrawlIntervalConfig()
        if (data && typeof data.crawlIntervalMinutes === 'number') {
          this.crawlIntervalMinutes = data.crawlIntervalMinutes
          localStorage.setItem(CRAWL_INTERVAL_KEY, String(data.crawlIntervalMinutes))
        }
        return data
      } catch (e) {
        return null
      }
    },

    setInterval(minutes) {
      this.crawlIntervalMinutes = minutes
      localStorage.setItem(CRAWL_INTERVAL_KEY, String(minutes))
    },

    resetToDefault() {
      this.crawlIntervalMinutes = DEFAULT_CRAWL_INTERVAL
      localStorage.removeItem(CRAWL_INTERVAL_KEY)
    }
  }
})
