import { Stack, useLocalSearchParams } from 'expo-router'
import { StyleSheet, Text, View } from 'react-native'
import { messageOf } from '../../api/client'
import { useGoal } from '../../api/goals'
import { ErrorMessage, Screen, Spinner } from '../../components/ui'
import { deadlineLabel, formatDay } from '../../dates'
import { colors, space } from '../../theme'

// Placeholder: the plan, today's check-in and progress come in the next step
export default function GoalScreen() {
  const goalId = Number(useLocalSearchParams<{ id: string }>().id)
  const goal = useGoal(goalId)

  return (
    <Screen>
      <Stack.Screen options={{ title: goal.data?.title ?? 'Goal' }} />
      {goal.isPending && <Spinner label="Loading goal" />}
      {goal.isError && <ErrorMessage>{messageOf(goal.error)}</ErrorMessage>}
      {goal.data && (
        <View style={styles.container}>
          <Text style={styles.title}>{goal.data.title}</Text>
          {goal.data.description && <Text style={styles.text}>{goal.data.description}</Text>}
          <Text style={styles.muted}>
            Deadline {formatDay(goal.data.deadline)} ({deadlineLabel(goal.data.deadline)}) · {goal.data.minutesPerDay} min
            a day
          </Text>
          <Text style={styles.muted}>Your plan, check-in and progress will show up here.</Text>
        </View>
      )}
    </Screen>
  )
}

const styles = StyleSheet.create({
  container: { gap: space.md },
  title: { fontSize: 22, fontWeight: '700', color: colors.text },
  text: { fontSize: 15, color: colors.text },
  muted: { fontSize: 14, color: colors.textMuted },
})
