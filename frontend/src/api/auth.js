import request from '@/utils/request'
import { encryptPassword } from '@/utils/rsa'

export async function login(data) {
  const encryptedData = {
    ...data,
    password: await encryptPassword(data.password)
  }
  return request({
    url: '/auth/login',
    method: 'post',
    data: encryptedData
  })
}

export function logout() {
  return request({
    url: '/auth/logout',
    method: 'post'
  })
}

export function getCurrentUser() {
  return request({
    url: '/auth/me',
    method: 'get'
  })
}

export function getUserList(params) {
  return request({
    url: '/users/page',
    method: 'get',
    params
  })
}

export function getUser(id) {
  return request({
    url: `/users/${id}`,
    method: 'get'
  })
}

export async function createUser(data) {
  const encryptedData = {
    ...data,
    password: data.password ? await encryptPassword(data.password) : data.password
  }
  return request({
    url: '/users',
    method: 'post',
    data: encryptedData
  })
}

export async function updateUser(id, data) {
  const encryptedData = { ...data }
  if (data.password) {
    encryptedData.password = await encryptPassword(data.password)
  }
  return request({
    url: `/users/${id}`,
    method: 'put',
    data: encryptedData
  })
}

export function deleteUser(id) {
  return request({
    url: `/users/${id}`,
    method: 'delete'
  })
}

export function unlockUser(id) {
  return request({
    url: `/users/${id}/unlock`,
    method: 'post'
  })
}

export function getProfile() {
  return request({
    url: '/profile',
    method: 'get'
  })
}

export function updateProfile(data) {
  return request({
    url: '/profile',
    method: 'put',
    data
  })
}

export async function updatePassword(data) {
  const encryptedData = {
    oldPassword: await encryptPassword(data.oldPassword),
    newPassword: await encryptPassword(data.newPassword),
    confirmPassword: await encryptPassword(data.confirmPassword)
  }
  return request({
    url: '/profile/password',
    method: 'put',
    data: encryptedData
  })
}

export function uploadAvatar(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request({
    url: '/profile/avatar',
    method: 'post',
    data: formData,
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

export function heartbeat() {
  return request({
    url: '/auth/heartbeat',
    method: 'post'
  })
}

export function getSessionConfig() {
  return request({
    url: '/auth/session-config',
    method: 'get'
  })
}

