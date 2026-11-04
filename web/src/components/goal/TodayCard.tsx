import { useState, type FormEvent } from 'react'
import { messageOf } from '../../api/client'
import { useCheckIn, useTasks, type Task, type TaskStatus } from '../../api/plan'
import { today, toIsoDate } from '../../dates'
import { Button, ErrorMessage, Spinner } from '../ui'
import { Card } from './Card'

type Result = Exclude<TaskStatus, 'PENDING'>

const RESULTS: { value: Result; label: string; pressed: string }[] = [
  { value: 'DONE', label: 'Done', pressed: 'aria-pressed:bg-emerald-600 aria-pressed:text-white' },
  { value: 'PARTIAL', label: 'Partly', pressed: 'aria-pressed:bg-amber-500 aria-pressed:text-white' },
  { value: 'SKIPPED', label: 'Skipped', pressed: 'aria-pressed:bg-slate-600 aria-pressed:text-white' },
]

/**
 * Today's tasks: mark each one, add a note, and check in. The backend re-plans and the coach's reply shows up
 * with the rest of the goal page, which reloads after a check-in.
 */
export function TodayCard({ goalId, checkedIn }: { goalId: number; checkedIn: boolean }) {
  const day = toIsoDate(today())
  const tasks = useTasks(goalId, day, day)

  return (
    <Card title="Today">
      {tasks.isPending && <Spinner label="Loading today's tasks" />}
      {tasks.isError && <ErrorMessage>{messageOf(tasks.error)}</ErrorMessage>}
      {tasks.data && (
        // Remounts with fresh choices whenever the task list changes
        <CheckInForm key={tasks.data.map(t => `${t.id}:${t.status}`).join()} goalId={goalId} day={day}
          tasks={tasks.data} checkedIn={checkedIn} />
      )}
    </Card>
  )
}

function CheckInForm({ goalId, day, tasks, checkedIn }:
  { goalId: number; day: string; tasks: Task[]; checkedIn: boolean }) {
  const checkIn = useCheckIn(goalId)
  const [results, setResults] = useState<Record<number, Result>>(() =>
    Object.fromEntries(tasks.filter(t => t.status !== 'PENDING').map(t => [t.id, t.status as Result])))
  const [note, setNote] = useState('')
  const marked = Object.keys(results).length
  // Every task needs a result: once a day has a check-in, nothing marks its leftovers as skipped
  const ready = tasks.length > 0 ? marked === tasks.length : note.trim() !== ''
  const totalMinutes = tasks.reduce((sum, t) => sum + t.estimatedMinutes, 0)

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    checkIn.mutate({
      date: day,
      tasks: Object.entries(results).map(([taskId, status]) => ({ taskId: Number(taskId), status })),
      note: note.trim() || undefined,
    })
  }

  return (
    <form onSubmit={onSubmit} className="space-y-4">
      {tasks.length === 0 ? (
        <p className="text-sm text-slate-500">
          Nothing planned today. Rest, or use the time to catch up; you can still leave a note for the coach.
        </p>
      ) : (
        <>
          <p className="text-sm text-slate-500">{totalMinutes} minutes planned. How did it go?</p>
          <ul className="divide-y divide-slate-100">
            {tasks.map(task => (
              <li key={task.id} className="flex flex-col gap-2 py-3 sm:flex-row sm:items-center sm:justify-between">
                <div>
                  <p className="text-sm font-medium">{task.title}</p>
                  <p className="text-xs text-slate-500">{task.estimatedMinutes} min</p>
                </div>
                <div role="group" aria-label={`Result for ${task.title}`} className="flex shrink-0 gap-1">
                  {RESULTS.map(r => (
                    <button
                      key={r.value}
                      type="button"
                      aria-pressed={results[task.id] === r.value}
                      onClick={() => setResults(prev => ({ ...prev, [task.id]: r.value }))}
                      className={`rounded-md px-2.5 py-1 text-xs font-medium text-slate-600 ring-1 ring-slate-200
                        hover:bg-slate-50 aria-pressed:ring-0 ${r.pressed}`}
                    >
                      {r.label}
                    </button>
                  ))}
                </div>
              </li>
            ))}
          </ul>
        </>
      )}

      <div>
        <label htmlFor="note" className="block text-sm font-medium text-slate-700">
          Note for the coach <span className="font-normal text-slate-400">(optional)</span>
        </label>
        <textarea id="note" rows={2} maxLength={2000} value={note} onChange={e => setNote(e.target.value)}
          placeholder="What got in the way, or what went well?"
          className="mt-1 block w-full rounded-lg border border-slate-300 px-3 py-2 text-sm shadow-sm
            focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/30" />
      </div>

      {checkIn.isError && <ErrorMessage>{messageOf(checkIn.error)}</ErrorMessage>}
      <div className="flex items-center justify-between gap-3">
        <p className="text-xs text-slate-500">
          {tasks.length > 0 && !ready && `Mark every task to check in (${marked} of ${tasks.length}).`}
        </p>
        <Button type="submit" disabled={checkIn.isPending || !ready}>
          {checkIn.isPending ? 'The coach is adjusting your plan…' : checkedIn ? 'Update check-in' : 'Check in'}
        </Button>
      </div>
    </form>
  )
}
