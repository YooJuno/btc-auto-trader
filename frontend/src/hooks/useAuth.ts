import { useState } from 'react'
import { apiFetch } from '../lib/api'

export default function useAuth() {
  const [token, setToken] = useState<string>(() => localStorage.getItem('token') ?? '')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [authError, setAuthError] = useState('')
  const [loading, setLoading] = useState(false)
  const [loginOpen, setLoginOpen] = useState(false)
  const [registerOpen, setRegisterOpen] = useState(false)

  const isAuthed = Boolean(token)

  const handleLogin = async () => {
    setAuthError('')
    setLoading(true)
    try {
      const response = await apiFetch<{ token: string }>('/api/auth/login', undefined, {
        method: 'POST',
        body: JSON.stringify({ email, password }),
      })
      localStorage.setItem('token', response.token)
      setToken(response.token)
      setLoginOpen(false)
      setRegisterOpen(false)
    } catch (error) {
      setAuthError('로그인 실패. 이메일과 비밀번호를 확인해주세요.')
    } finally {
      setLoading(false)
    }
  }

  const handleRegister = async (derivedTenantName?: string) => {
    setAuthError('')
    setLoading(true)
    try {
      const response = await apiFetch<{ token: string }>('/api/auth/register', undefined, {
        method: 'POST',
        body: JSON.stringify({ tenantName: derivedTenantName ?? '', email, password }),
      })
      localStorage.setItem('token', response.token)
      setToken(response.token)
      setLoginOpen(false)
      setRegisterOpen(false)
    } catch (error) {
      setAuthError('회원가입 실패. 입력값을 확인해주세요.')
    } finally {
      setLoading(false)
    }
  }

  const handleLogout = () => {
    localStorage.removeItem('token')
    setToken('')
    setLoginOpen(false)
    setRegisterOpen(false)
  }

  return {
    token,
    setToken,
    email,
    setEmail,
    password,
    setPassword,
    authError,
    setAuthError,
    loading,
    setLoading,
    loginOpen,
    setLoginOpen,
    registerOpen,
    setRegisterOpen,
    isAuthed,
    handleLogin,
    handleRegister,
    handleLogout,
  }
}
