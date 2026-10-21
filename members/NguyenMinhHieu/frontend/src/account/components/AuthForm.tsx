'use client';
import LoginForm from './LoginForm';
import RegisterForm from './RegisterForm';
export default function AuthForm({ register = false }: { register?: boolean }) {
  return register ? <RegisterForm /> : <LoginForm />;
}
