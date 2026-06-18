import { reportLog, reportLogsBatch } from '@/api/frontendLog'

class FrontendLogger {
  constructor(options = {}) {
    this.enabled = options.enabled !== false
    this.batchSize = options.batchSize || 20
    this.maxQueueSize = options.maxQueueSize || 100
    this.flushInterval = options.flushInterval || 5000
    this.includeUserInfo = options.includeUserInfo !== false
    this.includeBrowserInfo = options.includeBrowserInfo !== false
    this.logLevels = options.logLevels || ['ERROR', 'WARN', 'INFO']

    this.logQueue = []
    this.timer = null
    this.userInfo = null
    this.init()
  }

  init() {
    if (!this.enabled) return

    this.setupGlobalErrorHandler()
    this.setupUnhandledRejectionHandler()
    this.startAutoFlush()

    window.addEventListener('beforeunload', () => {
      this.flush(true)
    })
  }

  setUserInfo(userInfo) {
    this.userInfo = userInfo
  }

  setupGlobalErrorHandler() {
    const originalErrorHandler = window.onerror
    window.onerror = (message, source, lineno, colno, error) => {
      try {
        this.error(message, {
          pageUrl: source,
          lineNumber: lineno,
          columnNumber: colno,
          stackTrace: error ? error.stack : null,
          errorType: error ? error.name : 'Error'
        })
      } catch (e) {
        console.warn('Error handling error:', e)
      }
      if (originalErrorHandler) {
        return originalErrorHandler.call(window, message, source, lineno, colno, error)
      }
      return false
    }
  }

  setupUnhandledRejectionHandler() {
    window.addEventListener('unhandledrejection', (event) => {
      try {
        const reason = event.reason
        const message = reason instanceof Error ? reason.message : String(reason)
        this.error(`Unhandled Promise Rejection: ${message}`, {
          stackTrace: reason instanceof Error ? reason.stack : null,
          errorType: reason instanceof Error ? reason.name : 'PromiseRejection',
          additionalInfo: reason instanceof Object ? JSON.stringify(reason) : null
        })
      } catch (e) {
        console.warn('Error handling unhandled rejection:', e)
      }
    })
  }

  startAutoFlush() {
    if (this.timer) return
    this.timer = setInterval(() => {
      this.flush()
    }, this.flushInterval)
  }

  stopAutoFlush() {
    if (this.timer) {
      clearInterval(this.timer)
      this.timer = null
    }
  }

  getBrowserInfo() {
    const ua = navigator.userAgent
    const browserInfo = {}

    if (ua.includes('Edg')) {
      browserInfo.browser = 'Edge'
      browserInfo.version = ua.match(/Edg\/([\d.]+)/)?.[1] || ''
    } else if (ua.includes('Chrome')) {
      browserInfo.browser = 'Chrome'
      browserInfo.version = ua.match(/Chrome\/([\d.]+)/)?.[1] || ''
    } else if (ua.includes('Firefox')) {
      browserInfo.browser = 'Firefox'
      browserInfo.version = ua.match(/Firefox\/([\d.]+)/)?.[1] || ''
    } else if (ua.includes('Safari')) {
      browserInfo.browser = 'Safari'
      browserInfo.version = ua.match(/Version\/([\d.]+)/)?.[1] || ''
    } else {
      browserInfo.browser = 'Other'
      browserInfo.version = ''
    }

    if (ua.includes('Windows')) {
      browserInfo.os = 'Windows'
    } else if (ua.includes('Mac')) {
      browserInfo.os = 'macOS'
    } else if (ua.includes('Linux')) {
      browserInfo.os = 'Linux'
    } else if (ua.includes('Android')) {
      browserInfo.os = 'Android'
    } else if (ua.includes('iPhone') || ua.includes('iPad')) {
      browserInfo.os = 'iOS'
    } else {
      browserInfo.os = 'Other'
    }

    return browserInfo
  }

  buildLogData(level, message, extra = {}) {
    const browserInfo = this.getBrowserInfo()
    const logData = {
      logLevel: level,
      message: message,
      pageUrl: window.location.href,
      userAgent: navigator.userAgent,
      screenResolution: `${window.screen.width}x${window.screen.height}`,
      logTime: new Date().toISOString()
    }

    if (this.includeBrowserInfo) {
      logData.browserInfo = browserInfo.browser + (browserInfo.version ? ` ${browserInfo.version}` : '')
      logData.osInfo = browserInfo.os
    }

    if (this.includeUserInfo && this.userInfo) {
      logData.userId = this.userInfo.id
      logData.username = this.userInfo.username
    }

    Object.assign(logData, extra)

    return logData
  }

  log(level, message, extra) {
    if (!this.enabled) return
    if (!this.logLevels.includes(level)) return

    const logData = this.buildLogData(level, message, extra)

    if (this.logQueue.length >= this.maxQueueSize) {
      this.logQueue.shift()
    }
    this.logQueue.push(logData)

    if (this.logQueue.length >= this.batchSize) {
      this.flush()
    }
  }

  info(message, extra) {
    this.log('INFO', message, extra)
  }

  warn(message, extra) {
    this.log('WARN', message, extra)
  }

  error(message, extra) {
    this.log('ERROR', message, extra)
  }

  debug(message, extra) {
    this.log('DEBUG', message, extra)
  }

  async flush(immediate = false) {
    if (this.logQueue.length === 0) return

    const logsToSend = [...this.logQueue]
    this.logQueue = []

    try {
      if (logsToSend.length > 1) {
        await reportLogsBatch(logsToSend)
      } else {
        await reportLog(logsToSend[0])
      }
    } catch (error) {
      console.warn('Failed to send logs to server:', error)
      if (!immediate) {
        this.logQueue.unshift(...logsToSend)
        if (this.logQueue.length > this.maxQueueSize) {
          this.logQueue = this.logQueue.slice(0, this.maxQueueSize)
        }
      }
    }
  }

  clearQueue() {
    this.logQueue = []
  }

  getQueueSize() {
    return this.logQueue.length
  }

  enable() {
    this.enabled = true
    this.startAutoFlush()
  }

  disable() {
    this.enabled = false
    this.stopAutoFlush()
  }
}

const logger = new FrontendLogger({
  enabled: true,
  batchSize: 20,
  maxQueueSize: 100,
  flushInterval: 5000,
  includeUserInfo: true,
  includeBrowserInfo: true,
  logLevels: ['ERROR', 'WARN', 'INFO']
})

export default logger
