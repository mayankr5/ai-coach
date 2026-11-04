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
