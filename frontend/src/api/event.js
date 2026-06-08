import request from '@/utils/request'

export function getHotEventList(params) {
  return request({
    url: '/events',
    method: 'get',
    params
  })
}

export function getHotEventById(id) {
  return request({
    url: `/events/${id}`,
    method: 'get'
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
