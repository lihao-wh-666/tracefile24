import request from '@/utils/request'

export function getHotEventList(params) {
  return request({
    url: '/events',
    method: 'get',
    params
  })
}

export function getHotEventById(id, lang) {
  const params = {}
  if (lang && lang !== 'zh-CN') {
    params.lang = lang
  }
  return request({
    url: `/events/${id}`,
    method: 'get',
    params
  })
}

export function getEventSources() {
  return request({
    url: '/events/sources',
    method: 'get'
  })
}

export function getEventStatistics() {
  return request({
    url: '/events/statistics',
    method: 'get'
  })
}

export function deleteHotEvent(id) {
  return request({
    url: `/events/${id}`,
    method: 'delete'
  })
}

export function getEventTranslations(eventId) {
  return request({
    url: `/events/${eventId}/translations`,
    method: 'get'
  })
}

export function updateEventTranslation(eventId, language, data) {
  return request({
    url: `/events/${eventId}/translations/${language}`,
    method: 'put',
    data
  })
}
