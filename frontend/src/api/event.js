import request from '@/utils/request'
import axios from 'axios'

function downloadFile(url, params, filename) {
  const token = localStorage.getItem('hot_event_token')
  return axios({
    url: '/api' + url,
    method: 'get',
    params: params,
    responseType: 'blob',
    headers: {
      'Authorization': token ? 'Bearer ' + token : ''
    }
  }).then(response => {
    const blob = response.data
    if (blob.type && blob.type.includes('application/json')) {
      return blob.text().then(text => {
        const json = JSON.parse(text)
        if (json.code === 401) {
          localStorage.removeItem('hot_event_token')
          localStorage.removeItem('hot_event_user')
          if (window.location.pathname !== '/login') {
            window.location.href = '/login'
          }
          return Promise.reject(new Error('未登录或token已过期'))
        }
        return Promise.reject(new Error(json.message || '导出失败'))
      })
    }

    const disposition = response.headers['content-disposition']
    let finalFilename = filename
    if (disposition) {
      const utf8Match = disposition.match(/filename\*=utf-8''([^;]+)/)
      if (utf8Match) {
        finalFilename = decodeURIComponent(utf8Match[1])
      }
    }
    const downloadBlob = new Blob([blob])
    const link = document.createElement('a')
    link.href = URL.createObjectURL(downloadBlob)
    link.download = finalFilename
    link.click()
    URL.revokeObjectURL(link.href)
    return true
  }).catch(error => {
    if (error.response && error.response.status === 401) {
      localStorage.removeItem('hot_event_token')
      localStorage.removeItem('hot_event_user')
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
      return Promise.reject(new Error('未登录或token已过期'))
    }
    return Promise.reject(error)
  })
}

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

export function exportHotEventsExcel(params) {
  return downloadFile('/events/export/excel', params, 'hot_events.xlsx')
}

export function exportHotEventsCsv(params) {
  return downloadFile('/events/export/csv', params, 'hot_events.csv')
}

export function exportStatisticsExcel() {
  return downloadFile('/events/statistics/export/excel', {}, 'statistics.xlsx')
}

export function exportStatisticsCsv() {
  return downloadFile('/events/statistics/export/csv', {}, 'statistics.csv')
}
