import { useQueryClient } from '@tanstack/react-query'
import { Stack, useLocalSearchParams } from 'expo-router'
import { StyleSheet, Text, View } from 'react-native'
import { messageOf } from '../../../api/client'
import { goalKeys, useGoal } from '../../../api/goals'
import { useLastCheckIn, usePlan, useProgress } from '../../../api/plan'
import { BackOnTrack } from '../../../components/goal/BackOnTrack'
import { GeneratePlan } from '../../../components/goal/GeneratePlan'
import { Milestones } from '../../../components/goal/Milestones'
import { ProgressCard } from '../../../components/goal/ProgressCard'
import { TodayCard } from '../../../components/goal/TodayCard'
import { UpcomingDays } from '../../../components/goal/UpcomingDays'
import { SavedDataNote } from '../../../components/SavedDataNote'
import { ErrorMessage, Screen, Spinner } from '../../../components/ui'
import { deadlineLabel, formatDay, today, toIsoDate } from '../../../dates'
import { colors, radius, space } from '../../../theme'

/** The daily screen: the coach's last reply, today's check-in, then the week, progress and milestones. */
export default function GoalScreen() {
  const goalId = Number(useLocalSearchParams<{ id: string }>().id)
  const queryClient = useQueryClient()
  const goal = useGoal(goalId)
  const plan = usePlan(goalId)
  const lastCheckIn = useLastCheckIn(goalId)
  const progress = useProgress(goalId)
  const refresh = () => queryClient.invalidateQueries({ queryKey: goalKeys.detail(goalId) })

  const g = goal.data
  const last = lastCheckIn.data
  return (
    <Screen onRefresh={refresh}>
      <Stack.Screen options={{ title: g?.title ?? 'Goal' }} />
      <SavedDataNote queries={[goal, plan, lastCheckIn, progress]} />
      {(goal.isPending || plan.isPending) && <Spinner label="Loading goal" />}
      {goal.isError && !g && <ErrorMessage>{messageOf(goal.error)}</ErrorMessage>}
      {plan.isError && !plan.data && <ErrorMessage>{messageOf(plan.error)}</ErrorMessage>}
      {g && plan.data && (
        <View style={styles.sections}>
          <View style={styles.info}>
            <Text style={styles.title}>{g.title}</Text>
            {g.description && <Text style={styles.text}>{g.description}</Text>}
            <Text style={styles.muted}>
              Deadline {formatDay(g.deadline)} ({deadlineLabel(g.deadline)}) · {g.minutesPerDay} min a day
              {g.extraMinutesPerDay > 0 && ` (+${g.extraMinutesPerDay} when catching up)`}
              {g.bufferDay && ` · ${g.bufferDay[0] + g.bufferDay.slice(1).toLowerCase()}s kept free`}
            </Text>
          </View>

          {plan.data.milestones.length === 0 ? <GeneratePlan goalId={goalId} /> : (
            <>
              {last?.coachMessage && (
                <View accessibilityLabel="Coach" style={styles.coach}>
                  <Text style={styles.coachLabel}>COACH · AFTER {formatDay(last.date).toUpperCase()}</Text>
                  <Text style={styles.coachText}>{last.coachMessage}</Text>
                </View>
              )}
              <TodayCard goalId={goalId} checkedIn={last?.date === toIsoDate(today())} />
              <UpcomingDays goalId={goalId} bufferDay={g.bufferDay} />
              <ProgressCard goalId={goalId} />
              <BackOnTrack goalId={goalId} />
              <Milestones milestones={plan.data.milestones} />
            </>
          )}
        </View>
      )}
    </Screen>
  )
}

const styles = StyleSheet.create({
  sections: { gap: space.lg, paddingTop: space.sm, paddingBottom: space.xl },
  info: { gap: space.sm },
  title: { fontSize: 22, fontWeight: '700', color: colors.text },
  text: { fontSize: 15, color: colors.text },
  muted: { fontSize: 13, lineHeight: 18, color: colors.textMuted },
  coach: { padding: space.lg, gap: space.sm, borderRadius: radius.xl, backgroundColor: colors.primary },
  coachLabel: { fontSize: 12, fontWeight: '600', letterSpacing: 0.5, color: colors.coachMuted },
  coachText: { fontSize: 15, lineHeight: 22, color: colors.onPrimary },
})
