const DAY = new Intl.DateTimeFormat('en-GB', { weekday: 'short', day: 'numeric', month: 'short' })
const DAY_WITH_YEAR = new Intl.DateTimeFormat('en-GB', { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' })

/** "2026-11-05" as a local calendar date, with no timezone shift. */
export function parseDate(iso: string): Date {
  const [year, month, day] = iso.split('-').map(Number)
  return new Date(year, month - 1, day)
}

export function toIsoDate(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

export function addDays(date: Date, days: number): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate() + days)
}

export function today(): Date {
  const now = new Date()
  return new Date(now.getFullYear(), now.getMonth(), now.getDate())
}

/** "Thu 5 Nov", with the year only when it isn't this year. */
export function formatDay(iso: string): string {
  const date = parseDate(iso)
  return (date.getFullYear() === new Date().getFullYear() ? DAY : DAY_WITH_YEAR).format(date)
}

export function daysUntil(iso: string): number {
  return Math.round((parseDate(iso).getTime() - today().getTime()) / 86_400_000)
}
