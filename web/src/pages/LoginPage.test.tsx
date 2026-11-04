import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AuthProvider } from '../auth/AuthContext'
import { LoginPage } from './LoginPage'

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

function renderLogin() {
  render(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter initialEntries={['/login']}>
        <AuthProvider>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/" element={<p>Home</p>} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

async function submit(email: string, password: string) {
  const user = userEvent.setup()
  await user.type(screen.getByLabelText('Email'), email)
  await user.type(screen.getByLabelText('Password'), password)
  await user.click(screen.getByRole('button', { name: 'Log in' }))
}

describe('LoginPage', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('logs in and goes home', async () => {
    const fetchMock = vi.fn(async (request: Request) => request.url.endsWith('/auth/login')
      ? json({ accessToken: 'a', refreshToken: 'r', expiresInSeconds: 900 })
      : json({ id: 1, email: 'm@example.com', displayName: 'Mayank', timezone: 'Asia/Kolkata' }))
    vi.stubGlobal('fetch', fetchMock)
    renderLogin()

    await submit('m@example.com', 'password123')

    expect(await screen.findByText('Home')).toBeInTheDocument()
    const loginBody = await (fetchMock.mock.calls[0][0] as Request).json()
    expect(loginBody).toEqual({ email: 'm@example.com', password: 'password123' })
  })

  it('explains a wrong password', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => json({ title: 'Unauthorized', detail: 'Bad credentials' }, 401)))
    renderLogin()

    await submit('m@example.com', 'wrong-password')

    expect(await screen.findByRole('alert')).toHaveTextContent('Wrong email or password.')
  })
})
