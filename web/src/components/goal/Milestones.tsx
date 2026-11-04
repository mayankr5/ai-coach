import type { Schemas } from '../../api/client'
import { formatDay, today, toIsoDate } from '../../dates'
import { Card } from './Card'

export function Milestones({ milestones }: { milestones: Schemas['MilestonePlan'][] }) {
  const now = toIsoDate(today())
  const current = milestones.find(m => m.targetDate >= now)?.id
  return (
    <Card title="Milestones">
      <ol className="space-y-3">
        {milestones.map(m => {
          const done = m.tasks.filter(t => t.status === 'DONE').length
          return (
            <li key={m.id} aria-current={m.id === current ? 'step' : undefined}
              className="rounded-lg px-3 py-2 aria-[current=step]:bg-indigo-50 aria-[current=step]:ring-1
                aria-[current=step]:ring-indigo-200">
              <p className="text-sm font-medium">{m.title}</p>
              <p className="text-xs text-slate-500">
                By {formatDay(m.targetDate)}
                {m.tasks.length > 0 ? ` · ${done} of ${m.tasks.length} tasks done` : ' · tasks planned later'}
              </p>
            </li>
          )
        })}
      </ol>
    </Card>
  )
}
