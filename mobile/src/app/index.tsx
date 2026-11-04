import { router, Stack } from 'expo-router'
import { useState } from 'react'
import { FlatList, Pressable, RefreshControl, StyleSheet, Text, View } from 'react-native'
import { messageOf } from '../api/client'
import { useGoals, type GoalStatus } from '../api/goals'
import { useAuth } from '../auth/useAuth'
import { SavedDataNote } from '../components/SavedDataNote'
import { Button, ErrorMessage, Segmented, Spinner, type Choice } from '../components/ui'
import { deadlineLabel, formatDay } from '../dates'
import { colors, radius, space } from '../theme'

const TABS: Choice<GoalStatus>[] = [
  { value: 'ACTIVE', label: 'Active' },
  { value: 'COMPLETED', label: 'Completed' },
  { value: 'ARCHIVED', label: 'Archived' },
]

export default function GoalsScreen() {
  const [status, setStatus] = useState<GoalStatus>('ACTIVE')
  const goals = useGoals(status)
  const newGoal = () => router.push('/goals/new')

  return (
    <View style={styles.screen}>
      <Stack.Screen options={{
        title: 'Your goals',
        headerLeft: () => <AccountButton />,
        headerRight: () => (
          <Pressable accessibilityRole="button" accessibilityLabel="New goal" hitSlop={12} onPress={newGoal}>
            <Text style={styles.plus}>+</Text>
          </Pressable>
        ),
      }} />
      <FlatList
        // iOS headers float over the screen; this keeps the first rows below them
        contentInsetAdjustmentBehavior="automatic"
        data={goals.data?.content ?? []}
        keyExtractor={goal => String(goal.id)}
        contentContainerStyle={styles.list}
        refreshControl={<RefreshControl refreshing={goals.isRefetching} onRefresh={() => void goals.refetch()}
          tintColor={colors.primary} />}
        ListHeaderComponent={
          <View style={styles.header}>
            <Segmented label="Goal status" choices={TABS} value={status} onChange={setStatus} />
            <SavedDataNote queries={[goals]} />
            {goals.isError && !goals.data && <ErrorMessage>{messageOf(goals.error)}</ErrorMessage>}
          </View>
        }
        ListEmptyComponent={goals.isPending ? <Spinner label="Loading goals" />
          : goals.isSuccess ? <EmptyState active={status === 'ACTIVE'} onCreate={newGoal} /> : undefined}
        renderItem={({ item: goal }) => (
          <Pressable accessibilityRole="button" onPress={() => router.push(`/goals/${goal.id}`)}
            style={({ pressed }) => [styles.card, pressed && styles.cardPressed]}>
            <Text style={styles.title}>{goal.title}</Text>
            <Text style={styles.muted}>Deadline {formatDay(goal.deadline)} · {deadlineLabel(goal.deadline)}</Text>
          </Pressable>
        )}
      />
    </View>
  )
}

/** The user's initial; opens settings (reminder, log out). */
function AccountButton() {
  const { user } = useAuth()
  if (!user) return null
  return (
    <Pressable accessibilityRole="button" accessibilityLabel="Settings" hitSlop={8}
      onPress={() => router.push('/settings')} style={styles.avatar}>
      <Text style={styles.avatarText}>{user.displayName.charAt(0).toUpperCase()}</Text>
    </Pressable>
  )
}

function EmptyState({ active, onCreate }: { active: boolean; onCreate: () => void }) {
  return (
    <View style={styles.empty}>
      <Text style={styles.emptyTitle}>{active ? 'No active goals yet' : 'Nothing here'}</Text>
      {active && (
        <>
          <Text style={[styles.muted, styles.center]}>
            Set a goal and the coach turns it into a realistic daily plan.
          </Text>
          <Button title="Create your first goal" onPress={onCreate} />
        </>
      )}
    </View>
  )
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.background },
  list: { padding: space.lg, gap: space.md, flexGrow: 1 },
  header: { gap: space.md, marginBottom: space.xs },
  plus: { fontSize: 30, lineHeight: 32, color: colors.primary, fontWeight: '300' },
  avatar: {
    width: 32, height: 32, borderRadius: 16, alignItems: 'center', justifyContent: 'center',
    backgroundColor: colors.primary,
  },
  avatarText: { color: colors.onPrimary, fontWeight: '700' },
  card: {
    padding: space.lg, gap: space.xs, borderRadius: radius.lg, backgroundColor: colors.surface,
    borderWidth: 1, borderColor: colors.divider,
  },
  cardPressed: { backgroundColor: colors.background },
  title: { fontSize: 16, fontWeight: '600', color: colors.text },
  muted: { fontSize: 14, color: colors.textMuted },
  center: { textAlign: 'center' },
  empty: {
    marginTop: space.xl, padding: space.xl, gap: space.md, alignItems: 'stretch', borderRadius: radius.lg,
    borderWidth: 2, borderStyle: 'dashed', borderColor: colors.divider,
  },
  emptyTitle: { fontSize: 16, fontWeight: '600', color: colors.text, textAlign: 'center' },
})
