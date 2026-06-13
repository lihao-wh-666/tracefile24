import { ElMessage } from 'element-plus'

const MESSAGE_DURATION_KEY = 'message_duration'
const DEFAULT_DURATION = 1500

const getDuration = () => {
  const stored = localStorage.getItem(MESSAGE_DURATION_KEY)
  if (stored) {
    const duration = parseInt(stored)
    if (!isNaN(duration) && duration > 0) {
      return duration
    }
  }
  return DEFAULT_DURATION
}

const createMessage = (type) => {
  return (options) => {
    const duration = getDuration()

    if (typeof options === 'string') {
      return ElMessage({
        message: options,
        type,
        duration
      })
    }

    return ElMessage({
      ...options,
      type,
      duration: options.duration !== undefined ? options.duration : duration
    })
  }
}

const message = (options) => {
  const duration = getDuration()

  if (typeof options === 'string') {
    return ElMessage({
      message: options,
      duration
    })
  }

  return ElMessage({
    ...options,
    duration: options.duration !== undefined ? options.duration : duration
  })
}

message.success = createMessage('success')
message.warning = createMessage('warning')
message.info = createMessage('info')
message.error = createMessage('error')

message.closeAll = ElMessage.closeAll

export default message
