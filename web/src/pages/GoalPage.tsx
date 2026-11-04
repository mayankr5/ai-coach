import { useParams } from 'react-router'
import { messageOf } from '../api/client'
import { useGoal } from '../api/goals'
import { ErrorMessage, Spinner } from '../components/ui'
import { formatDay } from '../dates'

/** The goal's details for now; the plan, today's tasks and check-ins come in the next step. */
export function GoalPage() {
  const goalId = Number(useParams().goalId)
  const goal = useGoal(goalId)

  if (goal.isPending) return <Spinner label="Loading goal" />
  if (goal.isError) return <ErrorMessage>{messageOf(goal.error)}</ErrorMessage>

  const g = goal.data
  return (
    <>
      <h1 className="text-2xl font-bold">{g.title}</h1>
      {g.description && <p className="mt-2 text-slate-600">{g.description}</p>}
      <p className="mt-3 text-sm text-slate-500">
        Deadline {formatDay(g.deadline)} · {g.minutesPerDay} min a day
        {g.extraMinutesPerDay > 0 && ` (+${g.extraMinutesPerDay} when catching up)`}
        {g.bufferDay && ` · ${g.bufferDay[0] + g.bufferDay.slice(1).toLowerCase()}s kept free`}
      </p>
    </>
  )
}
