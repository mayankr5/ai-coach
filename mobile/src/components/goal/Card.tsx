import type { ReactNode } from 'react'
import { StyleSheet, Text, View } from 'react-native'
import { colors, radius, space } from '../../theme'

/** A titled section of the goal screen. */
export function Card({ title, children }: { title: string; children: ReactNode }) {
  return (
    <View style={styles.card}>
      <Text accessibilityRole="header" style={styles.title}>{title}</Text>
      <View style={styles.body}>{children}</View>
    </View>
  )
}

const styles = StyleSheet.create({
  card: {
    padding: space.lg, borderRadius: radius.xl, backgroundColor: colors.surface, borderWidth: 1,
    borderColor: colors.divider,
  },
  title: { fontSize: 17, fontWeight: '600', color: colors.text },
  body: { marginTop: space.md, gap: space.md },
})
