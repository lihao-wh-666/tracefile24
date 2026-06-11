import JSEncrypt from 'jsencrypt'
import request from '@/utils/request'

let cachedPublicKey = null

export async function getPublicKey() {
  if (cachedPublicKey) {
    return cachedPublicKey
  }
  try {
    const data = await request({
      url: '/auth/public-key',
      method: 'get'
    })
    cachedPublicKey = data
    return data
  } catch (e) {
    console.error('获取RSA公钥失败:', e)
    throw e
  }
}

export function encryptWithPublicKey(plainText, publicKey) {
  if (!plainText || !publicKey) {
    return plainText
  }
  const encryptor = new JSEncrypt()
  encryptor.setPublicKey(publicKey)
  const encrypted = encryptor.encrypt(plainText)
  if (!encrypted) {
    throw new Error('RSA加密失败')
  }
  return encrypted
}

export async function encryptPassword(plainText) {
  if (!plainText) {
    return plainText
  }
  const publicKey = await getPublicKey()
  return encryptWithPublicKey(plainText, publicKey)
}

export function clearPublicKeyCache() {
  cachedPublicKey = null
}
