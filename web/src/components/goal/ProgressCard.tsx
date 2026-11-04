import { messageOf } from '../../api/client'
import { useProgress } from '../../api/plan'
import { formatDay } from '../../dates'
import { ErrorMessage, Spinner } from '../ui'
import { Card } from './Card'

export function ProgressCard({ goalId }: { goalId: number }) {
  const progress = useProgress(goalId)
  if (progress.isPending) return <Card title="Progress"><Spinner /></Card>
  if (progress.isError) return <Card title="Progress"><ErrorMessage>{messageOf(progress.error)}</ErrorMessage></Card>

  const p = progress.data
  const healthy = p.minutesBehind === 0
  return (
    <Card title="Progress">
      <div>
        <div className="flex items-baseline justify-between">
          <p className={`text-sm font-medium ${healthy ? 'text-emerald-700' : 'text-amber-700'}`}>
            {healthy ? 'On track' : `${p.minutesBehind} min behind`}
          </p>
          <p className="text-sm text-slate-500">{p.healthPercent}%</p>
        </div>
        <div className="mt-2 h-2 rounded-full bg-slate-100" role="meter" aria-label="Plan health"
          aria-valuemin={0} aria-valuemax={100} aria-valuenow={p.healthPercent}>
          <div className={`h-2 rounded-full ${healthy ? 'bg-emerald-500' : 'bg-amber-500'}`}
            style={{ width: `${p.healthPercent}%` }} />
        </div>
        {p.nextMilestone && (
          <p className="mt-2 text-xs text-slate-500">
            {healthy ? 'Enough time' : 'Not enough time at your normal pace'} for “{p.nextMilestone.title}” by{' '}
            {formatDay(p.nextMilestone.targetDate)}
          </p>
        )}
      </div>
      <dl className="mt-5 grid grid-cols-2 gap-3 text-sm">
        <Stat label="Tasks done" value={`${p.doneTasks} of ${p.totalTasks}`} />
        <Stat label="Completion" value={p.completionRate === null ? '–' : `${Math.round(p.completionRate * 100)}%`} />
        <Stat label="Partly done" value={String(p.partialTasks)} />
        <Stat label="Skipped" value={String(p.skippedTasks)} />
      </dl>
    </Card>
  )
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg bg-slate-50 px-3 py-2">
      <dt className="text-xs text-slate-500">{label}</dt>
      <dd className="font-semibold">{value}</dd>
    </div>
  )
}
