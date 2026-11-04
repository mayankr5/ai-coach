import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, call, type Schemas } from './client'
import { goalKeys } from './goals'

export type Task = Schemas['TaskResponse']
export type TaskStatus = Task['status']
export type CheckInResult = Schemas['CheckInResponse']

// Everything about a goal sits under its key, so one invalidation after a change refreshes the whole page
const keys = {
  plan: (goalId: number) => [...goalKeys.detail(goalId), 'plan'] as const,
  tasks: (goalId: number, from: string, to: string) => [...goalKeys.detail(goalId), 'tasks', from, to] as const,
  progress: (goalId: number) => [...goalKeys.detail(goalId), 'progress'] as const,
  lastCheckIn: (goalId: number) => [...goalKeys.detail(goalId), 'check-ins', 'last'] as const,
}

export function usePlan(goalId: number) {
  return useQuery({
    queryKey: keys.plan(goalId),
    queryFn: () => call(api.GET('/api/v1/goals/{goalId}/plan', { params: { path: { goalId } } })),
  })
}

export function useTasks(goalId: number, from: string, to: string) {
  return useQuery({
    queryKey: keys.tasks(goalId, from, to),
    queryFn: () => call(api.GET('/api/v1/goals/{goalId}/tasks', { params: { path: { goalId }, query: { from, to } } })),
  })
}

export function useProgress(goalId: number) {
  return useQuery({
    queryKey: keys.progress(goalId),
    queryFn: () => call(api.GET('/api/v1/goals/{goalId}/progress', { params: { path: { goalId } } })),
  })
}

export function useLastCheckIn(goalId: number) {
  return useQuery({
    queryKey: keys.lastCheckIn(goalId),
    queryFn: async () => {
      const page = await call(api.GET('/api/v1/goals/{goalId}/check-ins',
        { params: { path: { goalId }, query: { size: 1 } } }))
      return page.content[0] ?? null
    },
  })
}

/** Asks the AI for a plan; takes up to a minute. */
export function useGeneratePlan(goalId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => call(api.POST('/api/v1/goals/{goalId}/plan', { params: { path: { goalId } } })),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: goalKeys.detail(goalId) }),
  })
}

/** Records the day's results; the backend re-plans and the coach replies. */
export function useCheckIn(goalId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: Schemas['CheckInRequest']) =>
      call(api.POST('/api/v1/goals/{goalId}/check-ins', { params: { path: { goalId } }, body })),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: goalKeys.detail(goalId) }),
  })
}
