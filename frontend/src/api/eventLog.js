import request from '@/utils/request'

export function getEventLogList(params) {
  return request({
    url: '/event-logs',
    method: 'get',
    params
  })
}

export function getEventLogsByEventId(eventId) {
  return request({
    url: `/event-logs/event/${eventId}`,
    method: 'get'
  })
}

export function getEventLogSources() {
  return request({
    url: '/event-logs/sources',
    method: 'get'
  })
}

export function getEventLogOperators() {
  return request({
    url: '/event-logs/operators',
    method: 'get'
  })
}

export function getEventLogStatistics(startTime, endTime) {
  const params = {}
  if (startTime) params.startTime = startTime
  if (endTime) params.endTime = endTime
  return request({
    url: '/event-logs/statistics',
    method: 'get',
    params
  })
}
