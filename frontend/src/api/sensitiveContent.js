import request from '@/utils/request'

export function getSensitiveStatus() {
  return request({
    url: '/sensitive-content/status',
    method: 'get'
  })
}

export function getSensitiveConfig() {
  return request({
    url: '/sensitive-content/config',
    method: 'get'
  })
}

export function toggleSensitiveFilter(enabled) {
  return request({
    url: '/sensitive-content/toggle',
    method: 'put',
    data: { enabled }
  })
}

export function updateSensitiveKeywords(typeCode, keywords) {
  return request({
    url: `/sensitive-content/keywords/${typeCode}`,
    method: 'put',
    data: { keywords }
  })
}

export function updateSensitiveRegex(typeCode, regex) {
  return request({
    url: `/sensitive-content/regex/${typeCode}`,
    method: 'put',
    data: { regex }
  })
}

export function checkSensitiveContent(text) {
  return request({
    url: '/sensitive-content/check',
    method: 'post',
    data: { text }
  })
}

export function reloadSensitiveConfig() {
  return request({
    url: '/sensitive-content/reload',
    method: 'post'
  })
}
