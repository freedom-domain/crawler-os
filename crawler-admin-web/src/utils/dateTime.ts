export const formatDateTime = (value: string | number | Date | null | undefined) => {
  if (value === null || value === undefined || value === '') return ''

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

  if (Number.isNaN(date.getTime())) return ''

  const pad = (part: number) => String(part).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}
