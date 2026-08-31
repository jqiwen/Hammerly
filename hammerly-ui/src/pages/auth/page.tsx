import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Header from '../../components/feature/Header';
// import Footer from '../../components/feature/Footer';
import { useAuthStore } from '@/store/useAuthStore';
import { AuthApiError, loginApi, registerApi } from '@/api/auth';
import {
  normalizeEmail,
  validateLogin,
  validateRegistration,
  type AuthFieldErrors,
} from './authValidation';

const FieldError = ({ message }: { message?: string }) =>
  message ? <p className="text-xs text-red-600 mt-1" role="alert">{message}</p> : null;

export default function Auth() {
  const navigate = useNavigate();
  const [isLogin, setIsLogin] = useState(false);
  const [formData, setFormData] = useState({
    firstName: '',
    lastName: '',
    email: '',
    phone: '',
    password: '',
    confirmPassword: '',
    agreeTerms: false,
  });
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [passwordMismatch, setPasswordMismatch] = useState(false);
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState<AuthFieldErrors>({});
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    if (!isLogin && formData.confirmPassword) {
      setPasswordMismatch(formData.password !== formData.confirmPassword);
    } else {
      setPasswordMismatch(false);
    }
  }, [formData.password, formData.confirmPassword, isLogin]);

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value, type, checked } = e.target;
    setFormData((prev) => ({
      ...prev,
      [name]: type === 'checkbox' ? checked : value,
    }));
    setFieldErrors((previous) => ({ ...previous, [name]: undefined }));
  };

  const handleRegister = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (isSubmitting) return;
    setError('');
    const validationErrors = validateRegistration(formData);
    if (Object.keys(validationErrors).length > 0) {
      setFieldErrors(validationErrors);
      setError('Please correct the highlighted fields.');
      return;
    }
    setFieldErrors({});
    setIsSubmitting(true);

    try {
      const data = await registerApi({
        email: normalizeEmail(formData.email),
        password: formData.password,
        firstName: formData.firstName.trim(),
        lastName: formData.lastName.trim(),
        phone: formData.phone.trim(),
      });

      useAuthStore.getState().loginSuccess({
        user: data.user,
        token: data.token,
      });

      navigate('/profile');
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Registration failed');
      if (caught instanceof AuthApiError) setFieldErrors(caught.fields);
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleLogin = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (isSubmitting) return;
    setError('');
    const validationErrors = validateLogin(formData);
    if (Object.keys(validationErrors).length > 0) {
      setFieldErrors(validationErrors);
      setError('Please correct the highlighted fields.');
      return;
    }
    setFieldErrors({});
    setIsSubmitting(true);

    try {
      const data = await loginApi({
        email: normalizeEmail(formData.email),
        password: formData.password,
      });

      useAuthStore.getState().loginSuccess({
        user: data.user,
        token: data.token,
      });

      navigate('/profile');
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Sign in failed');
      if (caught instanceof AuthApiError) setFieldErrors(caught.fields);
    } finally {
      setIsSubmitting(false);
    }
  };


  return (
    <div className="min-h-screen bg-gradient-to-br from-[#FDF8F6] via-[#F5E6E0] to-[#E8D5CF]">
      <Header />
      <main className="pt-24 pb-12 flex items-center justify-center min-h-screen">
        <div className="w-full max-w-2xl mx-auto px-6">
          <div className="bg-white rounded-2xl shadow-xl p-6 lg:p-8">
            {/* Tab Switcher */}
            <div className="flex bg-gray-100 rounded-full p-1 mb-6 max-w-xs mx-auto">
              <button
                onClick={() => {
                  setIsLogin(false);
                  setError('');
                  setFieldErrors({});
                }}
                disabled={isSubmitting}
                className={`flex-1 py-2.5 px-6 rounded-full font-medium transition-all cursor-pointer whitespace-nowrap text-sm ${
                  !isLogin ? 'bg-[#8B2635] text-white shadow-md' : 'text-gray-600 hover:text-gray-900'
                }`}
              >
                Register
              </button>
              <button
                onClick={() => {
                  setIsLogin(true);
                  setError('');
                  setFieldErrors({});
                }}
                disabled={isSubmitting}
                className={`flex-1 py-2.5 px-6 rounded-full font-medium transition-all cursor-pointer whitespace-nowrap text-sm ${
                  isLogin ? 'bg-[#8B2635] text-white shadow-md' : 'text-gray-600 hover:text-gray-900'
                }`}
              >
                Sign In
              </button>
            </div>

            {!isLogin ? (
              // ===================== REGISTER FORM =====================
              <form onSubmit={handleRegister} className="space-y-4" noValidate aria-busy={isSubmitting}>
                {error && (
                  <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg text-sm">
                    {error}
                  </div>
                )}
                {/* Name Fields */}
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label htmlFor="register-first-name" className="block text-sm font-medium text-gray-700 mb-1.5">First Name</label>
                    <input
                      id="register-first-name"
                      type="text"
                      name="firstName"
                      value={formData.firstName}
                      onChange={handleInputChange}
                      className="w-full px-4 py-2.5 rounded-lg border border-gray-300 focus:border-[#8B2635] focus:ring-2 focus:ring-[#8B2635]/20 text-sm"
                      placeholder="First Name"
                      maxLength={100}
                      aria-invalid={Boolean(fieldErrors.firstName)}
                      required
                    />
                    <FieldError message={fieldErrors.firstName} />
                  </div>
                  <div>
                    <label htmlFor="register-last-name" className="block text-sm font-medium text-gray-700 mb-1.5">Last Name</label>
                    <input
                      id="register-last-name"
                      type="text"
                      name="lastName"
                      value={formData.lastName}
                      onChange={handleInputChange}
                      className="w-full px-4 py-2.5 rounded-lg border border-gray-300 focus:border-[#8B2635] focus:ring-2 focus:ring-[#8B2635]/20 text-sm"
                      placeholder="Last Name"
                      maxLength={100}
                      aria-invalid={Boolean(fieldErrors.lastName)}
                      required
                    />
                    <FieldError message={fieldErrors.lastName} />
                  </div>
                </div>

                {/* Email */}
                <div>
                  <label htmlFor="register-email" className="block text-sm font-medium text-gray-700 mb-1.5">Email Address</label>
                  <div className="relative">
                    <i className="ri-mail-line absolute left-4 top-1/2 -translate-y-1/2 text-gray-400"></i>
                    <input
                      type="email"
                      id="register-email"
                      name="email"
                      value={formData.email}
                      onChange={handleInputChange}
                      className="w-full pl-11 pr-4 py-2.5 rounded-lg border border-gray-300 focus:border-[#8B2635] focus:ring-2 focus:ring-[#8B2635]/20 text-sm"
                      placeholder="hammerly@example.com"
                      maxLength={254}
                      autoComplete="email"
                      aria-invalid={Boolean(fieldErrors.email)}
                      required
                    />
                  </div>
                  <FieldError message={fieldErrors.email} />
                </div>

                {/* Phone Number */}
                <div>
                  <label htmlFor="register-phone" className="block text-sm font-medium text-gray-700 mb-1.5">
                    Phone Number
                  </label>
                  <div className="relative">
                    <i className="ri-phone-line absolute left-4 top-1/2 -translate-y-1/2 text-gray-400"></i>
                    <input
                      type="text"
                      id="register-phone"
                      name="phone"
                      value={formData.phone}
                      onChange={handleInputChange}
                      className="w-full pl-11 pr-4 py-2.5 rounded-lg border border-gray-300 focus:border-[#8B2635] focus:ring-2 focus:ring-[#8B2635]/20 text-sm"
                      placeholder="Enter your phone number"
                      maxLength={32}
                      autoComplete="tel"
                      aria-invalid={Boolean(fieldErrors.phone)}
                      required
                    />
                  </div>
                  <FieldError message={fieldErrors.phone} />
                </div>


                {/* Password + Confirm */}
                <div className="grid grid-cols-2 gap-4">
                  {/* Password */}
                  <div>
                    <label htmlFor="register-password" className="block text-sm font-medium text-gray-700 mb-1.5">Password</label>
                    <div className="relative">
                      <i className="ri-lock-line absolute left-4 top-1/2 -translate-y-1/2 text-gray-400"></i>
                      <input
                        type={showPassword ? 'text' : 'password'}
                        id="register-password"
                        name="password"
                        value={formData.password}
                        onChange={handleInputChange}
                        className="w-full pl-11 pr-11 py-2.5 rounded-lg border border-gray-300 focus:border-[#8B2635] focus:ring-2 focus:ring-[#8B2635]/20 text-sm"
                        placeholder="••••••••"
                        minLength={8}
                        maxLength={128}
                        autoComplete="new-password"
                        aria-invalid={Boolean(fieldErrors.password)}
                        required
                      />
                      <button
                        type="button"
                        onClick={() => setShowPassword(!showPassword)}
                        className="absolute right-4 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
                      >
                        <i className={showPassword ? 'ri-eye-off-line' : 'ri-eye-line'}></i>
                      </button>
                    </div>
                    <FieldError message={fieldErrors.password} />
                  </div>

                  {/* Confirm Password */}
                  <div>
                    <label htmlFor="register-confirm-password" className="block text-sm font-medium text-gray-700 mb-1.5">Confirm Password</label>
                    <div className="relative">
                      <i className="ri-lock-line absolute left-4 top-1/2 -translate-y-1/2 text-gray-400"></i>
                      <input
                        type={showConfirmPassword ? 'text' : 'password'}
                        id="register-confirm-password"
                        name="confirmPassword"
                        value={formData.confirmPassword}
                        onChange={handleInputChange}
                        className={`w-full pl-11 pr-11 py-2.5 rounded-lg border ${
                          passwordMismatch ? 'border-red-500' : 'border-gray-300'
                        } focus:border-[#8B2635] focus:ring-2 focus:ring-[#8B2635]/20 text-sm`}
                        placeholder="••••••••"
                        maxLength={128}
                        autoComplete="new-password"
                        aria-invalid={Boolean(fieldErrors.confirmPassword || passwordMismatch)}
                        required
                      />
                      <button
                        type="button"
                        onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                        className="absolute right-4 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
                      >
                        <i className={showConfirmPassword ? 'ri-eye-off-line' : 'ri-eye-line'}></i>
                      </button>
                    </div>
                    <FieldError message={fieldErrors.confirmPassword || (passwordMismatch ? 'Passwords do not match.' : undefined)} />
                  </div>
                </div>

                {/* Terms */}
                <div className="flex items-center justify-between mb-8">
                  <div className="flex items-center gap-2">
                    <input
                      type="checkbox"
                      name="agreeTerms"
                      checked={formData.agreeTerms}
                      onChange={handleInputChange}
                      aria-invalid={Boolean(fieldErrors.agreeTerms)}
                      className="w-4 h-4 rounded border-gray-300 text-[#8B2635] focus:ring-[#8B2635] cursor-pointer"
                      required
                    />
                    <span className="text-sm text-gray-600 select-none">
                      I agree to the <a className="text-[#8B2635] hover:underline">Terms</a> &{' '}
                      <a className="text-[#8B2635] hover:underline">Privacy Policy</a>
                    </span>
                  </div>
                  <FieldError message={fieldErrors.agreeTerms} />
                </div>

                {/* Register Submit */}
                <button
                  type="submit"
                  disabled={isSubmitting}
                  className="w-full bg-[#8B2635] text-white py-3 rounded-lg font-medium hover:bg-[#7A1F2B] disabled:opacity-60 disabled:cursor-not-allowed transition-all flex items-center justify-center gap-2 text-sm"
                >
                  {isSubmitting ? 'Creating account…' : 'Create Account'}
                  <i className={`${isSubmitting ? 'ri-loader-4-line animate-spin' : 'ri-arrow-right-line'} w-5 h-5 flex items-center justify-center`}></i>
                </button>
              </form>
            ) : (
              // ===================== LOGIN FORM =====================
              <form onSubmit={handleLogin} className="space-y-4" noValidate aria-busy={isSubmitting}>
                {error && (
                  <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg text-sm">
                    {error}
                  </div>
                )}
                {/* Email */}
                <div>
                  <label htmlFor="login-email" className="block text-sm font-medium text-gray-700 mb-1.5">Email Address</label>
                  <div className="relative">
                    <i className="ri-mail-line absolute left-4 top-1/2 -translate-y-1/2 text-gray-400"></i>
                    <input
                      type="email"
                      id="login-email"
                      name="email"
                      value={formData.email}
                      onChange={handleInputChange}
                      className="w-full pl-11 pr-4 py-2.5 rounded-lg border border-gray-300 focus:border-[#8B2635] focus:ring-2 focus:ring-[#8B2635]/20 text-sm"
                      placeholder="hammerly@example.com"
                      maxLength={254}
                      autoComplete="email"
                      aria-invalid={Boolean(fieldErrors.email)}
                      required
                    />
                  </div>
                  <FieldError message={fieldErrors.email} />
                </div>

                {/* Password */}
                <div>
                  <label htmlFor="login-password" className="block text-sm font-medium text-gray-700 mb-1.5">Password</label>
                  <div className="relative">
                    <i className="ri-lock-line absolute left-4 top-1/2 -translate-y-1/2 text-gray-400"></i>
                    <input
                      type={showPassword ? 'text' : 'password'}
                      id="login-password"
                      name="password"
                      value={formData.password}
                      onChange={handleInputChange}
                      className="w-full pl-11 pr-11 py-2.5 rounded-lg border border-gray-300 focus:border-[#8B2635] focus:ring-2 focus:ring-[#8B2635]/20 text-sm"
                      placeholder="••••••••"
                      maxLength={128}
                      autoComplete="current-password"
                      aria-invalid={Boolean(fieldErrors.password)}
                      required
                    />
                    <button
                      type="button"
                      onClick={() => setShowPassword(!showPassword)}
                      className="absolute right-4 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
                    >
                      <i className={showPassword ? 'ri-eye-off-line' : 'ri-eye-line'}></i>
                    </button>
                  </div>
                  <FieldError message={fieldErrors.password} />
                </div>

                {/* Forgot password */}
                <div className="flex items-center justify-between mb-8">
                  <div className="text-sm text-[#8B2635] hover:underline cursor-pointer">Forgot password?</div>
                </div>

                {/* Login Submit */}
                <button
                  type="submit"
                  disabled={isSubmitting}
                  className="w-full bg-[#8B2635] text-white py-3 rounded-lg font-medium hover:bg-[#7A1F2B] disabled:opacity-60 disabled:cursor-not-allowed transition-all flex items-center justify-center gap-2 text-sm"
                >
                  {isSubmitting ? 'Signing in…' : 'Sign In'}
                  <i className={`${isSubmitting ? 'ri-loader-4-line animate-spin' : 'ri-arrow-right-line'} w-5 h-5 flex items-center justify-center`}></i>
                </button>
              </form>
            )}


          </div>
        </div>
      </main>
    </div>
  );
}
