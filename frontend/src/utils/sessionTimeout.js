import { SESSION_TIMEOUT_CONFIG, getTimeoutConfig, validateTimeout } from '@/config/sessionTimeout'
import { useUserStore } from '@/stores/user'

class SessionTimeoutManager {
  constructor() {
    this.timeoutId = null
    this.warningTimeoutId = null
    this.checkIntervalId = null
    this.heartbeatIntervalId = null
    this.eventListeners = new Map()
    this.isInitialized = false
    this.isWarningShown = false
    this.callbacks = {
      onWarning: null,
      onTimeout: null,
      onActivity: null,
      onHeartbeat: null
    }
    this.config = getTimeoutConfig()
    this.lastActivityTime = this.getLastActivityTime()
  }

  init(options = {}) {
    if (this.isInitialized) {
      this.destroy()
    }

    this.callbacks = { ...this.callbacks, ...options.callbacks }

    const defaultConfig = getTimeoutConfig()
    const customConfig = options.config || {}
    this.config = {
      timeout: customConfig.timeout ?? defaultConfig.timeout,
      warningBefore: customConfig.warningBefore ?? defaultConfig.warningBefore
    }

    if (!validateTimeout(this.config.timeout)) {
      throw new Error('Invalid timeout configuration')
    }

    this.setupActivityListeners()
    this.setupStorageListener()
    this.startCheckInterval()

    this.updateLastActivityTime()
    this.scheduleTimeouts()

    this.isInitialized = true
  }

  setupActivityListeners() {
    const handleActivity = this.debounce(() => {
      this.handleUserActivity()
    }, 1000)

    SESSION_TIMEOUT_CONFIG.ACTIVITY_EVENTS.forEach(event => {
      document.addEventListener(event, handleActivity, true)
      this.eventListeners.set(event, handleActivity)
    })

    document.addEventListener('visibilitychange', () => {
      if (!document.hidden) {
        this.checkSessionStatus()
      }
    })
  }

  setupStorageListener() {
    const handleStorage = (e) => {
      if (e.key === SESSION_TIMEOUT_CONFIG.STORAGE_KEYS.LAST_ACTIVITY) {
        const newActivityTime = parseInt(e.newValue, 10)
        if (!isNaN(newActivityTime) && newActivityTime > this.lastActivityTime) {
          this.lastActivityTime = newActivityTime
          this.scheduleTimeouts()
          if (this.isWarningShown) {
            this.hideWarning()
          }
        }
      } else if (e.key === 'hot_event_token' && !e.newValue) {
        this.destroy()
      }
    }

    window.addEventListener('storage', handleStorage)
    this.eventListeners.set('storage', handleStorage)
  }

  startCheckInterval() {
    this.checkIntervalId = setInterval(() => {
      this.checkSessionStatus()
    }, 1000)
  }

  checkSessionStatus() {
    const now = Date.now()
    const elapsed = now - this.lastActivityTime
    const remaining = this.config.timeout - elapsed

    if (remaining <= 0) {
      this.handleTimeout()
    } else if (remaining <= this.config.warningBefore && !this.isWarningShown) {
      this.showWarning()
    }

    return remaining
  }

  getRemainingTime() {
    return Math.max(0, this.config.timeout - (Date.now() - this.lastActivityTime))
  }

  getLastActivityTime() {
    try {
      const stored = localStorage.getItem(SESSION_TIMEOUT_CONFIG.STORAGE_KEYS.LAST_ACTIVITY)
      const parsed = parseInt(stored, 10)
      if (!isNaN(parsed) && parsed > 0) {
        return parsed
      }
    } catch (e) {
      console.warn('Failed to get last activity time:', e)
    }
    return Date.now()
  }

  updateLastActivityTime(timestamp = Date.now()) {
    this.lastActivityTime = timestamp
    localStorage.setItem(
      SESSION_TIMEOUT_CONFIG.STORAGE_KEYS.LAST_ACTIVITY,
      timestamp.toString()
    )
  }

  handleUserActivity() {
    this.updateLastActivityTime()
    this.scheduleTimeouts()

    if (this.isWarningShown) {
      this.hideWarning()
    }

    if (this.callbacks.onActivity) {
      this.callbacks.onActivity({
        timestamp: this.lastActivityTime,
        remainingTime: this.getRemainingTime()
      })
    }
  }

  scheduleTimeouts() {
    this.clearTimeouts()

    const remaining = this.getRemainingTime()
    const warningTime = remaining - this.config.warningBefore

    if (warningTime > 0) {
      this.warningTimeoutId = setTimeout(() => {
        this.showWarning()
      }, warningTime)
    } else if (remaining > 0) {
      this.showWarning()
    }

    this.timeoutId = setTimeout(() => {
      this.handleTimeout()
    }, remaining)
  }

