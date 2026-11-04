import { router, Stack } from 'expo-router'
import { useState } from 'react'
import { Pressable, StyleSheet, Text, View } from 'react-native'
import { ApiError, messageOf } from '../../api/client'
import { useCreateGoal, type ExperienceLevel, type Weekday } from '../../api/goals'
import { DeadlinePicker } from '../../components/DeadlinePicker'
import { Button, ChoiceGroup, ErrorMessage, Field, Screen, type Choice } from '../../components/ui'
import { addDays, addMonths, today, toIsoDate } from '../../dates'
import { colors, space } from '../../theme'

const LEVELS: Choice<ExperienceLevel>[] = [
  { value: 'BEGINNER', label: 'Beginner' },
  { value: 'INTERMEDIATE', label: 'Intermediate' },
  { value: 'ADVANCED', label: 'Advanced' },
]
const OTHER = 'other'
const MINUTES: Choice<number | typeof OTHER>[] = [
  ...[30, 45, 60, 90, 120].map(m => ({ value: m, label: String(m), spoken: `${m} minutes a day` })),
  { value: OTHER, label: 'Other', spoken: 'Other minutes a day' },
]
const EXTRA = [0, 15, 30]
const WEEKDAYS: Choice<Weekday>[] = (['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'] as const)
  .map(day => ({ value: day, label: day[0], spoken: day[0] + day.slice(1).toLowerCase() }))

/** The backend allows at most half the daily time as extra catch-up time. */
const maxExtra = (minutesPerDay: number) => Math.floor(minutesPerDay / 2)

export default function NewGoalScreen() {
  const createGoal = useCreateGoal()
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [deadline, setDeadline] = useState(() => toIsoDate(addMonths(today(), 3)))
  const [level, setLevel] = useState<ExperienceLevel>('BEGINNER')
  const [minutesChoice, setMinutesChoice] = useState<number | typeof OTHER>(60)
  const [otherMinutes, setOtherMinutes] = useState('')
  const [extra, setExtra] = useState(0)
  const [bufferDay, setBufferDay] = useState<Weekday>('SUNDAY')
  const [checked, setChecked] = useState(false)

  const minutesPerDay = minutesChoice === OTHER ? Number(otherMinutes) : minutesChoice
  const minutesValid = Number.isInteger(minutesPerDay) && minutesPerDay >= 10 && minutesPerDay <= 600
  const extraChoices = EXTRA.filter(m => !minutesValid || m <= maxExtra(minutesPerDay))
    .map(m => ({ value: m, label: String(m), spoken: `${m} extra minutes` }))
  const serverErrors = createGoal.error instanceof ApiError ? createGoal.error.fieldErrors : {}
  const formError = createGoal.isError && Object.keys(serverErrors).length === 0 ? messageOf(createGoal.error) : null
  const errors: Record<string, string> = {
    ...serverErrors,
    ...(checked && !title.trim() && { title: 'Say what you want to achieve' }),
    ...(checked && !minutesValid && { minutesPerDay: 'Between 10 and 600 minutes' }),
  }

  // Less daily time can make the chosen extra time too much
  function chooseMinutes(choice: number | typeof OTHER, other = otherMinutes) {
    setMinutesChoice(choice)
    setOtherMinutes(other)
    const minutes = choice === OTHER ? Number(other) : choice
    if (minutes >= 10 && extra > maxExtra(minutes)) setExtra(0)
  }

  function submit() {
    setChecked(true)
    if (!title.trim() || !minutesValid) return
    createGoal.mutate({
      title: title.trim(),
      description: description.trim() || undefined,
      deadline,
      minutesPerDay,
      extraMinutesPerDay: extra,
      bufferDay,
      experienceLevel: level,
    }, { onSuccess: goal => router.replace(`/goals/${goal.id}`) })
  }

  return (
    <Screen>
      <Stack.Screen options={{
        title: 'New goal',
        headerLeft: () => (
          <Pressable accessibilityRole="button" hitSlop={8} onPress={() => router.back()}>
            <Text style={styles.cancel}>Cancel</Text>
          </Pressable>
        ),
      }} />
      <View style={styles.form}>
        <Text style={styles.intro}>Be honest about your time: a plan you can keep beats an ambitious one.</Text>
        {formError && <ErrorMessage>{formError}</ErrorMessage>}
        <Field label="What do you want to achieve?" value={title} onChangeText={setTitle} maxLength={200}
          placeholder="Get a Java backend job" error={errors.title} />
        <Field label="Details" hint="(optional)" value={description} onChangeText={setDescription} maxLength={2000}
          placeholder="What you know already, what you want to build…" multiline style={styles.multiline}
          error={errors.description} />
        <DeadlinePicker value={deadline} minimumDate={addDays(today(), 1)} onChange={setDeadline}
          error={errors.deadline} />
        <ChoiceGroup label="Experience" choices={LEVELS} value={level} onChange={setLevel}
          error={errors.experienceLevel} />
        <ChoiceGroup label="Minutes per day" choices={MINUTES} value={minutesChoice}
          onChange={choice => chooseMinutes(choice)} error={minutesChoice === OTHER ? undefined : errors.minutesPerDay} />
        {minutesChoice === OTHER && (
          <Field label="Your minutes per day" hint="(10–600)" value={otherMinutes} keyboardType="number-pad"
            maxLength={3} onChangeText={text => chooseMinutes(OTHER, text.replace(/\D/g, ''))}
            error={errors.minutesPerDay} />
        )}
        <ChoiceGroup label="Extra minutes for catching up" hint={minutesValid ? `(up to ${maxExtra(minutesPerDay)})` : undefined}
          choices={extraChoices} value={extra} onChange={setExtra} error={errors.extraMinutesPerDay} />
        <ChoiceGroup label="Weekly buffer day" hint="(nothing planned, kept for catching up)" choices={WEEKDAYS}
          value={bufferDay} onChange={setBufferDay} error={errors.bufferDay} />
        <Button title={createGoal.isPending ? 'Creating…' : 'Create goal'} onPress={submit} busy={createGoal.isPending} />
      </View>
    </Screen>
  )
}

const styles = StyleSheet.create({
  form: { gap: space.xl, paddingBottom: space.xl },
  intro: { fontSize: 14, color: colors.textMuted },
  multiline: { minHeight: 88, paddingTop: space.md, textAlignVertical: 'top' },
  cancel: { fontSize: 17, color: colors.primary },
})
