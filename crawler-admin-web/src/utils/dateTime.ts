const parseDateTime = (value: string | number | Date | null | undefined) => {
  if (value === null || value === undefined || value === '') return null
  let date: Date
  if (value instanceof Date) {
    date = value
  } else if (typeof value === 'number') {
    date = new Date(value)
  } else {
    const localDateTime = value.match(
      /^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2}):(\d{2})(?:\.(\d+))?$/
    )
    if (localDateTime) {
      date = new Date(
        Number(localDateTime[1]),
        Number(localDateTime[2]) - 1,
        Number(localDateTime[3]),
        Number(localDateTime[4]),
        Number(localDateTime[5]),
        Number(localDateTime[6]),
        Number((localDateTime[7] || '').padEnd(3, '0').slice(0, 3))
      )
    } else {
      date = new Date(value)
    }
  }

  return Number.isNaN(date.getTime()) ? null : date
}

export const formatDateTime = (value: string | number | Date | null | undefined) => {
  const date = parseDateTime(value)
  if (!date) return ''

  const pad = (part: number) => String(part).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

export const formatTimeAgo = (value: string | number | Date | null | undefined, now = Date.now()) => {
  const date = parseDateTime(value)
  if (!date) return ''

  const difference = now - date.getTime()
  const absoluteDifference = Math.abs(difference)
  const suffix = difference < 0 ? '后' : '前'

  if (absoluteDifference < 60_000) return difference < 0 ? '即将' : '刚刚'
  if (absoluteDifference < 3_600_000) return `${Math.floor(absoluteDifference / 60_000)}分钟${suffix}`
  if (absoluteDifference < 86_400_000) return `${Math.floor(absoluteDifference / 3_600_000)}小时${suffix}`
  if (absoluteDifference < 2_592_000_000) return `${Math.floor(absoluteDifference / 86_400_000)}天${suffix}`
  if (absoluteDifference < 31_536_000_000) return `${Math.floor(absoluteDifference / 2_592_000_000)}个月${suffix}`
  return `${Math.floor(absoluteDifference / 31_536_000_000)}年${suffix}`
}