  clearTimeouts() {
    if (this.timeoutId) {
      clearTimeout(this.timeoutId)
      this.timeoutId = null
    }
    if (this.warningTimeoutId) {
      clearTimeout(this.warningTimeoutId)
      this.warningTimeoutId = null
    }
  }

  showWarning() {
    if (this.isWarningShown) return

    this.isWarningShown = true
    localStorage.setItem(SESSION_TIMEOUT_CONFIG.STORAGE_KEYS.WARNING_SHOWN, 'true')

    if (this.callbacks.onWarning) {
      this.callbacks.onWarning({
        remainingTime: this.getRemainingTime(),
        totalTimeout: this.config.timeout,
        warningBefore: this.config.warningBefore
      })
    }
  }

  hideWarning() {
    this.isWarningShown = false
    localStorage.removeItem(SESSION_TIMEOUT_CONFIG.STORAGE_KEYS.WARNING_SHOWN)
  }

  async handleTimeout() {
    this.clearTimeouts()

    if (this.callbacks.onTimeout) {
      await this.callbacks.onTimeout()
    }

    this.performSecureLogout()
  }

  async performSecureLogout() {
    try {
      const userStore = useUserStore()
      await userStore.logout()
    } catch (e) {
      console.warn('Logout error during session timeout:', e)
    } finally {
      this.clearSensitiveData()
      this.redirectToLogin()
    }
  }

  clearSensitiveData() {
    const sensitiveKeys = [
      'hot_event_token',
      'hot_event_user',
      SESSION_TIMEOUT_CONFIG.STORAGE_KEYS.LAST_ACTIVITY,
      SESSION_TIMEOUT_CONFIG.STORAGE_KEYS.WARNING_SHOWN
    ]

    sensitiveKeys.forEach(key => {
      try {
        localStorage.removeItem(key)
        sessionStorage.removeItem(key)
      } catch (e) {
        console.warn(`Failed to remove ${key}:`, e)
      }
    })

    if (window.caches) {
      caches.keys().then(keys => {
        keys.forEach(key => {
          if (key.includes('auth') || key.includes('session')) {
            caches.delete(key)
          }
        })
      }).catch(() => {})
    }

    if (navigator.credentials && navigator.credentials.preventSilentAccess) {
      navigator.credentials.preventSilentAccess().catch(() => {})
    }
  }

  redirectToLogin() {
    const loginUrl = '/login?timeout=1'
    if (window.location.pathname !== '/login') {
      window.location.replace(loginUrl)
    }
  }

  continueSession() {
    this.hideWarning()
    this.updateLastActivityTime()
    this.scheduleTimeouts()
  }

  async logoutSession() {
    this.clearTimeouts()
    await this.performSecureLogout()
    this.destroy()
  }

  setTimeout(timeout) {
    if (!validateTimeout(timeout)) {
      throw new Error(`Invalid timeout value. Must be between ${SESSION_TIMEOUT_CONFIG.MIN_TIMEOUT} and ${SESSION_TIMEOUT_CONFIG.MAX_TIMEOUT} ms`)
    }

    this.config.timeout = timeout
    this.config.warningBefore = Math.min(this.config.warningBefore, timeout / 2)
    this.scheduleTimeouts()

    localStorage.setItem(
      SESSION_TIMEOUT_CONFIG.STORAGE_KEYS.TIMEOUT_CONFIG,
      JSON.stringify(this.config)
    )
  }

  getConfig() {
    return { ...this.config }
  }

  isActive() {
    return this.isInitialized && this.getRemainingTime() > 0
  }

  debounce(fn, delay) {
    let timerId = null
    return (...args) => {
      if (timerId) {
        clearTimeout(timerId)
      }
      timerId = setTimeout(() => {
        fn.apply(this, args)
        timerId = null
      }, delay)
    }
  }

  destroy() {
    this.clearTimeouts()

    if (this.checkIntervalId) {
      clearInterval(this.checkIntervalId)
      this.checkIntervalId = null
    }

    if (this.heartbeatIntervalId) {
      clearInterval(this.heartbeatIntervalId)
      this.heartbeatIntervalId = null
    }

    this.eventListeners.forEach((handler, event) => {
      if (event === 'storage') {
        window.removeEventListener(event, handler)
      } else {
        document.removeEventListener(event, handler, true)
      }
    })
    this.eventListeners.clear()

    this.isInitialized = false
    this.isWarningShown = false

    localStorage.removeItem(SESSION_TIMEOUT_CONFIG.STORAGE_KEYS.WARNING_SHOWN)
    localStorage.removeItem(SESSION_TIMEOUT_CONFIG.STORAGE_KEYS.LAST_ACTIVITY)
  }
}

export { SessionTimeoutManager }

export const sessionTimeout = new SessionTimeoutManager()

export function useSessionTimeout(options = {}) {
  if (!sessionTimeout.isInitialized) {
    sessionTimeout.init(options)
  }
  return sessionTimeout
}

export default sessionTimeout
