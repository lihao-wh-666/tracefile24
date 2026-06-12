import { beforeEach, afterEach, vi } from 'vitest'

function createStorageMock() {
  let storage = {}
  return {
    getItem: vi.fn((key) => storage[key] || null),
    setItem: vi.fn((key, value) => {
      storage[key] = String(value)
    }),
    removeItem: vi.fn((key) => {
      delete storage[key]
    }),
    clear: vi.fn(() => {
      storage = {}
    }),
    get length() {
      return Object.keys(storage).length
    },
    key: vi.fn((index) => Object.keys(storage)[index] || null)
  }
}

beforeEach(() => {
  const localStorageMock = createStorageMock()
  const sessionStorageMock = createStorageMock()

  Object.defineProperty(window, 'localStorage', {
    value: localStorageMock,
    writable: true
  })

  Object.defineProperty(window, 'sessionStorage', {
    value: sessionStorageMock,
    writable: true
  })

  Object.defineProperty(global, 'localStorage', {
    value: localStorageMock,
    writable: true
  })

  Object.defineProperty(global, 'sessionStorage', {
    value: sessionStorageMock,
    writable: true
  })

  localStorage.clear()
  sessionStorage.clear()

  vi.useFakeTimers()

  Object.defineProperty(window, 'location', {
    value: {
      href: 'http://localhost/',
      pathname: '/',
      search: '',
      replace: vi.fn(),
      assign: vi.fn()
    },
    writable: true,
    configurable: true
  })

  Object.defineProperty(window, 'caches', {
    value: {
      keys: vi.fn().mockResolvedValue([]),
      delete: vi.fn().mockResolvedValue(true)
    },
    writable: true
  })

  Object.defineProperty(navigator, 'credentials', {
    value: {
      preventSilentAccess: vi.fn().mockResolvedValue()
    },
    writable: true,
    configurable: true
  })

  Object.defineProperty(document, 'hidden', {
    value: false,
    writable: true,
    configurable: true
  })

  if (Element.prototype.scrollIntoView) {
    Element.prototype.scrollIntoView = vi.fn()
  }
})

afterEach(() => {
  vi.clearAllTimers()
  vi.clearAllMocks()
  vi.useRealTimers()
})
