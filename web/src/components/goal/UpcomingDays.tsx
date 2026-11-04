import { messageOf } from '../../api/client'
import { useTasks, type Task } from '../../api/plan'
import { addDays, formatDay, parseDate, today, toIsoDate } from '../../dates'
import { ErrorMessage, Spinner } from '../ui'
import { Card } from './Card'

const DAYS = 7
const WEEKDAYS = ['SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY']

/** The next week day by day, so the user can see where catch-up work landed. */
export function UpcomingDays({ goalId, bufferDay }: { goalId: number; bufferDay: string | null }) {
  const from = toIsoDate(addDays(today(), 1))
  const to = toIsoDate(addDays(today(), DAYS))
  const tasks = useTasks(goalId, from, to)

  const byDay = new Map<string, Task[]>()
  for (let i = 1; i <= DAYS; i++) byDay.set(toIsoDate(addDays(today(), i)), [])
  tasks.data?.forEach(t => byDay.get(t.scheduledDate)?.push(t))

  return (
    <Card title="Next 7 days">
      {tasks.isPending && <Spinner label="Loading the week" />}
      {tasks.isError && <ErrorMessage>{messageOf(tasks.error)}</ErrorMessage>}
      {tasks.data && (
        <ol className="space-y-3">
          {[...byDay].map(([day, dayTasks]) => {
            const minutes = dayTasks.reduce((sum, t) => sum + t.estimatedMinutes, 0)
            const isBuffer = WEEKDAYS[parseDate(day).getDay()] === bufferDay
            return (
              <li key={day} className="flex gap-4">
                <div className="w-24 shrink-0 text-sm">
                  <p className="font-medium">{formatDay(day)}</p>
                  {minutes > 0 && <p className="text-xs text-slate-500">{minutes} min</p>}
                </div>
                <ul className="min-w-0 flex-1 space-y-1">
                  {dayTasks.length === 0 && (
                    <li className="text-sm text-slate-400">{isBuffer ? 'Buffer day' : 'Nothing planned'}</li>
                  )}
                  {dayTasks.map(t => (
                    <li key={t.id} className="text-sm text-slate-700">
                      {t.title}
                      {isBuffer && <span className="ml-2 text-xs text-amber-600">catch-up</span>}
                    </li>
                  ))}
                </ul>
              </li>
            )
          })}
        </ol>
      )}
    </Card>
  )
}
