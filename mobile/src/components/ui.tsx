import type { ReactNode } from 'react'
import {
  ActivityIndicator, KeyboardAvoidingView, Platform, Pressable, ScrollView, StyleSheet, Text, TextInput, View,
  type TextInputProps,
} from 'react-native'
import { SafeAreaView, type Edge } from 'react-native-safe-area-context'
import { colors, radius, space } from '../theme'

type Variant = 'primary' | 'secondary'

export function Button({ title, onPress, variant = 'primary', disabled, busy }: {
  title: string; onPress: () => void; variant?: Variant; disabled?: boolean; busy?: boolean
}) {
  const primary = variant === 'primary'
  return (
    <Pressable accessibilityRole="button" accessibilityState={{ disabled: disabled || busy, busy }}
      disabled={disabled || busy} onPress={onPress}
      style={({ pressed }) => [styles.button, primary ? styles.primary : styles.secondary,
        pressed && (primary ? styles.primaryPressed : styles.secondaryPressed), (disabled || busy) && styles.disabled]}>
      {busy && <ActivityIndicator color={primary ? colors.onPrimary : colors.primary} />}
      <Text style={[styles.buttonText, { color: primary ? colors.onPrimary : colors.text }]}>{title}</Text>
    </Pressable>
  )
}

export function Field({ label, hint, error, style, ...props }:
  { label: string; hint?: string; error?: string } & TextInputProps) {
  return (
    <View style={styles.field}>
      <Text style={styles.label}>
        {label}{hint && <Text style={styles.hint}> {hint}</Text>}
      </Text>
      <TextInput accessibilityLabel={label} placeholderTextColor={colors.textFaint}
        style={[styles.input, error ? styles.inputError : null, style]} {...props} />
      {error && <Text style={styles.fieldError}>{error}</Text>}
    </View>
  )
}

export function ErrorMessage({ children }: { children: ReactNode }) {
  return <Text accessibilityRole="alert" style={styles.error}>{children}</Text>
}

export function Spinner({ label = 'Loading' }: { label?: string }) {
  return (
    <View accessibilityRole="progressbar" accessibilityLabel={label} style={styles.spinner}>
      <ActivityIndicator color={colors.primary} />
      <Text style={styles.muted}>{label}…</Text>
    </View>
  )
}

/**
 * A scrolling screen that keeps focused inputs above the keyboard. Screens under a header only need the bottom
 * inset; ones without (login) pass the top too.
 */
export function Screen({ children, edges = ['bottom'] }: { children: ReactNode; edges?: Edge[] }) {
  return (
    <SafeAreaView style={styles.screen} edges={edges}>
      <KeyboardAvoidingView style={styles.flex} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled"
          contentInsetAdjustmentBehavior="automatic">{children}</ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  )
}

export interface Choice<T> {
  value: T
  label: string
  /** Read out instead of the label, e.g. "Sunday" for "S". */
  spoken?: string
}

/** One of a few options as buttons in a row: fewer taps than a dropdown on a phone. */
export function ChoiceGroup<T extends string | number>({ label, hint, choices, value, onChange, error }: {
  label: string; hint?: string; choices: Choice<T>[]; value: T | null; onChange: (value: T) => void; error?: string
}) {
  return (
    <View style={styles.field}>
      <Text style={styles.label}>
        {label}{hint && <Text style={styles.hint}> {hint}</Text>}
      </Text>
      <View accessibilityRole="radiogroup" accessibilityLabel={label} style={styles.choices}>
        {choices.map(choice => {
          const selected = choice.value === value
          return (
            <Pressable key={String(choice.value)} accessibilityRole="radio" accessibilityLabel={choice.spoken ?? choice.label}
              accessibilityState={{ checked: selected }} onPress={() => onChange(choice.value)}
              style={[styles.choice, selected && styles.choiceSelected]}>
              <Text style={[styles.choiceText, selected && styles.choiceTextSelected]}>{choice.label}</Text>
            </Pressable>
          )
        })}
      </View>
      {error && <Text style={styles.fieldError}>{error}</Text>}
    </View>
  )
}

/** Switches between views of one list, like the Active / Completed / Archived goals. */
export function Segmented<T extends string>({ label, choices, value, onChange }: {
  label: string; choices: Choice<T>[]; value: T; onChange: (value: T) => void
}) {
  return (
    <View accessibilityRole="tablist" accessibilityLabel={label} style={styles.segmented}>
      {choices.map(choice => {
        const selected = choice.value === value
        return (
          <Pressable key={choice.value} accessibilityRole="tab" accessibilityState={{ selected }}
            onPress={() => onChange(choice.value)} style={[styles.segment, selected && styles.segmentSelected]}>
            <Text style={[styles.segmentText, selected && styles.segmentTextSelected]}>{choice.label}</Text>
          </Pressable>
        )
      })}
    </View>
  )
}

const styles = StyleSheet.create({
  choices: { flexDirection: 'row', flexWrap: 'wrap', gap: space.sm },
  choice: {
    minWidth: 44, minHeight: 40, paddingHorizontal: space.md, alignItems: 'center', justifyContent: 'center',
    borderRadius: radius.md, borderWidth: 1, borderColor: colors.border, backgroundColor: colors.surface,
  },
  choiceSelected: { borderColor: colors.primary, backgroundColor: colors.primary },
  choiceText: { fontSize: 15, color: colors.text },
  choiceTextSelected: { color: colors.onPrimary, fontWeight: '600' },
  segmented: { flexDirection: 'row', padding: 3, borderRadius: radius.md, backgroundColor: colors.divider },
  segment: { flex: 1, minHeight: 36, alignItems: 'center', justifyContent: 'center', borderRadius: radius.md - 2 },
  segmentSelected: {
    backgroundColor: colors.surface, shadowColor: '#000', shadowOpacity: 0.08, shadowRadius: 2,
    shadowOffset: { width: 0, height: 1 }, elevation: 1,
  },
  segmentText: { fontSize: 14, color: colors.textMuted, fontWeight: '500' },
  segmentTextSelected: { color: colors.text, fontWeight: '600' },
  flex: { flex: 1 },
  screen: { flex: 1, backgroundColor: colors.background },
  content: { flexGrow: 1, padding: space.lg },
  button: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: space.sm,
    minHeight: 48, paddingHorizontal: space.lg, borderRadius: radius.md,
  },
  primary: { backgroundColor: colors.primary },
  primaryPressed: { backgroundColor: colors.primaryPressed },
  secondary: { backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.border },
  secondaryPressed: { backgroundColor: colors.background },
  disabled: { opacity: 0.6 },
  buttonText: { fontSize: 16, fontWeight: '600' },
  field: { gap: space.xs },
  label: { fontSize: 14, fontWeight: '500', color: colors.text },
  hint: { fontWeight: '400', color: colors.textFaint },
  input: {
    minHeight: 48, paddingHorizontal: space.md, borderRadius: radius.md, borderWidth: 1, borderColor: colors.border,
    backgroundColor: colors.surface, fontSize: 16, color: colors.text,
  },
  inputError: { borderColor: colors.danger },
  fieldError: { fontSize: 13, color: colors.danger },
  error: {
    padding: space.md, borderRadius: radius.md, backgroundColor: colors.dangerBackground, color: colors.danger,
    fontSize: 14,
  },
  spinner: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: space.sm, padding: space.xxl },
  muted: { color: colors.textMuted, fontSize: 14 },
})
