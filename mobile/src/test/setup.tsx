import AsyncStorage from '@react-native-async-storage/async-storage'

// Native modules don't exist under Jest: secure storage becomes an in-memory map, the time zone is fixed
const mockStore = new Map<string, string>()

jest.mock('expo-secure-store', () => ({
  getItemAsync: async (key: string) => mockStore.get(key) ?? null,
  setItemAsync: async (key: string, value: string) => void mockStore.set(key, value),
  deleteItemAsync: async (key: string) => void mockStore.delete(key),
}))

jest.mock('expo-localization', () => ({
  getCalendars: () => [{ timeZone: 'Asia/Kolkata' }],
}))

afterEach(() => mockStore.clear())

type MockHeaderOptions = { headerLeft?: () => React.ReactNode; headerRight?: () => React.ReactNode }

// Navigation is recorded, not performed; header buttons set through <Stack.Screen options> render in place
jest.mock('expo-router', () => {
  const { Text, View } = jest.requireActual('react-native')
  return {
    router: { push: jest.fn(), replace: jest.fn(), back: jest.fn() },
    useLocalSearchParams: jest.fn(() => ({})),
    Link: ({ children }: { children: React.ReactNode }) => <Text>{children}</Text>,
    Stack: {
      Screen: ({ options }: { options?: MockHeaderOptions }) =>
        <View>{options?.headerLeft?.()}{options?.headerRight?.()}</View>,
    },
  }
})

// The native picker has no Jest renderer; the deadline is shown as text
jest.mock('@react-native-community/datetimepicker', () => ({
  __esModule: true,
  default: () => null,
  DateTimePickerAndroid: { open: jest.fn() },
}))

afterEach(() => jest.clearAllMocks())

jest.mock('@react-native-async-storage/async-storage', () =>
  jest.requireActual('@react-native-async-storage/async-storage/jest/async-storage-mock'))

jest.mock('expo-haptics', () => ({
  selectionAsync: jest.fn(async () => undefined),
  notificationAsync: jest.fn(async () => undefined),
  NotificationFeedbackType: { Success: 'success' },
}))

afterEach(() => AsyncStorage.clear())
