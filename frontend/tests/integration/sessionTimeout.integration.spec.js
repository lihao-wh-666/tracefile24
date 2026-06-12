import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { SESSION_TIMEOUT_CONFIG, getTimeoutConfig, setTimeoutConfig, resetTimeoutConfig } from '@/config/sessionTimeout'

vi.mock('@/stores/user', () => ({
  useUserStore: vi.fn().mockReturnValue({
    logout: vi.fn().mockResolvedValue(),
    isLoggedIn: true,
    token: 'test-token',
    user: { id: 1, username: 'testuser' }
  })
}))

import sessionTimeout, { SessionTimeoutManager, useSessionTimeout } from '@/utils/sessionTimeout'
import { useUserStore } from '@/stores/user'

describe('Session Timeout Integration Tests', () => {
  let manager

  beforeEach(() => {
    localStorage.clear()
    sessionStorage.clear()
    vi.clearAllTimers()
    vi.useFakeTimers()

    const userStore = useUserStore()
    vi.mocked(userStore.logout).mockClear()
    vi.mocked(useUserStore).mockClear()

    resetTimeoutConfig()
    manager = new SessionTimeoutManager()
  })

  afterEach(() => {
    if (manager && manager.isInitialized) {
      manager.destroy()
    }
    if (sessionTimeout.isInitialized) {
      sessionTimeout.destroy()
    }
    resetTimeoutConfig()
    vi.useRealTimers()
  })

  describe('Config Integration', () => {
    it('should use stored config when initializing manager', () => {
      const customConfig = {
        timeout: 15 * 60 * 1000,
        warningBefore: 2 * 60 * 1000
      }
      setTimeoutConfig(customConfig)

      manager.init()

      expect(manager.config.timeout).toBe(customConfig.timeout)
      expect(manager.config.warningBefore).toBe(customConfig.warningBefore)
    })

    it('should persist config changes made via manager', () => {
      manager.init()
      const newTimeout = 45 * 60 * 1000
      manager.setTimeout(newTimeout)

      const stored = getTimeoutConfig()
      expect(stored.timeout).toBe(newTimeout)
    })

    it('should validate config when setting via manager', () => {
      manager.init()
      expect(() => {
        manager.setTimeout(-1000)
      }).toThrow('Invalid timeout value')
    })

    it('should fallback to default config when stored config is invalid', () => {
      localStorage.setItem(
        SESSION_TIMEOUT_CONFIG.STORAGE_KEYS.TIMEOUT_CONFIG,
        JSON.stringify({ timeout: -1000, warningBefore: 60000 })
      )

      manager.init()

      expect(manager.config.timeout).toBe(SESSION_TIMEOUT_CONFIG.DEFAULT_TIMEOUT)
    })
  })

  describe('Full Session Lifecycle', () => {
    it('should complete full session lifecycle from init to timeout', async () => {
      const onWarningSpy = vi.fn()
      const onTimeoutSpy = vi.fn()
      const userStore = useUserStore()

      manager.init({
        config: { timeout: 60000, warningBefore: 30000 },
        callbacks: {
          onWarning: onWarningSpy,
          onTimeout: onTimeoutSpy
        }
      })

      expect(manager.isInitialized).toBe(true)
      expect(manager.isActive()).toBe(true)

      vi.advanceTimersByTime(29999)
      expect(onWarningSpy).not.toHaveBeenCalled()

      vi.advanceTimersByTime(2)
      expect(onWarningSpy).toHaveBeenCalled()
      expect(manager.isWarningShown).toBe(true)

      vi.advanceTimersByTime(29998)
      expect(onTimeoutSpy).not.toHaveBeenCalled()

      vi.advanceTimersByTime(3)
      expect(onTimeoutSpy).toHaveBeenCalled()

      if (manager.checkIntervalId) {
        clearInterval(manager.checkIntervalId)
        manager.checkIntervalId = null
      }

      await vi.runAllTicks()
      expect(userStore.logout).toHaveBeenCalled()
    })

    it('should reset session when user continues', () => {
      const onWarningSpy = vi.fn()

      manager.init({
        config: { timeout: 60000, warningBefore: 30000 },
        callbacks: {
          onWarning: onWarningSpy
        }
      })

      vi.advanceTimersByTime(30000)
      expect(onWarningSpy).toHaveBeenCalled()
      expect(manager.isWarningShown).toBe(true)

      manager.continueSession()

      expect(manager.isWarningShown).toBe(false)
      expect(manager.getRemainingTime()).toBeCloseTo(60000, -3)
    })

    it('should extend session on user activity', () => {
      manager.init({
        config: { timeout: 60000, warningBefore: 30000 }
      })

      vi.advanceTimersByTime(35000)
      const remainingBefore = manager.getRemainingTime()

      document.dispatchEvent(new Event('mousedown', { bubbles: true }))
      vi.advanceTimersByTime(1000)

      const remainingAfter = manager.getRemainingTime()
      expect(remainingAfter).toBeGreaterThan(remainingBefore)
    })
  })

  describe('Security Features Integration', () => {
    it('should clear all sensitive data from both localStorage and sessionStorage', async () => {
      const testData = {
        token: 'secret-jwt-token-12345',
        user: JSON.stringify({ id: 1, username: 'admin', email: 'admin@test.com', role: 'ADMIN' }),
        activity: Date.now().toString(),
        warning: 'true'
      }

      localStorage.setItem('hot_event_token', testData.token)
      localStorage.setItem('hot_event_user', testData.user)
      localStorage.setItem(SESSION_TIMEOUT_CONFIG.STORAGE_KEYS.LAST_ACTIVITY, testData.activity)
      localStorage.setItem(SESSION_TIMEOUT_CONFIG.STORAGE_KEYS.WARNING_SHOWN, testData.warning)

      sessionStorage.setItem('hot_event_token', testData.token)
      sessionStorage.setItem('hot_event_user', testData.user)

      manager.init()
      await manager.performSecureLogout()

      expect(localStorage.getItem('hot_event_token')).toBeNull()
      expect(localStorage.getItem('hot_event_user')).toBeNull()
      expect(localStorage.getItem(SESSION_TIMEOUT_CONFIG.STORAGE_KEYS.LAST_ACTIVITY)).toBeNull()
      expect(localStorage.getItem(SESSION_TIMEOUT_CONFIG.STORAGE_KEYS.WARNING_SHOWN)).toBeNull()

      expect(sessionStorage.getItem('hot_event_token')).toBeNull()
      expect(sessionStorage.getItem('hot_event_user')).toBeNull()
    })

    it('should use location.replace to prevent back button access', async () => {
      window.location.pathname = '/sensitive-data-page'

      manager.init()
      await manager.performSecureLogout()

      expect(window.location.replace).toHaveBeenCalledWith('/login?timeout=1')
      expect(window.location.assign).not.toHaveBeenCalled()
    })

    it('should call preventSilentAccess for credential management', async () => {
      manager.init()
      await manager.performSecureLogout()

      expect(navigator.credentials.preventSilentAccess).toHaveBeenCalled()
    })
  })

  describe('Cross-tab Synchronization', () => {
    let manager2

    beforeEach(() => {
      manager2 = new SessionTimeoutManager()
    })

    afterEach(() => {
      if (manager2 && manager2.isInitialized) {
        manager2.destroy()
      }
    })

    it('should sync activity across tabs via localStorage', () => {
      manager.init({
        config: { timeout: 60000, warningBefore: 30000 }
      })
      manager2.init({
        config: { timeout: 60000, warningBefore: 30000 }
      })

      vi.advanceTimersByTime(35000)
      expect(manager.isWarningShown).toBe(true)

      const newActivityTime = Date.now()
      manager2.updateLastActivityTime(newActivityTime)

      window.dispatchEvent(new StorageEvent('storage', {
        key: SESSION_TIMEOUT_CONFIG.STORAGE_KEYS.LAST_ACTIVITY,
        newValue: newActivityTime.toString(),
        oldValue: (newActivityTime - 1000).toString()
      }))

      expect(manager.isWarningShown).toBe(false)
      expect(manager.lastActivityTime).toBe(newActivityTime)
    })

    it('should destroy all managers when token is removed', () => {
      manager.init()
      manager2.init()

      expect(manager.isInitialized).toBe(true)
      expect(manager2.isInitialized).toBe(true)

      window.dispatchEvent(new StorageEvent('storage', {
        key: 'hot_event_token',
        newValue: null,
        oldValue: 'test-token'
      }))

      expect(manager.isInitialized).toBe(false)
      expect(manager2.isInitialized).toBe(false)
    })
  })

  describe('Edge Cases', () => {
    it('should handle corrupted localStorage data gracefully', () => {
      localStorage.setItem(SESSION_TIMEOUT_CONFIG.STORAGE_KEYS.LAST_ACTIVITY, 'not-a-number')
      localStorage.setItem(SESSION_TIMEOUT_CONFIG.STORAGE_KEYS.TIMEOUT_CONFIG, 'invalid-json')

      expect(() => {
        manager.init()
      }).not.toThrow()

      expect(manager.isInitialized).toBe(true)
      expect(manager.config.timeout).toBe(SESSION_TIMEOUT_CONFIG.DEFAULT_TIMEOUT)
    })

    it('should handle localStorage quota exceeded gracefully', () => {
      const originalSetItem = localStorage.setItem
      let callCount = 0

      localStorage.setItem = vi.fn().mockImplementation((key, value) => {
        callCount++
        if (callCount > 1) {
          throw new Error('QuotaExceededError')
        }
        originalSetItem.call(localStorage, key, value)
      })

      expect(() => {
        manager.init()
      }).not.toThrow()

      expect(manager.isInitialized).toBe(true)

      localStorage.setItem = originalSetItem
    })

    it('should not redirect when already on login page', async () => {
      window.location.pathname = '/login'
      window.location.replace.mockClear()

      manager.init()
      await manager.performSecureLogout()

      expect(window.location.replace).not.toHaveBeenCalled()
    })

    it('should check session status when tab becomes visible', () => {
      manager.init({
        config: { timeout: 60000, warningBefore: 30000 }
      })

      Object.defineProperty(document, 'hidden', {
        value: true,
        writable: true,
        configurable: true
      })

      vi.advanceTimersByTime(60000)

      Object.defineProperty(document, 'hidden', {
        value: false,
        writable: true,
        configurable: true
      })

      const checkStatusSpy = vi.spyOn(manager, 'checkSessionStatus')
      document.dispatchEvent(new Event('visibilitychange'))

      expect(checkStatusSpy).toHaveBeenCalled()
    })
  })

  describe('Singleton Instance', () => {
    it('should return same instance via default export', () => {
      expect(sessionTimeout).toBeInstanceOf(SessionTimeoutManager)
    })

    it('should initialize singleton via useSessionTimeout', () => {
      expect(sessionTimeout.isInitialized).toBe(false)

      const instance = useSessionTimeout()

      expect(instance).toBe(sessionTimeout)
      expect(sessionTimeout.isInitialized).toBe(true)

      sessionTimeout.destroy()
    })

    it('should not reinitialize if already initialized', () => {
      sessionTimeout.init()
      const initSpy = vi.spyOn(sessionTimeout, 'init')

      useSessionTimeout()

      expect(initSpy).not.toHaveBeenCalled()

      sessionTimeout.destroy()
    })
  })

  describe('Callback Integration', () => {
    it('should call all callbacks in correct order', async () => {
      const callOrder = []

      const onActivitySpy = vi.fn(() => callOrder.push('activity'))
      const onWarningSpy = vi.fn(() => callOrder.push('warning'))
      const onTimeoutSpy = vi.fn(() => callOrder.push('timeout'))

      manager.init({
        config: { timeout: 60000, warningBefore: 30000 },
        callbacks: {
          onActivity: onActivitySpy,
          onWarning: onWarningSpy,
          onTimeout: onTimeoutSpy
        }
      })

      document.dispatchEvent(new Event('mousedown', { bubbles: true }))
      vi.advanceTimersByTime(1000)

      vi.advanceTimersByTime(29999)
      expect(onWarningSpy).not.toHaveBeenCalled()

      vi.advanceTimersByTime(2)
      expect(onWarningSpy).toHaveBeenCalled()

      vi.advanceTimersByTime(29998)
      expect(onTimeoutSpy).not.toHaveBeenCalled()

      vi.advanceTimersByTime(3)
      expect(onTimeoutSpy).toHaveBeenCalled()

      expect(callOrder).toEqual(['activity', 'warning', 'timeout'])
    })

    it('should pass correct data to callbacks', () => {
      const onWarningSpy = vi.fn()
      const onActivitySpy = vi.fn()

      manager.init({
        config: { timeout: 60000, warningBefore: 30000 },
        callbacks: {
          onWarning: onWarningSpy,
          onActivity: onActivitySpy
        }
      })

      document.dispatchEvent(new Event('keydown', { bubbles: true }))
      vi.advanceTimersByTime(1000)

      expect(onActivitySpy).toHaveBeenCalledWith(
        expect.objectContaining({
          timestamp: expect.any(Number),
          remainingTime: expect.any(Number)
        })
      )

      vi.advanceTimersByTime(29999)
      expect(onWarningSpy).not.toHaveBeenCalled()

      vi.advanceTimersByTime(2)

      expect(onWarningSpy).toHaveBeenCalledWith(
        expect.objectContaining({
          remainingTime: expect.any(Number),
          totalTimeout: 60000,
          warningBefore: 30000
        })
      )
    })
  })
})
