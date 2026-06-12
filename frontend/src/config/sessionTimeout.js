export const SESSION_TIMEOUT_CONFIG = {
  DEFAULT_TIMEOUT: 30 * 60 * 1000,
  WARNING_BEFORE: 5 * 60 * 1000,
  MIN_TIMEOUT: 1 * 60 * 1000,
  MAX_TIMEOUT: 24 * 60 * 60 * 1000,
  HEARTBEAT_INTERVAL: 5 * 60 * 1000,
  ACTIVITY_EVENTS: [
    'mousedown',
    'mousemove',
    'keydown',
    'scroll',
    'touchstart',
    'click',
    'keypress'
  ],
  STORAGE_KEYS: {
    LAST_ACTIVITY: 'hot_event_last_activity',
    TIMEOUT_CONFIG: 'hot_event_timeout_config',
    WARNING_SHOWN: 'hot_event_warning_shown'
  },
  CUSTOM_CONFIG_KEY: 'session_timeout'
}

export function validateTimeout(timeout) {
  if (typeof timeout !== 'number' || isNaN(timeout)) {
    return false
  }
  return timeout >= SESSION_TIMEOUT_CONFIG.MIN_TIMEOUT && 
         timeout <= SESSION_TIMEOUT_CONFIG.MAX_TIMEOUT
}

export function getTimeoutConfig() {
  try {
    const stored = localStorage.getItem(SESSION_TIMEOUT_CONFIG.STORAGE_KEYS.TIMEOUT_CONFIG)
    if (stored) {
      const parsed = JSON.parse(stored)
      if (validateTimeout(parsed.timeout)) {
        return parsed
      }
    }
  } catch (e) {
    console.warn('Failed to parse timeout config:', e)
  }
  return {
    timeout: SESSION_TIMEOUT_CONFIG.DEFAULT_TIMEOUT,
    warningBefore: SESSION_TIMEOUT_CONFIG.WARNING_BEFORE
  }
}

export function setTimeoutConfig(config) {
  if (!validateTimeout(config.timeout)) {
    throw new Error(`Invalid timeout value. Must be between ${SESSION_TIMEOUT_CONFIG.MIN_TIMEOUT} and ${SESSION_TIMEOUT_CONFIG.MAX_TIMEOUT} ms`)
  }
  localStorage.setItem(
    SESSION_TIMEOUT_CONFIG.STORAGE_KEYS.TIMEOUT_CONFIG,
    JSON.stringify(config)
  )
}

export function resetTimeoutConfig() {
  localStorage.removeItem(SESSION_TIMEOUT_CONFIG.STORAGE_KEYS.TIMEOUT_CONFIG)
}
