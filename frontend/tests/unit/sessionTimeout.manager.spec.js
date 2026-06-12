import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { SESSION_TIMEOUT_CONFIG } from '@/config/sessionTimeout'

vi.mock('@/stores/user', () => ({
  useUserStore: vi.fn().mockReturnValue({
    logout: vi.fn().mockResolvedValue()
  })
}))

import { SessionTimeoutManager } from '@/utils/sessionTimeout'
import { useUserStore } from '@/stores/user'

describe('SessionTimeoutManager', () => {
  let manager

  beforeEach(() => {
    localStorage.clear()
    sessionStorage.clear()
    vi.clearAllTimers()
    vi.useFakeTimers()

    const userStore = useUserStore()
    vi.mocked(userStore.logout).mockClear()
    vi.mocked(useUserStore).mockClear()

    manager = new SessionTimeoutManager()
  })

  afterEach(() => {
    if (manager && manager.isInitialized) {
      manager.destroy()
    }
    vi.useRealTimers()
  })

  describe('Initialization', () => {
    it('should initialize with default config', () => {
      manager.init()
      expect(manager.isInitialized).toBe(true)
      expect(manager.config.timeout).toBe(SESSION_TIMEOUT_CONFIG.DEFAULT_TIMEOUT)
      expect(manager.config.warningBefore).toBe(SESSION_TIMEOUT_CONFIG.WARNING_BEFORE)
    })

    it('should initialize with custom config', () => {
      const customConfig = {
        timeout: 15 * 60 * 1000,
        warningBefore: 2 * 60 * 1000
      }
      manager.init({ config: customConfig })
      expect(manager.config.timeout).toBe(customConfig.timeout)
      expect(manager.config.warningBefore).toBe(customConfig.warningBefore)
    })

    it('should throw error for invalid config', () => {
      expect(() => {
        manager.init({ config: { timeout: -1000, warningBefore: 60000 } })
      }).toThrow('Invalid timeout configuration')
    })

    it('should setup activity event listeners', () => {
      const addEventListenerSpy = vi.spyOn(document, 'addEventListener')
      manager.init()
      SESSION_TIMEOUT_CONFIG.ACTIVITY_EVENTS.forEach(event => {
        expect(addEventListenerSpy).toHaveBeenCalledWith(
          event,
          expect.any(Function),
          true
        )
      })
    })

    it('should start check interval', () => {
      manager.init()
      expect(manager.checkIntervalId).not.toBeNull()
    })
  })

  describe('Activity Detection', () => {
    beforeEach(() => {
      manager.init({
        config: { timeout: 60000, warningBefore: 30000 }
      })
    })

    it('should update last activity time on user activity', () => {
      const initialTime = manager.lastActivityTime
      vi.advanceTimersByTime(1000)
      document.dispatchEvent(new Event('mousedown', { bubbles: true }))
      vi.advanceTimersByTime(1000)
      expect(manager.lastActivityTime).toBeGreaterThan(initialTime)
    })

    it('should reset timeouts on user activity', () => {
      const scheduleTimeoutsSpy = vi.spyOn(manager, 'scheduleTimeouts')
      document.dispatchEvent(new Event('keydown', { bubbles: true }))
      vi.advanceTimersByTime(1000)
      expect(scheduleTimeoutsSpy).toHaveBeenCalled()
    })

    it('should hide warning when user activity detected during warning', () => {
      manager.showWarning()
      expect(manager.isWarningShown).toBe(true)
      document.dispatchEvent(new Event('click', { bubbles: true }))
      vi.advanceTimersByTime(1000)
      expect(manager.isWarningShown).toBe(false)
    })
  })

  describe('Timeout Scheduling', () => {
    beforeEach(() => {
      manager.init({
        config: { timeout: 60000, warningBefore: 30000 }
      })
    })

    it('should schedule warning timeout', () => {
      expect(manager.warningTimeoutId).not.toBeNull()
    })

    it('should schedule logout timeout', () => {
      expect(manager.timeoutId).not.toBeNull()
    })

    it('should show warning after warning time', () => {
      const showWarningSpy = vi.spyOn(manager, 'showWarning')
      vi.advanceTimersByTime(29999)
      expect(showWarningSpy).not.toHaveBeenCalled()
      vi.advanceTimersByTime(2)
      expect(showWarningSpy).toHaveBeenCalled()
    })

    it('should handle timeout after timeout duration', () => {
      const handleTimeoutSpy = vi.spyOn(manager, 'handleTimeout')
      vi.advanceTimersByTime(59999)
      expect(handleTimeoutSpy).not.toHaveBeenCalled()
      vi.advanceTimersByTime(2)
      expect(handleTimeoutSpy).toHaveBeenCalled()
    })
  })

  describe('Session Actions', () => {
    beforeEach(() => {
      manager.init({
        config: { timeout: 60000, warningBefore: 30000 }
      })
    })

    it('should continue session and reset timers', () => {
      manager.showWarning()
      expect(manager.isWarningShown).toBe(true)
      const scheduleTimeoutsSpy = vi.spyOn(manager, 'scheduleTimeouts')
      manager.continueSession()
      expect(manager.isWarningShown).toBe(false)
      expect(scheduleTimeoutsSpy).toHaveBeenCalled()
    })

    it('should perform secure logout', async () => {
      localStorage.setItem('hot_event_token', 'test-token')
      localStorage.setItem('hot_event_user', JSON.stringify({ id: 1, name: 'test' }))
      await manager.performSecureLogout()
      const userStore = useUserStore()
      expect(userStore.logout).toHaveBeenCalled()
      expect(localStorage.getItem('hot_event_token')).toBeNull()
      expect(localStorage.getItem('hot_event_user')).toBeNull()
    })

    it('should clear all sensitive data on logout', () => {
      localStorage.setItem('hot_event_token', 'token123')
      localStorage.setItem('hot_event_user', 'user123')
      localStorage.setItem(SESSION_TIMEOUT_CONFIG.STORAGE_KEYS.LAST_ACTIVITY, '123456')
      localStorage.setItem(SESSION_TIMEOUT_CONFIG.STORAGE_KEYS.WARNING_SHOWN, 'true')
      sessionStorage.setItem('hot_event_token', 'session-token')
      manager.clearSensitiveData()
      expect(localStorage.getItem('hot_event_token')).toBeNull()
      expect(localStorage.getItem('hot_event_user')).toBeNull()
      expect(sessionStorage.getItem('hot_event_token')).toBeNull()
    })

    it('should redirect to login page on timeout', async () => {
      window.location.pathname = '/dashboard'
      await manager.performSecureLogout()
      expect(window.location.replace).toHaveBeenCalledWith('/login?timeout=1')
    })
  })

  describe('Dynamic Configuration', () => {
    beforeEach(() => {
      manager.init()
    })

    it('should update timeout dynamically', () => {
      const newTimeout = 60 * 60 * 1000
      manager.setTimeout(newTimeout)
      expect(manager.config.timeout).toBe(newTimeout)
    })

    it('should persist timeout config to localStorage', () => {
      const newTimeout = 45 * 60 * 1000
      manager.setTimeout(newTimeout)
      const stored = JSON.parse(
        localStorage.getItem(SESSION_TIMEOUT_CONFIG.STORAGE_KEYS.TIMEOUT_CONFIG)
      )
      expect(stored.timeout).toBe(newTimeout)
    })

    it('should throw error for invalid timeout update', () => {
      expect(() => {
        manager.setTimeout(-1000)
      }).toThrow('Invalid timeout value')
    })
  })

  describe('Cross-tab Communication', () => {
    beforeEach(() => {
      manager.init()
    })

    it('should update activity time when storage event received', () => {
      const newActivityTime = Date.now() + 5000
      window.dispatchEvent(new StorageEvent('storage', {
        key: SESSION_TIMEOUT_CONFIG.STORAGE_KEYS.LAST_ACTIVITY,
        newValue: newActivityTime.toString()
      }))
      expect(manager.lastActivityTime).toBe(newActivityTime)
    })

    it('should hide warning when activity detected in another tab', () => {
      manager.showWarning()
      expect(manager.isWarningShown).toBe(true)
      const newActivityTime = Date.now() + 1000
      window.dispatchEvent(new StorageEvent('storage', {
        key: SESSION_TIMEOUT_CONFIG.STORAGE_KEYS.LAST_ACTIVITY,
        newValue: newActivityTime.toString()
      }))
      expect(manager.isWarningShown).toBe(false)
    })

    it('should destroy manager when token is removed', () => {
      window.dispatchEvent(new StorageEvent('storage', {
        key: 'hot_event_token',
        newValue: null
      }))
      expect(manager.isInitialized).toBe(false)
    })
  })

  describe('Status Check', () => {
    beforeEach(() => {
      manager.init({
        config: { timeout: 60000, warningBefore: 30000 }
      })
    })

    it('should return correct remaining time', () => {
      vi.advanceTimersByTime(3000)
      const afterRemaining = manager.getRemainingTime()
      expect(afterRemaining).toBeGreaterThan(56000)
      expect(afterRemaining).toBeLessThan(58000)
    })

    it('should return zero when time is up', () => {
      vi.advanceTimersByTime(60000)
      expect(manager.getRemainingTime()).toBe(0)
    })

    it('should report active when session is valid', () => {
      expect(manager.isActive()).toBe(true)
    })

    it('should report inactive after timeout', () => {
      vi.advanceTimersByTime(60000)
      expect(manager.isActive()).toBe(false)
    })

    it('should return correct config', () => {
      const config = manager.getConfig()
      expect(config.timeout).toBe(60000)
      expect(config.warningBefore).toBe(30000)
    })
  })

  describe('Cleanup', () => {
    it('should clear all timeouts on destroy', () => {
      manager.init()
      const clearTimeoutSpy = vi.spyOn(global, 'clearTimeout')
      const clearIntervalSpy = vi.spyOn(global, 'clearInterval')
      manager.destroy()
      expect(clearTimeoutSpy).toHaveBeenCalled()
      expect(clearIntervalSpy).toHaveBeenCalled()
    })

    it('should remove all event listeners on destroy', () => {
      manager.init()
      const removeEventListenerDocSpy = vi.spyOn(document, 'removeEventListener')
      const removeEventListenerWinSpy = vi.spyOn(window, 'removeEventListener')
      manager.destroy()
      SESSION_TIMEOUT_CONFIG.ACTIVITY_EVENTS.forEach(event => {
        expect(removeEventListenerDocSpy).toHaveBeenCalledWith(
          event,
          expect.any(Function),
          true
        )
      })
      expect(removeEventListenerWinSpy).toHaveBeenCalledWith(
        'storage',
        expect.any(Function)
      )
    })

    it('should clear warning state on destroy', () => {
      manager.init()
      manager.showWarning()
      manager.destroy()
      expect(manager.isWarningShown).toBe(false)
      expect(manager.isInitialized).toBe(false)
    })
  })
})
