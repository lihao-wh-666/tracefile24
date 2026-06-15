import request from '@/utils/request'

export function getSupportedLanguages() {
  return request.get('/i18n/supported-languages')
}

export function getLanguageNames() {
  return request.get('/i18n/language-names')
}

export function translateText(text, sourceLang, targetLang) {
  return request.post('/i18n/translate', { text, sourceLang, targetLang })
}

export function translateBatch(texts, sourceLang, targetLang) {
  return request.post('/i18n/translate-batch', { texts, sourceLang, targetLang })
}

export function detectLanguage(text) {
  return request.post('/i18n/detect-language', { text })
}

export function segmentText(text, language) {
  return request.post('/i18n/segment', { text, language })
}

export function extractKeywords(text, language, count) {
  return request.post('/i18n/extract-keywords', { text, language, count })
}

export function convertChinese(text, direction) {
  return request.post('/i18n/convert', { text, direction })
}

export function getI18nStatus() {
  return request.get('/i18n/status')
}
