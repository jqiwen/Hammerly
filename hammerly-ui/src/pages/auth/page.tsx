import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Header from '../../components/feature/Header';
// import Footer from '../../components/feature/Footer';
import { useAuthStore } from '@/store/useAuthStore';
import { loginApi, registerApi } from '@/api/auth';

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
  };

  const handleRegister = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setError('');

    try {
      if (!formData.agreeTerms) {
        setError('Please agree to the Terms & Privacy Policy.');
        return;
      }

      if (formData.password.trim() !== formData.confirmPassword.trim()) {
        setError('Passwords do not match.');
        return;
      }

      const data = await registerApi({
        email: formData.email,
        password: formData.password,
        firstName: formData.firstName,
        lastName: formData.lastName,
        phone: formData.phone, // Pass phone number to the API
      });

      useAuthStore.getState().loginSuccess({
        user: data.user,
        token: data.token,
      });

      navigate('/profile');
    } catch (error: any) {
      const errorMessage = error instanceof Error ? error.message : 'Registration failed';
      setError(errorMessage);
      console.error('Register error:', error);
    }
  };

  const handleLogin = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setError('');

    try {
      const data = await loginApi({
        email: formData.email,
        password: formData.password,
      });

      useAuthStore.getState().loginSuccess({
        user: data.user,
        token: data.token,
      });

      navigate('/profile');
    } catch (err: any) {
      const errorMessage = err instanceof Error ? err.message : 'Sign in failed';
      setError(errorMessage);
      console.error('Login error:', err);
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
                }}
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
                }}
                className={`flex-1 py-2.5 px-6 rounded-full font-medium transition-all cursor-pointer whitespace-nowrap text-sm ${
                  isLogin ? 'bg-[#8B2635] text-white shadow-md' : 'text-gray-600 hover:text-gray-900'
                }`}
              >
                Sign In
              </button>
            </div>

            {!isLogin ? (
              // ===================== REGISTER FORM =====================
              <form onSubmit={handleRegister} className="space-y-4">
                {error && (
                  <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg text-sm">
                    {error}
                  </div>
                )}
                {/* Name Fields */}
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1.5">First Name</label>
                    <input
                      type="text"
                      name="firstName"
                      value={formData.firstName}
                      onChange={handleInputChange}
                      className="w-full px-4 py-2.5 rounded-lg border border-gray-300 focus:border-[#8B2635] focus:ring-2 focus:ring-[#8B2635]/20 text-sm"
                      placeholder="First Name"
                      required
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1.5">Last Name</label>
                    <input
                      type="text"
                      name="lastName"
                      value={formData.lastName}
                      onChange={handleInputChange}
                      className="w-full px-4 py-2.5 rounded-lg border border-gray-300 focus:border-[#8B2635] focus:ring-2 focus:ring-[#8B2635]/20 text-sm"
                      placeholder="Last Name"
                      required
                    />
                  </div>
                </div>

                {/* Email */}
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1.5">Email Address</label>
                  <div className="relative">
                    <i className="ri-mail-line absolute left-4 top-1/2 -translate-y-1/2 text-gray-400"></i>
                    <input
                      type="email"
                      name="email"
                      value={formData.email}
                      onChange={handleInputChange}
                      className="w-full pl-11 pr-4 py-2.5 rounded-lg border border-gray-300 focus:border-[#8B2635] focus:ring-2 focus:ring-[#8B2635]/20 text-sm"
                      placeholder="hammerly@example.com"
                      required
                    />
                  </div>
                </div>

                {/* Phone Number */}
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1.5">
                    Phone Number
                  </label>
                  <div className="relative">
                    <i className="ri-phone-line absolute left-4 top-1/2 -translate-y-1/2 text-gray-400"></i>
                    <input
                      type="text"
                      name="phone"
                      value={formData.phone}
                      onChange={handleInputChange}
                      className="w-full pl-11 pr-4 py-2.5 rounded-lg border border-gray-300 focus:border-[#8B2635] focus:ring-2 focus:ring-[#8B2635]/20 text-sm"
                      placeholder="Enter your phone number"
                      required
                    />
                  </div>
                </div>


                {/* Password + Confirm */}
                <div className="grid grid-cols-2 gap-4">
                  {/* Password */}
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1.5">Password</label>
                    <div className="relative">
                      <i className="ri-lock-line absolute left-4 top-1/2 -translate-y-1/2 text-gray-400"></i>
                      <input
                        type={showPassword ? 'text' : 'password'}
                        name="password"
                        value={formData.password}
                        onChange={handleInputChange}
                        className="w-full pl-11 pr-11 py-2.5 rounded-lg border border-gray-300 focus:border-[#8B2635] focus:ring-2 focus:ring-[#8B2635]/20 text-sm"
                        placeholder="••••••••"
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
                  </div>

                  {/* Confirm Password */}
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1.5">Confirm Password</label>
                    <div className="relative">
                      <i className="ri-lock-line absolute left-4 top-1/2 -translate-y-1/2 text-gray-400"></i>
                      <input
                        type={showConfirmPassword ? 'text' : 'password'}
                        name="confirmPassword"
                        value={formData.confirmPassword}
                        onChange={handleInputChange}
                        className={`w-full pl-11 pr-11 py-2.5 rounded-lg border ${
                          passwordMismatch ? 'border-red-500' : 'border-gray-300'
                        } focus:border-[#8B2635] focus:ring-2 focus:ring-[#8B2635]/20 text-sm`}
                        placeholder="••••••••"
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
                    {passwordMismatch && <p className="text-xs text-red-500 mt-1">Passwords do not match.</p>}
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
                      className="w-4 h-4 rounded border-gray-300 text-[#8B2635] focus:ring-[#8B2635] cursor-pointer"
                      required
                    />
                    <span className="text-sm text-gray-600 select-none">
                      I agree to the <a className="text-[#8B2635] hover:underline">Terms</a> &{' '}
                      <a className="text-[#8B2635] hover:underline">Privacy Policy</a>
                    </span>
                  </div>
                </div>

                {/* Register Submit */}
                <button
                  type="submit"
                  className="w-full bg-[#8B2635] text-white py-3 rounded-lg font-medium hover:bg-[#7A1F2B] transition-all flex items-center justify-center gap-2 text-sm"
                >
                  Create Account
                  <i className="ri-arrow-right-line w-5 h-5 flex items-center justify-center"></i>
                </button>
              </form>
            ) : (
              // ===================== LOGIN FORM =====================
              <form onSubmit={handleLogin} className="space-y-4">
                {error && (
                  <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg text-sm">
                    {error}
                  </div>
                )}
                {/* Email */}
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1.5">Email Address</label>
                  <div className="relative">
                    <i className="ri-mail-line absolute left-4 top-1/2 -translate-y-1/2 text-gray-400"></i>
                    <input
                      type="email"
                      name="email"
                      value={formData.email}
                      onChange={handleInputChange}
                      className="w-full pl-11 pr-4 py-2.5 rounded-lg border border-gray-300 focus:border-[#8B2635] focus:ring-2 focus:ring-[#8B2635]/20 text-sm"
                      placeholder="hammerly@example.com"
                      required
                    />
                  </div>
                </div>

                {/* Password */}
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1.5">Password</label>
                  <div className="relative">
                    <i className="ri-lock-line absolute left-4 top-1/2 -translate-y-1/2 text-gray-400"></i>
                    <input
                      type={showPassword ? 'text' : 'password'}
                      name="password"
                      value={formData.password}
                      onChange={handleInputChange}
                      className="w-full pl-11 pr-11 py-2.5 rounded-lg border border-gray-300 focus:border-[#8B2635] focus:ring-2 focus:ring-[#8B2635]/20 text-sm"
                      placeholder="••••••••"
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
                </div>

                {/* Forgot password */}
                <div className="flex items-center justify-between mb-8">
                  <div className="text-sm text-[#8B2635] hover:underline cursor-pointer">Forgot password?</div>
                </div>

                {/* Login Submit */}
                <button
                  type="submit"
                  className="w-full bg-[#8B2635] text-white py-3 rounded-lg font-medium hover:bg-[#7A1F2B] transition-all flex items-center justify-center gap-2 text-sm"
                >
                  Sign In
                  <i className="ri-arrow-right-line w-5 h-5 flex items-center justify-center"></i>
                </button>
              </form>
            )}


          </div>
        </div>
      </main>
    </div>
  );
}
