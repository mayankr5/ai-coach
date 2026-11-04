import { useState, type ReactNode } from 'react'
import { useAdjustmentOptions, useCutScope, useExtend, useMoreTime, useSuggestCut, type CutSuggestion } from '../../api/adjust'
import { ApiError, messageOf } from '../../api/client'
import { useProgress } from '../../api/plan'
import { formatDay } from '../../dates'
import { Button, ErrorMessage, Spinner } from '../ui'
import { Card } from './Card'

type Fix = 'extend' | 'moreTime' | 'cut'

/** Shown while the next milestone doesn't fit: each fix with what it would change, applied only once confirmed. */
export function BackOnTrack({ goalId }: { goalId: number }) {
  const progress = useProgress(goalId)
  const behind = (progress.data?.minutesBehind ?? 0) > 0
  const options = useAdjustmentOptions(goalId, behind)
  const [open, setOpen] = useState<Fix | null>(null)
  const extend = useExtend(goalId)
  const moreTime = useMoreTime(goalId)
  const suggest = useSuggestCut(goalId)

  if (!behind) return null
  if (options.isPending) return <Card title="Get back on track"><Spinner /></Card>
  if (options.isError) return <Card title="Get back on track"><ErrorMessage>{messageOf(options.error)}</ErrorMessage></Card>

  const o = options.data
  if (o.minutesBehind === 0 || !o.milestone) return null
  const toggle = (fix: Fix) => setOpen(open === fix ? null : fix)
  const close = { onSuccess: () => setOpen(null) }
  return (
    <Card title="Get back on track">
      <p className="text-sm text-slate-600">
        “{o.milestone.title}” needs {o.minutesBehind} more minutes than you have before{' '}
        {formatDay(o.milestone.targetDate)}. Pick a fix; nothing changes until you confirm.
      </p>
      <div className="mt-4 space-y-3">
        {o.extend && (
          <Option title={`Move it by ${o.extend.days} ${o.extend.days === 1 ? 'day' : 'days'}`}
            detail={`Due ${formatDay(o.extend.milestoneDate)}; later milestones and the deadline
              (${formatDay(o.extend.deadline)}) move too.`}
            action="Move dates" open={open === 'extend'} onToggle={() => toggle('extend')}>
            <Confirm pending={extend.isPending} error={extend.error} onCancel={() => setOpen(null)}
              onConfirm={() => extend.mutate(o.extend!.days, close)} />
          </Option>
        )}
        {o.moreTime && (
          <Option title={`${o.moreTime.minutesPerDay} min a day`}
            detail={`Up from ${o.moreTime.currentMinutesPerDay} (+${o.moreTime.minutesPerDay
              - o.moreTime.currentMinutesPerDay}), from tomorrow on. Upcoming tasks move earlier.`}
            warning={o.moreTime.realistic ? undefined
              : 'That is a big jump from your usual time and may be hard to keep up.'}
            action="Add time" open={open === 'moreTime'} onToggle={() => toggle('moreTime')}>
            <Confirm pending={moreTime.isPending} error={moreTime.error} onCancel={() => setOpen(null)}
              onConfirm={() => moreTime.mutate(o.moreTime!.minutesPerDay, close)} />
          </Option>
        )}
        <Option title="Drop some tasks" detail="The coach suggests upcoming tasks you could skip, and why."
          action="Suggest" open={open === 'cut'} onToggle={() => { toggle('cut'); suggest.mutate() }}>
          <CutScope goalId={goalId} suggest={suggest} onDone={() => setOpen(null)} />
        </Option>
      </div>
    </Card>
  )
}

function Option({ title, detail, warning, action, open, onToggle, children }: {
  title: string; detail: string; warning?: string; action: string; open: boolean; onToggle: () => void
  children: ReactNode
}) {
  return (
    <div className="rounded-lg ring-1 ring-slate-200">
      <div className="p-3">
        <p className="text-sm font-medium">{title}</p>
        <p className="mt-0.5 text-xs text-slate-500">{detail}</p>
        {warning && <p className="mt-1 text-xs text-amber-700">{warning}</p>}
        {!open && <Button variant="secondary" className="mt-3 px-3 py-1.5" onClick={onToggle}>{action}</Button>}
      </div>
      {open && <div className="border-t border-slate-200 p-3">{children}</div>}
    </div>
  )
}

