<template>
  <el-dropdown trigger="click" @command="handleLanguageChange">
    <span class="language-switcher">
      <el-icon><Globe /></el-icon>
      <span class="lang-label">{{ currentLocaleName }}</span>
    </span>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item
          v-for="locale in supportedLocales"
          :key="locale.value"
          :command="locale.value"
          :class="{ 'is-active': currentLocale === locale.value }"
        >
          {{ locale.label }}
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { setLocale, getLocaleName, SUPPORTED_LOCALES } from '@/i18n'

const { t } = useI18n()

const currentLocale = computed(() => {
  const { locale } = useI18n()
  return locale.value
})

const currentLocaleName = computed(() => {
  return getLocaleName(currentLocale.value)
})

const supportedLocales = computed(() => {
  return SUPPORTED_LOCALES.map(locale => ({
    value: locale,
    label: getLocaleName(locale)
  }))
})

const handleLanguageChange = (locale) => {
  setLocale(locale)
}
</script>

<style scoped lang="scss">
.language-switcher {
  display: flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  padding: 0 8px;
  line-height: 40px;
  font-size: 14px;
  color: #606266;

  &:hover {
    background: #f5f7fa;
    border-radius: 6px;
  }

  .lang-label {
    font-size: 13px;
  }
}
</style>
