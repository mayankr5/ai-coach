import { useParams } from 'react-router'
import { messageOf } from '../api/client'
import { useGoal } from '../api/goals'
import { useLastCheckIn, usePlan } from '../api/plan'
import { GeneratePlan } from '../components/goal/GeneratePlan'
import { Milestones } from '../components/goal/Milestones'
import { ProgressCard } from '../components/goal/ProgressCard'
import { TodayCard } from '../components/goal/TodayCard'
import { UpcomingDays } from '../components/goal/UpcomingDays'
import { ErrorMessage, Spinner } from '../components/ui'
import { formatDay, today, toIsoDate } from '../dates'

export function GoalPage() {
  const goalId = Number(useParams().goalId)
  const goal = useGoal(goalId)
  const plan = usePlan(goalId)
  const lastCheckIn = useLastCheckIn(goalId)

  if (goal.isPending || plan.isPending) return <Spinner label="Loading goal" />
  if (goal.isError) return <ErrorMessage>{messageOf(goal.error)}</ErrorMessage>
  if (plan.isError) return <ErrorMessage>{messageOf(plan.error)}</ErrorMessage>

  const g = goal.data
  const last = lastCheckIn.data
  return (
    <>
      <h1 className="text-2xl font-bold">{g.title}</h1>
      {g.description && <p className="mt-2 text-slate-600">{g.description}</p>}
      <p className="mt-3 text-sm text-slate-500">
        Deadline {formatDay(g.deadline)} · {g.minutesPerDay} min a day
        {g.extraMinutesPerDay > 0 && ` (+${g.extraMinutesPerDay} when catching up)`}
        {g.bufferDay && ` · ${g.bufferDay[0] + g.bufferDay.slice(1).toLowerCase()}s kept free`}
      </p>

      <div className="mt-8">
        {plan.data.milestones.length === 0 ? <GeneratePlan goalId={goalId} /> : (
          <div className="grid gap-6 lg:grid-cols-3">
            <div className="space-y-6 lg:col-span-2">
              {last?.coachMessage && (
                <section aria-label="Coach" className="rounded-2xl bg-indigo-600 p-5 text-white shadow-sm">
                  <p className="text-xs font-semibold uppercase tracking-wide text-indigo-200">
                    Coach · after {formatDay(last.date)}
                  </p>
                  <p className="mt-2 leading-relaxed">{last.coachMessage}</p>
                </section>
              )}
              <TodayCard goalId={goalId} checkedIn={last?.date === toIsoDate(today())} />
              <UpcomingDays goalId={goalId} bufferDay={g.bufferDay} />
            </div>
            <div className="space-y-6">
              <ProgressCard goalId={goalId} />
              <Milestones milestones={plan.data.milestones} />
            </div>
          </div>
        )}
      </div>
    </>
  )
}
