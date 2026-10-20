import * as React from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router';
import {
  RiErrorWarningFill,
  RiEyeLine,
  RiEyeOffLine,
  RiHeartPulseFill,
  RiInformationFill,
  RiLoader4Line,
  RiLock2Line,
  RiUserLine,
} from '@remixicon/react';

import * as Alert from '@/components/ui/alert';
import * as Button from '@/components/ui/button';
import * as Hint from '@/components/ui/hint';
import * as Input from '@/components/ui/input';
import * as Label from '@/components/ui/label';
import { canAccessPath, roleHome } from '@/features/auth/roles';
import { useAuth } from '@/features/auth/use-auth';
import { ApiError, getErrorMessage } from '@/lib/api/client';
import { LOGOUT_MESSAGES, sessionStore } from '@/lib/session';

interface Notice {
  status: 'error' | 'warning' | 'information';
  message: string;
}

/**
 * Inicio de sesión (CU-011). Composición AlignUI: tarjeta centrada, Label + Input con icono,
 * toggle de contraseña (ejemplo input-password), Alert de error (alert-demo) y botón a ancho completo.
 */
export function LoginPage() {
  const { isAuthenticated, user, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const [username, setUsername] = React.useState('');
  const [password, setPassword] = React.useState('');
  const [showPassword, setShowPassword] = React.useState(false);
  const [isPending, setIsPending] = React.useState(false);
  const [notice, setNotice] = React.useState<Notice | null>(() => {
    const reason = sessionStore.consumeLogoutReason();
    if (!reason || reason === 'manual') return null;
    return { status: reason === 'inactive' ? 'error' : 'warning', message: LOGOUT_MESSAGES[reason] };
  });
  const [fieldErrors, setFieldErrors] = React.useState<Record<string, string>>({});

  React.useEffect(() => {
    document.title = 'Iniciar sesión · MediCitas Anglo';
  }, []);

  if (isAuthenticated && user) {
    return <Navigate to={roleHome(user.role)} replace />;
  }

  const validate = (): boolean => {
    const errors: Record<string, string> = {};
    if (username.trim() === '') errors.username = 'Ingresa tu usuario.';
    if (password === '') errors.password = 'Ingresa tu contraseña.';
    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setNotice(null);
    if (!validate()) return;
    setIsPending(true);
    try {
      const loggedUser = await login({ username: username.trim(), password });
      const from = (location.state as { from?: string } | null)?.from;
      // Solo se vuelve a la ruta pedida si el usuario que entra puede abrirla; si no, va a su inicio.
      const destination = from && canAccessPath(loggedUser.role, from) ? from : roleHome(loggedUser.role);
      navigate(destination, { replace: true });
    } catch (error) {
      if (error instanceof ApiError && error.isValidation) {
        setFieldErrors(error.fieldErrors());
      } else {
        setNotice({ status: 'error', message: getErrorMessage(error) });
      }
    } finally {
      setIsPending(false);
    }
  };

  return (
    <div className='flex min-h-screen items-center justify-center bg-bg-weak-50 px-4 py-10'>
      <div className='w-full max-w-[420px]'>
        <div className='flex flex-col gap-6 rounded-20 bg-bg-white-0 p-8 shadow-regular-xs ring-1 ring-inset ring-stroke-soft-200'>
          <div className='flex flex-col items-center gap-3 text-center'>
            <div className='flex size-12 items-center justify-center rounded-full bg-primary-base text-text-white-0 shadow-regular-xs'>
              <RiHeartPulseFill className='size-6' />
            </div>
            <div className='flex flex-col gap-1'>
              <h1 className='text-title-h5 text-text-strong-950'>MediCitas Anglo</h1>
              <p className='text-paragraph-sm text-text-sub-600'>Backoffice · Ingresa con tu usuario del personal</p>
            </div>
          </div>

          {notice && (
            <Alert.Root variant='lighter' status={notice.status} size='small'>
              <Alert.Icon as={notice.status === 'error' ? RiErrorWarningFill : RiInformationFill} />
              {notice.message}
            </Alert.Root>
          )}

          <form className='flex flex-col gap-4' onSubmit={handleSubmit} noValidate>
            <div className='flex flex-col gap-1'>
              <Label.Root htmlFor='username'>
                Usuario <Label.Asterisk />
              </Label.Root>
              <Input.Root hasError={Boolean(fieldErrors.username)}>
                <Input.Wrapper>
                  <Input.Icon as={RiUserLine} />
                  <Input.Input
                    id='username'
                    name='username'
                    autoComplete='username'
                    autoFocus
                    placeholder='Tu usuario'
                    value={username}
                    onChange={(e) => setUsername(e.target.value)}
                    disabled={isPending}
                  />
                </Input.Wrapper>
              </Input.Root>
              {fieldErrors.username && (
                <Hint.Root hasError>
                  <Hint.Icon as={RiErrorWarningFill} />
                  {fieldErrors.username}
                </Hint.Root>
              )}
            </div>

            <div className='flex flex-col gap-1'>
              <Label.Root htmlFor='password'>
                Contraseña <Label.Asterisk />
              </Label.Root>
              <Input.Root hasError={Boolean(fieldErrors.password)}>
                <Input.Wrapper>
                  <Input.Icon as={RiLock2Line} />
                  <Input.Input
                    id='password'
                    name='password'
                    type={showPassword ? 'text' : 'password'}
                    autoComplete='current-password'
                    placeholder='••••••••••'
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    disabled={isPending}
                  />
                  <button
                    type='button'
                    onClick={() => setShowPassword((s) => !s)}
                    aria-label={showPassword ? 'Ocultar contraseña' : 'Mostrar contraseña'}
                  >
                    {showPassword ? (
                      <RiEyeOffLine className='size-5 text-text-soft-400 group-has-[disabled]:text-text-disabled-300' />
                    ) : (
                      <RiEyeLine className='size-5 text-text-soft-400 group-has-[disabled]:text-text-disabled-300' />
                    )}
                  </button>
                </Input.Wrapper>
              </Input.Root>
              {fieldErrors.password && (
                <Hint.Root hasError>
                  <Hint.Icon as={RiErrorWarningFill} />
                  {fieldErrors.password}
                </Hint.Root>
              )}
            </div>

            <Button.Root type='submit' variant='primary' mode='filled' size='medium' className='mt-2 w-full' disabled={isPending}>
              {isPending ? (
                <>
                  <Button.Icon as={RiLoader4Line} className='animate-spin' />
                  Ingresando…
                </>
              ) : (
                'Iniciar sesión'
              )}
            </Button.Root>
          </form>
        </div>

        <p className='mt-6 text-center text-paragraph-xs text-text-soft-400'>
          Clínica Anglo Americana · Uso exclusivo del personal autorizado
        </p>
      </div>
    </div>
  );
}
