import { StyleSheet, Text, View } from 'react-native'
import { ApiError, messageOf } from '../../api/client'
import { useGeneratePlan } from '../../api/plan'
import { colors, radius, space } from '../../theme'
import { Button, ErrorMessage } from '../ui'

export function GeneratePlan({ goalId }: { goalId: number }) {
  const generate = useGeneratePlan(goalId)
  return (
    <View style={styles.card}>
      <Text style={styles.title}>Let the coach plan it</Text>
      <Text style={styles.text}>
        You get milestones up to your deadline and concrete tasks for the next two weeks, sized to your daily time.
        New weeks are added as you go.
      </Text>
      {generate.isError && (
        <ErrorMessage>
          {generate.error instanceof ApiError && generate.error.status === 502
            ? 'The AI couldn’t make a plan right now (it may be busy). Please try again in a minute.'
            : messageOf(generate.error)}
        </ErrorMessage>
      )}
      <Button title={generate.isPending ? 'Planning… this can take up to a minute' : 'Generate my plan'}
        busy={generate.isPending} onPress={() => generate.mutate()} />
    </View>
  )
}

const styles = StyleSheet.create({
  card: {
    padding: space.xl, gap: space.md, borderRadius: radius.xl, backgroundColor: colors.surface, borderWidth: 1,
    borderColor: colors.divider,
  },
  title: { fontSize: 18, fontWeight: '600', color: colors.text, textAlign: 'center' },
  text: { fontSize: 14, lineHeight: 20, color: colors.textMuted, textAlign: 'center' },
})
