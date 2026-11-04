import { useState } from 'react'
import { Link } from 'react-router'
import { messageOf } from '../api/client'
import { useGoals, type GoalStatus } from '../api/goals'
import { ErrorMessage, LinkButton, Spinner } from '../components/ui'
import { daysUntil, formatDay } from '../dates'

const TABS: { status: GoalStatus; label: string }[] = [
  { status: 'ACTIVE', label: 'Active' },
  { status: 'COMPLETED', label: 'Completed' },
  { status: 'ARCHIVED', label: 'Archived' },
]

export function GoalsPage() {
  const [status, setStatus] = useState<GoalStatus>('ACTIVE')
  const goals = useGoals(status)

  return (
    <>
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">Your goals</h1>
        <LinkButton to="/goals/new">New goal</LinkButton>
      </div>

      <nav className="mt-6 flex gap-1 border-b border-slate-200" aria-label="Goal status">
        {TABS.map(tab => (
          <button
            key={tab.status}
            onClick={() => setStatus(tab.status)}
            aria-current={tab.status === status ? 'page' : undefined}
            className="-mb-px border-b-2 border-transparent px-3 py-2 text-sm font-medium text-slate-500
              hover:text-slate-800 aria-[current=page]:border-indigo-600 aria-[current=page]:text-indigo-600"
          >
            {tab.label}
          </button>
        ))}
      </nav>

      <div className="mt-6">
        {goals.isPending && <Spinner label="Loading goals" />}
        {goals.isError && <ErrorMessage>{messageOf(goals.error)}</ErrorMessage>}
        {goals.data?.content.length === 0 && <EmptyState active={status === 'ACTIVE'} />}
        <ul className="grid gap-3 sm:grid-cols-2">
          {goals.data?.content.map(goal => (
            <li key={goal.id}>
              <Link
                to={`/goals/${goal.id}`}
                className="block rounded-xl bg-white p-4 shadow-sm ring-1 ring-slate-200 hover:ring-indigo-300"
              >
                <p className="font-semibold">{goal.title}</p>
                <p className="mt-1 text-sm text-slate-500">
                  Deadline {formatDay(goal.deadline)} · {deadlineLabel(goal.deadline)}
                </p>
              </Link>
            </li>
          ))}
        </ul>
      </div>
    </>
  )
}

function deadlineLabel(deadline: string): string {
  const days = daysUntil(deadline)
  if (days < 0) return 'passed'
  if (days === 0) return 'today'
  return days === 1 ? '1 day left' : `${days} days left`
}

function EmptyState({ active }: { active: boolean }) {
  return (
    <div className="rounded-xl border-2 border-dashed border-slate-200 p-10 text-center">
      <p className="font-medium">{active ? 'No active goals yet' : 'Nothing here'}</p>
      {active && (
        <>
          <p className="mt-1 text-sm text-slate-500">Set a goal and the coach turns it into a realistic daily plan.</p>
          <LinkButton to="/goals/new" className="mt-4">Create your first goal</LinkButton>
        </>
      )}
    </div>
  )
}
