import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, call, type Schemas } from './client'
import { goalKeys } from './goals'

export type AdjustmentOptions = Schemas['AdjustmentOptions']
export type CutSuggestion = Schemas['CutSuggestion']

const keys = {
  options: (goalId: number) => [...goalKeys.detail(goalId), 'adjustments'] as const,
}

/** What each fix would change; only worth asking for when the goal is behind. */
export function useAdjustmentOptions(goalId: number, enabled: boolean) {
  return useQuery({
    queryKey: keys.options(goalId),
    queryFn: () => call(api.GET('/api/v1/goals/{goalId}/adjustments', { params: { path: { goalId } } })),
    enabled,
  })
}

// Each fix changes dates, daily time or tasks, so the whole goal is refreshed afterwards
function useAdjustment<T>(goalId: number, run: (body: T) => Promise<AdjustmentOptions>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: run,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: goalKeys.detail(goalId) }),
  })
}

export function useExtend(goalId: number) {
  return useAdjustment(goalId, (days: number) => call(api.POST('/api/v1/goals/{goalId}/adjustments/extend',
    { params: { path: { goalId } }, body: { days } })))
}

export function useMoreTime(goalId: number) {
  return useAdjustment(goalId, (minutesPerDay: number) => call(api.POST(
    '/api/v1/goals/{goalId}/adjustments/more-time', { params: { path: { goalId } }, body: { minutesPerDay } })))
}

export function useCutScope(goalId: number) {
  return useAdjustment(goalId, (taskIds: number[]) => call(api.POST('/api/v1/goals/{goalId}/adjustments/cut-scope',
    { params: { path: { goalId } }, body: { taskIds } })))
}

/** Asks the AI which upcoming tasks could go; changes nothing. Takes up to a minute. */
export function useSuggestCut(goalId: number) {
  return useMutation({
    mutationFn: () => call(api.POST('/api/v1/goals/{goalId}/adjustments/cut-scope/suggest',
      { params: { path: { goalId } } })),
  })
}
