import { ApiError, messageOf } from '../../api/client'
import { useGeneratePlan } from '../../api/plan'
import { Button, ErrorMessage } from '../ui'

export function GeneratePlan({ goalId }: { goalId: number }) {
  const generate = useGeneratePlan(goalId)
  return (
    <section className="rounded-2xl bg-white p-8 text-center shadow-sm ring-1 ring-slate-200">
      <h2 className="text-lg font-semibold">Let the coach plan it</h2>
      <p className="mx-auto mt-2 max-w-md text-sm text-slate-500">
        You get milestones up to your deadline and concrete tasks for the next two weeks, sized to your daily time.
        New weeks are added as you go.
      </p>
      {generate.isError && (
        <div className="mx-auto mt-4 max-w-md text-left">
          <ErrorMessage>
            {generate.error instanceof ApiError && generate.error.status === 502
              ? 'The AI couldn’t make a plan right now (it may be busy). Please try again in a minute.'
              : messageOf(generate.error)}
          </ErrorMessage>
        </div>
      )}
      <Button className="mt-6" onClick={() => generate.mutate()} disabled={generate.isPending}>
        {generate.isPending ? 'Planning… this can take up to a minute' : 'Generate my plan'}
      </Button>
    </section>
  )
}
