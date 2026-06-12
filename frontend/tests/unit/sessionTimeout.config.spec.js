import { describe, it, expect, beforeEach } from 'vitest'
import {
  SESSION_TIMEOUT_CONFIG,
  validateTimeout,
  getTimeoutConfig,
  setTimeoutConfig,
  resetTimeoutConfig
} from '@/config/sessionTimeout'

describe('Session Timeout Configuration', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  describe('SESSION_TIMEOUT_CONFIG', () => {
    it('should have correct default values', () => {
      expect(SESSION_TIMEOUT_CONFIG.DEFAULT_TIMEOUT).toBe(30 * 60 * 1000)
      expect(SESSION_TIMEOUT_CONFIG.WARNING_BEFORE).toBe(5 * 60 * 1000)
      expect(SESSION_TIMEOUT_CONFIG.MIN_TIMEOUT).toBe(1 * 60 * 1000)
      expect(SESSION_TIMEOUT_CONFIG.MAX_TIMEOUT).toBe(24 * 60 * 60 * 1000)
    })

    it('should define all activity events', () => {
      expect(SESSION_TIMEOUT_CONFIG.ACTIVITY_EVENTS).toContain('mousedown')
      expect(SESSION_TIMEOUT_CONFIG.ACTIVITY_EVENTS).toContain('mousemove')
      expect(SESSION_TIMEOUT_CONFIG.ACTIVITY_EVENTS).toContain('keydown')
      expect(SESSION_TIMEOUT_CONFIG.ACTIVITY_EVENTS).toContain('scroll')
      expect(SESSION_TIMEOUT_CONFIG.ACTIVITY_EVENTS).toContain('touchstart')
      expect(SESSION_TIMEOUT_CONFIG.ACTIVITY_EVENTS).toContain('click')
      expect(SESSION_TIMEOUT_CONFIG.ACTIVITY_EVENTS).toContain('keypress')
    })

    it('should define storage keys', () => {
      expect(SESSION_TIMEOUT_CONFIG.STORAGE_KEYS.LAST_ACTIVITY).toBeDefined()
      expect(SESSION_TIMEOUT_CONFIG.STORAGE_KEYS.TIMEOUT_CONFIG).toBeDefined()
      expect(SESSION_TIMEOUT_CONFIG.STORAGE_KEYS.WARNING_SHOWN).toBeDefined()
    })
  })

  describe('validateTimeout', () => {
    it('should return true for valid timeout values', () => {
      expect(validateTimeout(SESSION_TIMEOUT_CONFIG.MIN_TIMEOUT)).toBe(true)
      expect(validateTimeout(SESSION_TIMEOUT_CONFIG.DEFAULT_TIMEOUT)).toBe(true)
      expect(validateTimeout(SESSION_TIMEOUT_CONFIG.MAX_TIMEOUT)).toBe(true)
      expect(validateTimeout(10 * 60 * 1000)).toBe(true)
    })

    it('should return false for invalid timeout values', () => {
      expect(validateTimeout(SESSION_TIMEOUT_CONFIG.MIN_TIMEOUT - 1)).toBe(false)
      expect(validateTimeout(SESSION_TIMEOUT_CONFIG.MAX_TIMEOUT + 1)).toBe(false)
      expect(validateTimeout(0)).toBe(false)
      expect(validateTimeout(-1000)).toBe(false)
      expect(validateTimeout(NaN)).toBe(false)
      expect(validateTimeout('300000')).toBe(false)
      expect(validateTimeout(null)).toBe(false)
      expect(validateTimeout(undefined)).toBe(false)
    })
  })

  describe('getTimeoutConfig', () => {
    it('should return default config when no stored config exists', () => {
      const config = getTimeoutConfig()
      expect(config.timeout).toBe(SESSION_TIMEOUT_CONFIG.DEFAULT_TIMEOUT)
      expect(config.warningBefore).toBe(SESSION_TIMEOUT_CONFIG.WARNING_BEFORE)
    })

    it('should return stored config when valid', () => {
      const customConfig = {
        timeout: 15 * 60 * 1000,
        warningBefore: 2 * 60 * 1000
      }
      localStorage.setItem(
        SESSION_TIMEOUT_CONFIG.STORAGE_KEYS.TIMEOUT_CONFIG,
        JSON.stringify(customConfig)
      )

      const config = getTimeoutConfig()
      expect(config.timeout).toBe(customConfig.timeout)
      expect(config.warningBefore).toBe(customConfig.warningBefore)
    })

    it('should return default config when stored config is invalid', () => {
      localStorage.setItem(
        SESSION_TIMEOUT_CONFIG.STORAGE_KEYS.TIMEOUT_CONFIG,
        JSON.stringify({ timeout: -1000 })
      )

      const config = getTimeoutConfig()
      expect(config.timeout).toBe(SESSION_TIMEOUT_CONFIG.DEFAULT_TIMEOUT)
    })

    it('should return default config when stored config is corrupted', () => {
      localStorage.setItem(
        SESSION_TIMEOUT_CONFIG.STORAGE_KEYS.TIMEOUT_CONFIG,
        'invalid-json'
      )

      const config = getTimeoutConfig()
      expect(config.timeout).toBe(SESSION_TIMEOUT_CONFIG.DEFAULT_TIMEOUT)
    })
  })

  describe('setTimeoutConfig', () => {
    it('should store valid config in localStorage', () => {
      const config = {
        timeout: 15 * 60 * 1000,
        warningBefore: 3 * 60 * 1000
      }

      setTimeoutConfig(config)

      const stored = JSON.parse(
        localStorage.getItem(SESSION_TIMEOUT_CONFIG.STORAGE_KEYS.TIMEOUT_CONFIG)
      )
      expect(stored.timeout).toBe(config.timeout)
      expect(stored.warningBefore).toBe(config.warningBefore)
    })

    it('should throw error for invalid timeout', () => {
      expect(() => {
        setTimeoutConfig({ timeout: -1000, warningBefore: 60000 })
      }).toThrow('Invalid timeout value')
    })
  })

  describe('resetTimeoutConfig', () => {
    it('should remove stored config from localStorage', () => {
      localStorage.setItem(
        SESSION_TIMEOUT_CONFIG.STORAGE_KEYS.TIMEOUT_CONFIG,
        JSON.stringify({ timeout: 600000, warningBefore: 60000 })
      )

      resetTimeoutConfig()

      expect(localStorage.getItem(SESSION_TIMEOUT_CONFIG.STORAGE_KEYS.TIMEOUT_CONFIG)).toBeNull()
    })
  })
})