function Confirm({ pending, error, onConfirm, onCancel }: {
  pending: boolean; error: Error | null; onConfirm: () => void; onCancel: () => void
}) {
  return (
    <div className="space-y-3">
      {error && <ErrorMessage>{messageOf(error)}</ErrorMessage>}
      <div className="flex gap-2">
        <Button className="px-3 py-1.5" onClick={onConfirm} disabled={pending}>{pending ? 'Saving…' : 'Confirm'}</Button>
        <Button variant="secondary" className="px-3 py-1.5" onClick={onCancel} disabled={pending}>Cancel</Button>
      </div>
    </div>
  )
}

/** The coach's suggestion, asked for when the option opens; the suggested tasks start ticked. */
function CutScope({ goalId, suggest, onDone }: {
  goalId: number; suggest: ReturnType<typeof useSuggestCut>; onDone: () => void
}) {
  if (suggest.isError) {
    return (
      <div className="space-y-3">
        <ErrorMessage>
          {suggest.error instanceof ApiError && suggest.error.status === 502
            ? 'The coach couldn’t suggest anything right now. Please try again in a minute.'
            : messageOf(suggest.error)}
        </ErrorMessage>
        <div className="flex gap-2">
          <Button variant="secondary" className="px-3 py-1.5" onClick={() => suggest.mutate()}>Try again</Button>
          <Button variant="secondary" className="px-3 py-1.5" onClick={onDone}>Cancel</Button>
        </div>
      </div>
    )
  }
  if (suggest.isPending || !suggest.data) return <Spinner label="The coach is picking tasks, this can take up to a minute" />
  return <CutChoice goalId={goalId} suggestion={suggest.data} onDone={onDone} />
}

function CutChoice({ goalId, suggestion, onDone }: { goalId: number; suggestion: CutSuggestion; onDone: () => void }) {
  const cut = useCutScope(goalId)
  const [ticked, setTicked] = useState(() => new Set(suggestion.tasks.map(t => t.taskId)))
  const freed = suggestion.tasks.filter(t => ticked.has(t.taskId)).reduce((sum, t) => sum + t.minutes, 0)
  const flip = (id: number) => setTicked(prev => {
    const next = new Set(prev)
    if (!next.delete(id)) next.add(id)
    return next
  })

  return (
    <div className="space-y-3">
      <ul className="space-y-2">
        {suggestion.tasks.map(t => (
          <li key={t.taskId}>
            <label className="flex items-start gap-2 text-sm">
              <input type="checkbox" className="mt-1 accent-indigo-600" checked={ticked.has(t.taskId)}
                onChange={() => flip(t.taskId)} />
              <span>
                <span className="font-medium">{t.title}</span>
                <span className="text-slate-500"> · {t.minutes} min · {formatDay(t.scheduledDate)}</span>
                <span className="block text-xs text-slate-500">{t.reason}</span>
              </span>
            </label>
          </li>
        ))}
      </ul>
      <p className={`text-xs ${freed >= suggestion.minutesBehind ? 'text-emerald-700' : 'text-amber-700'}`}>
        Frees {freed} of the {suggestion.minutesBehind} minutes needed.
      </p>
      {cut.isError && <ErrorMessage>{messageOf(cut.error)}</ErrorMessage>}
      <div className="flex gap-2">
        <Button className="px-3 py-1.5" disabled={ticked.size === 0 || cut.isPending}
          onClick={() => cut.mutate([...ticked], { onSuccess: onDone })}>
          {cut.isPending ? 'Dropping…' : `Drop ${ticked.size} ${ticked.size === 1 ? 'task' : 'tasks'}`}
        </Button>
        <Button variant="secondary" className="px-3 py-1.5" onClick={onDone} disabled={cut.isPending}>Cancel</Button>
      </div>
    </div>
  )
}
