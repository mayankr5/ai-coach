import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router'
import { ApiError, messageOf } from '../api/client'
import { useCreateGoal, type NewGoal } from '../api/goals'
import { Button, ErrorMessage, Field, LinkButton, Select, TextArea } from '../components/ui'
import { addDays, today, toIsoDate } from '../dates'

const WEEKDAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'] as const
const LEVELS: { value: NewGoal['experienceLevel']; label: string }[] = [
  { value: 'BEGINNER', label: 'Beginner' },
  { value: 'INTERMEDIATE', label: 'Intermediate' },
  { value: 'ADVANCED', label: 'Advanced' },
]

export function NewGoalPage() {
  const navigate = useNavigate()
  const createGoal = useCreateGoal()
  const [minutesPerDay, setMinutesPerDay] = useState(60)
  const maxExtra = Math.floor(minutesPerDay / 2)
  const fieldErrors = createGoal.error instanceof ApiError ? createGoal.error.fieldErrors : {}
  const formError = createGoal.isError && Object.keys(fieldErrors).length === 0 ? messageOf(createGoal.error) : null

  function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const description = String(form.get('description')).trim()
    createGoal.mutate({
      title: String(form.get('title')).trim(),
      description: description || undefined,
      deadline: String(form.get('deadline')),
      minutesPerDay,
      extraMinutesPerDay: Number(form.get('extraMinutesPerDay')),
      bufferDay: form.get('bufferDay') as NewGoal['bufferDay'],
      experienceLevel: form.get('experienceLevel') as NewGoal['experienceLevel'],
    }, { onSuccess: goal => navigate(`/goals/${goal.id}`) })
  }

  return (
    <div className="mx-auto max-w-xl">
      <h1 className="text-2xl font-bold">New goal</h1>
      <p className="mt-1 text-sm text-slate-500">Be honest about your time: a plan you can keep beats an ambitious one.</p>

      <form onSubmit={onSubmit} className="mt-6 space-y-5 rounded-2xl bg-white p-6 shadow-sm ring-1 ring-slate-200">
        {formError && <ErrorMessage>{formError}</ErrorMessage>}
        <Field label="What do you want to achieve?" name="title" required maxLength={200}
          placeholder="Get a Java backend job" error={fieldErrors.title} />
        <TextArea label="Details" hint="(optional)" name="description" rows={3} maxLength={2000}
          placeholder="What you know already, what you want to build…" error={fieldErrors.description} />
        <div className="grid gap-5 sm:grid-cols-2">
          <Field label="Deadline" name="deadline" type="date" required min={toIsoDate(addDays(today(), 1))}
            error={fieldErrors.deadline} />
          <Select label="Experience" name="experienceLevel" defaultValue="BEGINNER" error={fieldErrors.experienceLevel}>
            {LEVELS.map(l => <option key={l.value} value={l.value}>{l.label}</option>)}
          </Select>
          <Field label="Minutes per day" name="minutesPerDay" type="number" required min={10} max={600}
            value={minutesPerDay} onChange={e => setMinutesPerDay(Number(e.target.value))}
            error={fieldErrors.minutesPerDay} />
          <Field label="Extra minutes for catching up" hint={`(up to ${maxExtra})`} name="extraMinutesPerDay"
            type="number" min={0} max={maxExtra} defaultValue={0} error={fieldErrors.extraMinutesPerDay} />
        </div>
        <Select label="Weekly buffer day" hint="(no planned tasks, kept for catching up)" name="bufferDay"
          defaultValue="SUNDAY" error={fieldErrors.bufferDay}>
          {WEEKDAYS.map(d => <option key={d} value={d}>{d[0] + d.slice(1).toLowerCase()}</option>)}
        </Select>
        <div className="flex justify-end gap-3 pt-2">
          <LinkButton to="/" variant="secondary">Cancel</LinkButton>
          <Button type="submit" disabled={createGoal.isPending}>
            {createGoal.isPending ? 'Creating…' : 'Create goal'}
          </Button>
        </div>
      </form>
    </div>
  )
}
