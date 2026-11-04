import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, call, type Schemas } from './client'

export type GoalStatus = Schemas['GoalSummary']['status']
export type NewGoal = Schemas['CreateGoalRequest']

export const goalKeys = {
  all: ['goals'] as const,
  list: (status: GoalStatus) => ['goals', 'list', status] as const,
  detail: (id: number) => ['goals', id] as const,
}

export function useGoals(status: GoalStatus) {
  return useQuery({
    queryKey: goalKeys.list(status),
    queryFn: () => call(api.GET('/api/v1/goals', { params: { query: { status, size: 50 } } })),
  })
}

export function useGoal(id: number) {
  return useQuery({
    queryKey: goalKeys.detail(id),
    queryFn: () => call(api.GET('/api/v1/goals/{id}', { params: { path: { id } } })),
  })
}

export function useCreateGoal() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (goal: NewGoal) => call(api.POST('/api/v1/goals', { body: goal })),
    onSuccess: goal => {
      queryClient.setQueryData(goalKeys.detail(goal.id), goal)
      return queryClient.invalidateQueries({ queryKey: goalKeys.all })
    },
  })
}
